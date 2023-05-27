package actors

import actors.common.{JsonWriter, RateLimiter}
import actors.counter.MultiSet
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import model.ChatMessage
import play.api.libs.json.{JsValue, Json, Writes}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/**
 * Counts messages grouping by senders.
 */
object MessagesBySenderCounter {
  sealed trait Command
  final case class Subscribe(subscriber: ActorRef[Event]) extends Command
  final case class Unsubscribe(subscriber: ActorRef[Event]) extends Command
  final case class Record(chatMessage: ChatMessage) extends Command
  final case object Reset extends Command

  sealed trait Event
  final case class Counts(sendersByCount: Map[Int,Seq[String]]) extends Event

  // JSON
  private implicit val eventWrites: Writes[Event] = {
    case counts: Counts =>
      Json.obj("sendersAndCounts" -> counts.sendersByCount.toSeq)
  }

  private def running(
    senders: MultiSet[String], subscribers: Set[ActorRef[Event]]
  ): Behavior[Command] = Behaviors.receive { (ctx: ActorContext[Command], cmd: Command) =>
    cmd match {
      case Record(chatMessage: ChatMessage) =>
        val sender: String = chatMessage.sender
        val newSenders: MultiSet[String] = senders + sender
        for (subscriber: ActorRef[Event] <- subscribers) {
          subscriber ! Counts(newSenders.elementsByCount)
        }
        running(newSenders, subscribers)

      case Reset =>
        for (subscriber: ActorRef[Event] <- subscribers) {
          subscriber ! Counts(Map())
        }
        running(MultiSet[String](), subscribers)

      case Subscribe(subscriber: ActorRef[Event]) if !subscribers.contains(subscriber) =>
        ctx.log.info(s"+1 subscriber (=${subscribers.size + 1})")
        subscriber ! Counts(senders.elementsByCount)
        ctx.watchWith(subscriber, Unsubscribe(subscriber))
        running(senders, subscribers + subscriber)

      case Subscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"attempted to subscribe duplicate subscriber - ${subscriber.path}")
        Behaviors.unhandled

      case Unsubscribe(subscriber: ActorRef[Event]) if subscribers.contains(subscriber) =>
        ctx.log.info(s"-1 subscriber (=${subscribers.size - 1})")
        ctx.unwatch(subscriber)
        running(senders, subscribers - subscriber)

      case Unsubscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"attempted to unsubscribe unknown subscriber - ${subscriber.path}")
        Behaviors.unhandled
    }
  }

  def apply(
    chatMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command]
  ): Behavior[Command] = Behaviors.setup { ctx: ActorContext[Command] =>
    chatMessageBroadcaster ! ChatMessageBroadcaster.Subscribe(
      ctx.messageAdapter {
        case ChatMessageBroadcaster.New(chatMessage: ChatMessage) => Record(chatMessage)
      }
    )

    running(MultiSet[String](), Set())
  }

  /**
   * Publishes broadcast as JSON - Rate Limited
   */
  object JsonPublisher {
    private val MinPeriodBetweenMessages: FiniteDuration = 100.milliseconds

    def apply(
      subscriber: ActorRef[JsValue], counter: ActorRef[Command]
    ): Behavior[Event] = Behaviors.setup { ctx: ActorContext[Event] =>
      ctx.watch(counter)
      val rateLimitedCounter = ctx.spawn(
        RateLimiter(ctx.self, MinPeriodBetweenMessages), "rate-limiter"
      )
      counter ! Subscribe(rateLimitedCounter)

      JsonWriter(subscriber)
    }
  }
}
