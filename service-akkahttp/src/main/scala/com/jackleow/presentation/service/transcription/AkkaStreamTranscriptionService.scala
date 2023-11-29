package com.jackleow.presentation.service.transcription

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.stream.BoundedSourceQueue
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, Source}
import com.jackleow.akka.stream.scaladsl.*
import com.jackleow.presentation.service.SubscriptionSupport
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

/**
 * Akka Stream implementation of `TranscriptionService`.
 */
class AkkaStreamTranscriptionService(implicit val system: ActorSystem[Nothing])
  extends TranscriptionService with SubscriptionSupport with StrictLogging:

  import TranscriptionService.*

  private implicit val ec: ExecutionContext = system.executionContext
  private val transcriptionQueueSource: Source[Transcription, BoundedSourceQueue[Transcription]] =
    Source
      .queue[Transcription](1)
      .wireTap:
        case Transcription(text: String) =>
          logger.info(s"Received transcription text: $text")
  private val (
    transcriptionQueue: BoundedSourceQueue[Transcription],
    subscriptionCounter: Counter,
    transcriptionSource: Source[Transcription, NotUsed]
  ) =
    transcriptionQueueSource
      .viaMat(Flow.activeFilter(hasActiveSubscriptionsSource()))(Keep.both)
      .toMat(BroadcastHub.sink)(Keep.both)
      .mapMaterializedValue:
        case ((tq, sq), ts) => (tq, sq, ts) // flatten
      .run()

  override def receiveTranscription(text: String): Future[Unit] =
    Future
      .successful:
        transcriptionQueue.offer(Transcription(text))
      .filter:
        _.isEnqueued
      .map:
        _ => ()

  override val transcriptions: Flow[Any, Transcription, NotUsed] =
    subscriptionTrackingFlow(transcriptionSource, subscriptionCounter)
