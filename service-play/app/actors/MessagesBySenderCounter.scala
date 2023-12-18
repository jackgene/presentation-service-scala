package actors

import actors.ChatMessageBroadcaster.ChatMessage
import actors.common.{JsonWriter, RateLimiter}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.jackleow.presentation.collection.MultiSet
import play.api.libs.json.{JsValue, Json, Writes}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/**
 * Counts messages grouping by senders.
 */
object MessagesBySenderCounter {
  enum Command {
    case Subscribe(subscriber: ActorRef[Event])
    case Unsubscribe(subscriber: ActorRef[Event])
    case Record(chatMessage: ChatMessage)
    case Reset
  }

  object Event {
    // JSON
    private[MessagesBySenderCounter] given writes: Writes[Event] = {
      case counts: Counts =>
        Json.obj("sendersAndCounts" -> counts.sendersByCount.toSeq)
    }
  }
  enum Event {
    private[MessagesBySenderCounter] case Counts(sendersByCount: Map[Int, Seq[String]])
  }

  private def running(
    senders: MultiSet[String], subscribers: Set[ActorRef[Event]]
  ): Behavior[Command] = Behaviors.receive { (ctx: ActorContext[Command], cmd: Command) =>
    cmd match {
      case Command.Record(chatMessage: ChatMessage) =>
        val sender: String = chatMessage.sender
        val newSenders: MultiSet[String] = senders + sender
        for (subscriber: ActorRef[Event] <- subscribers) {
          subscriber ! Event.Counts(newSenders.elementsByCount)
        }
        running(newSenders, subscribers)

      case Command.Reset =>
        for (subscriber: ActorRef[Event] <- subscribers) {
          subscriber ! Event.Counts(Map())
        }
        running(MultiSet[String](), subscribers)

      case Command.Subscribe(subscriber: ActorRef[Event]) if !subscribers.contains(subscriber) =>
        ctx.log.info(s"+1 subscriber (=${subscribers.size + 1})")
        subscriber ! Event.Counts(senders.elementsByCount)
        ctx.watchWith(subscriber, Command.Unsubscribe(subscriber))
        running(senders, subscribers + subscriber)

      case Command.Subscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"attempted to subscribe duplicate subscriber - ${subscriber.path}")
        Behaviors.unhandled

      case Command.Unsubscribe(subscriber: ActorRef[Event]) if subscribers.contains(subscriber) =>
        ctx.log.info(s"-1 subscriber (=${subscribers.size - 1})")
        ctx.unwatch(subscriber)
        running(senders, subscribers - subscriber)

      case Command.Unsubscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"attempted to unsubscribe unknown subscriber - ${subscriber.path}")
        Behaviors.unhandled
    }
  }

  def apply(
    chatMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command]
  ): Behavior[Command] = Behaviors.setup { (ctx: ActorContext[Command]) =>
    chatMessageBroadcaster ! ChatMessageBroadcaster.Command.Subscribe(
      ctx.messageAdapter {
        case ChatMessageBroadcaster.Event.New(chatMessage: ChatMessage) => Command.Record(chatMessage)
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
    ): Behavior[Event] = Behaviors.setup { (ctx: ActorContext[Event]) =>
      ctx.watch(counter)
      val rateLimitedCounter = ctx.spawn(
        RateLimiter(ctx.self, MinPeriodBetweenMessages), "rate-limiter"
      )
      counter ! Command.Subscribe(rateLimitedCounter)

      JsonWriter(subscriber)
    }
  }
}
