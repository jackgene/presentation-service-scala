package com.jackleow.presentation.service.transcription

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.stream.BoundedSourceQueue
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, Sink, Source}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

/**
 * Akka Stream implementation of `TranscriptionService`.
 */
object AkkaStreamTranscriptionService {
  private sealed trait Subscription
  private case object Subscribed extends Subscription
  private case object Unsubscribed extends Subscription
}
class AkkaStreamTranscriptionService(implicit val system: ActorSystem[Nothing])
  extends TranscriptionService with StrictLogging {

  import AkkaStreamTranscriptionService.*
  import TranscriptionService.*

  private implicit val ec: ExecutionContext = system.executionContext
  private val transcriptionQueueSource: Source[Either[Subscription, Transcription], BoundedSourceQueue[Transcription]] =
    Source.
      queue[Transcription](1).
      wireTap { case Transcription(text: String) =>
        logger.info(s"Received transcription text: $text")
      }.
      map(Right(_))
  // Track subscriptions, needed to defeat Akka Stream buffering (which cannot be set to 0)
  private val subscriptionQueueSource: Source[Either[Subscription, Transcription], BoundedSourceQueue[Subscription]] =
    Source.
      queue[Subscription](1).
      map(Left(_))
  private val (
    transcriptionQueue: BoundedSourceQueue[Transcription],
    subscriptionQueue: BoundedSourceQueue[Subscription],
    transcriptionSource: Source[Transcription, NotUsed]
  ) =
    transcriptionQueueSource.mergeMat(subscriptionQueueSource)(Keep.both).
      scan[(Int, Option[Transcription])](0 -> None) {
        case ((subscribers: Int, _), Left(Subscribed)) =>
          val newSubscribers = subscribers + 1
          logger.info(s"+1 subscriber (=$newSubscribers)")
          newSubscribers -> None

        case ((subscribers: Int, _), Left(Unsubscribed)) =>
          val newSubscribers = subscribers - 1
          logger.info(s"-1 subscriber (=$newSubscribers)")
          newSubscribers -> None

        case ((0, _), Right(_)) => 0 -> None

        case ((subscribers: Int, _), Right(transcription: Transcription)) =>
          subscribers -> Some(transcription)
      }.
      collect {
        case (_, Some(transcription: Transcription)) => transcription
      }.
      toMat(BroadcastHub.sink)(Keep.both).
      mapMaterializedValue {
        case ((tq, sq), ts) => (tq, sq, ts) // flatten
      }.
      run()
  private val transcriptionFlow: Flow[Any, Transcription, NotUsed] =
    Flow.fromSinkAndSourceCoupled(
      Sink.onComplete { _ => subscriptionQueue.offer(Unsubscribed) },
      transcriptionSource
    )

  override def receiveTranscription(text: String): Future[Unit] = Future.
    successful { transcriptionQueue.offer(Transcription(text)) }.
    filter(_.isEnqueued).
    map(_ => ())

  override def transcriptions: Flow[Any, Transcription, NotUsed] = {
    subscriptionQueue.offer(Subscribed)
    transcriptionFlow
  }
}
