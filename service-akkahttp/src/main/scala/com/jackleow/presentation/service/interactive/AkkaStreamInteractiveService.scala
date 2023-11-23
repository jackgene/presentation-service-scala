package com.jackleow.presentation.service.interactive

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.stream.{BoundedSourceQueue, ClosedShape}
import akka.stream.scaladsl.{Broadcast, BroadcastHub, Flow, GraphDSL, Keep, Merge, Partition, RunnableGraph, Sink, Source}
import com.typesafe.scalalogging.StrictLogging

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

class AkkaStreamInteractiveService(implicit val system: ActorSystem[Nothing])
  extends InteractiveService with StrictLogging {

  import InteractiveService.*

  private implicit val ec: ExecutionContext = system.executionContext
  private val activeRejectedMessagesFlows: AtomicInteger = new AtomicInteger(0)
  private val chatMessageSource: Source[ChatMessage, BoundedSourceQueue[ChatMessage]] = Source.
    queue[ChatMessage](1).
    wireTap { (chatMessage: ChatMessage) =>
      logger.info(s"Received chat message $chatMessage")
    }
  private val questionsSink: Sink[ModeratedText, Source[ModeratedText, NotUsed]] =
    BroadcastHub.sink[ModeratedText](1)
  private val rejectedMessagesSink: Sink[ChatMessage, Source[ChatMessage, NotUsed]] =
    BroadcastHub.sink[ChatMessage](1)
  private val graph: RunnableGraph[(BoundedSourceQueue[ChatMessage], Source[ModeratedText, NotUsed], Source[ChatMessage, NotUsed])] =
    RunnableGraph.fromGraph(
      GraphDSL.createGraph(
        chatMessageSource, questionsSink, rejectedMessagesSink
      )((_, _, _)) { implicit builder => (chatMessageSource, questionsSink, rejectedMessagesSink) =>
        import GraphDSL.Implicits.*

        val chatMessageBroadcast = builder.add(Broadcast[ChatMessage](1))
        chatMessageSource ~> chatMessageBroadcast

        val moderate = builder.add(
          Partition[ChatMessage](2, { (msg: ChatMessage) => if (msg.sender == "") 1 else 0 })
        )
        val collectModeratedText: Flow[ChatMessage, ModeratedText, NotUsed] = Flow[ChatMessage].
          scan[List[String]](Nil) { (accum, next) =>
            accum :+ next.text
          }.
          map(ModeratedText).
          extrapolate(Iterator.continually(_))
        chatMessageBroadcast ~> moderate ~> rejectedMessagesSink
                                moderate ~> collectModeratedText ~> questionsSink

        ClosedShape
      }
    )
  private val (queue, questionsSource, rejectedMessagesSource) = graph.run()
  private val flow: Flow[Any, ChatMessage, NotUsed] =
    Flow.fromSinkAndSourceCoupled(
      Sink.onComplete { _ =>
        val subscribers = activeRejectedMessagesFlows.decrementAndGet()
        logger.info(s"-1 subscriber (=$subscribers)")
      },
      rejectedMessagesSource
    )

  override def receiveChatMessage(chatMessage: ChatMessage): Future[Unit] =
    Future.
      successful { queue.offer(chatMessage) }.
      filter(_.isEnqueued).
      map(_ => ())

  override def languagePoll: Flow[Any, Counts, NotUsed] = ???

  override def wordCloud: Flow[Any, Counts, NotUsed] = ???

  override def questions: Flow[Any, ModeratedText, NotUsed] =
    Flow.fromSinkAndSourceCoupled(
      Sink.ignore,
      questionsSource.
        scan[(ModeratedText, Option[ModeratedText])](ModeratedText(Nil), None) {
          case ((last, _), next) if last == next => (next, None)
          case (_, next) => (next, Some(next))
        }.
        collect {
          case (_, Some(yo)) => yo
        }
    )

  override def rejectedMessages: Flow[Any, ChatMessage, NotUsed] = {
    val subscribers: Int = activeRejectedMessagesFlows.incrementAndGet()
    logger.info(s"+1 subscriber (=$subscribers)")

    flow
  }
}
