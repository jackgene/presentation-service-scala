package com.jackleow.presentation.service.transcription

import akka.NotUsed
import akka.stream.BoundedSourceQueue
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, Source}
import com.jackleow.akka.stream.scaladsl.*
import com.jackleow.presentation.infrastructure.AkkaModule
import com.jackleow.presentation.service.SubscriptionSupport
import com.jackleow.presentation.service.transcription.model.Transcription
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}


/**
 * Default implementation of `TranscriptionModule`.
 */
trait DefaultTranscriptionModule extends TranscriptionModule:
  this: AkkaModule =>

  override val transcriptionService: TranscriptionService =
    new TranscriptionService with SubscriptionSupport with StrictLogging:
      private given ec: ExecutionContext = system.executionContext

      private val transcriptionQueueSource: Source[Transcription, BoundedSourceQueue[Transcription]] =
        Source.queue[Transcription](1)
      private val (
        transcriptionQueue: BoundedSourceQueue[Transcription],
        subscriptionCounter: Counter,
        transcriptionSource: Source[Transcription, NotUsed]
      ) =
        transcriptionQueueSource
          .wireTap:
            case Transcription(text: String) =>
              logger.info(s"Received transcription text: $text")
          .viaMat(Flow.activeFilter(hasActiveSubscriptionsSource()))(Keep.both)
          .toMat(BroadcastHub.sink)(Keep.both)
          .mapMaterializedValue:
            case ((tq, sq), ts) => (tq, sq, ts) // flatten
          .run()

      override def broadcastTranscription(text: String): Future[Unit] =
        Future
          .successful:
            transcriptionQueue.offer(Transcription(text))
          .filter:
            _.isEnqueued
          .map:
            _ => ()

      override val transcriptions: Flow[Any, Transcription, NotUsed] =
        subscriptionTrackingFlow(transcriptionSource, subscriptionCounter)
