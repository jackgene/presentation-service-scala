package com.jackleow.presentation.service.interactive

import akka.NotUsed
import akka.stream.scaladsl.*
import akka.stream.{BoundedSourceQueue, Materializer}
import com.jackleow.akka.stream.scaladsl.*
import com.jackleow.presentation.infrastructure.AkkaModule
import com.jackleow.presentation.service.SubscriptionSupport
import com.jackleow.presentation.service.interactive.graph.*
import com.jackleow.presentation.service.interactive.model.*
import com.typesafe.scalalogging.StrictLogging

//import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
import scala.util.chaining.*

/**
 * Default implementation of `InteractiveModule`.
 */
object DefaultInteractiveModule:
  extension [Out, Mat](source: Source[Out, Mat])
    private def prependingLastEmitted()(using materializer: Materializer): Source[Out, Mat] =
      source
        .conflate:
          (_, next: Out) => next
        .extrapolate:
          Iterator.continually(_)
        .runWith(BroadcastHub.sink(1))
        .drop(2) // Defeats Akka buffering
        .take(1)
        .pipe(source.prependLazy)

  //    private def prependingLastEmitted()(using materializer: Materializer): Source[Out, Mat] =
  //      val lastEmitted: AtomicReference[Out] = new AtomicReference[Out]()
  //      source.runForeach(lastEmitted.set)
  //
  //      Source
  //        .lazySingle:
  //          () => Option(lastEmitted.get)
  //        .collect:
  //          case Some(value: Out) => value
  //        .pipe(source.prepend)

trait DefaultInteractiveModule extends InteractiveModule with StrictLogging:
  this: AkkaModule & RejectedMessageModule & LanguagePollModule & WordCloudModule & QuestionsModule =>

  override val interactiveService: InteractiveService =
    new InteractiveService with SubscriptionSupport with StrictLogging:
      import DefaultInteractiveModule.*

      private given ec: ExecutionContext = system.executionContext

      // Source
      private val inputQueueSource: Source[ChatMessage | Reset.type, BoundedSourceQueue[ChatMessage | Reset.type]] =
        Source
          .queue[ChatMessage | Reset.type](64)
          .wireTap:
            case chatMessage: ChatMessage =>
              logger.info(s"Received chat message $chatMessage")
            case _ =>

      // Sinks
      private val languagePollSink: Sink[ChatMessage | Reset.type, (Counter, Source[Counts, NotUsed])] =
        languagePollFlow.toMat(BroadcastHub.sink(1))(Keep.both)
      private val wordCloudSink: Sink[ChatMessage | Reset.type, (Counter, Source[Counts, NotUsed])] =
        wordCloudFlow.toMat(BroadcastHub.sink(1))(Keep.both)
      private val questionsSink: Sink[ChatMessage | Reset.type, (Counter, Source[ChatMessages, NotUsed])] =
        questionsFlow.toMat(BroadcastHub.sink(1))(Keep.both)

      // Fully connected graph
      private val (
        inputQueue: BoundedSourceQueue[ChatMessage | Reset.type],
        languagePollSubscriptionCounter: Counter,
        languagePollSource: Source[Counts, NotUsed],
        wordCloudSubscriptionCounter: Counter,
        wordCloudSource: Source[Counts, NotUsed],
        questionsSubscriptionCounter: Counter,
        questionsSource: Source[ChatMessages, NotUsed]
      ) =
        inputQueueSource
          .alsoToMat(languagePollSink)(Keep.both)
          .alsoToMat(wordCloudSink)(Keep.both)
          .alsoToMat(questionsSink)(Keep.both)
          .to(Sink.ignore)
          .mapMaterializedValue:
            case (((a, (b, c)), (d, e)), (f, g)) => (a, b, c, d, e, f, g) // flatten
          .run()

      override def receiveChatMessage(chatMessage: ChatMessage): Future[Unit] =
        Future
          .successful:
            inputQueue.offer(chatMessage)
          .filter:
            _.isEnqueued
          .map:
            _ => ()

      override def reset(): Future[Unit] =
        Future
          .successful:
            inputQueue.offer(Reset)
          .filter:
            _.isEnqueued
          .map:
            _ => ()

      override val languagePoll: Flow[Any, Counts, NotUsed] =
        subscriptionTrackingFlow(
          languagePollSource.prependingLastEmitted(),
          languagePollSubscriptionCounter
        )

      override val wordCloud: Flow[Any, Counts, NotUsed] =
        subscriptionTrackingFlow(
          wordCloudSource.prependingLastEmitted(),
          wordCloudSubscriptionCounter
        )

      override val questions: Flow[Any, ChatMessages, NotUsed] =
        subscriptionTrackingFlow(
          questionsSource.prependingLastEmitted(),
          questionsSubscriptionCounter
        )

      override val rejectedMessages: Flow[Any, ChatMessage, NotUsed] =
        subscriptionTrackingFlow(
          rejectedMessagesSource,
          rejectedMessagesSubscriptionCounter
        )
