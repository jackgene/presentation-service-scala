package actors

import actors.common.JsonWriter
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import model.ChatMessage
import play.api.libs.json.{JsValue, Json, Writes}

/**
 * Actor that collects text from the moderation tool.
 *
 * Specifically, accepts text for messages where the sender is an empty string.
 */
object ModeratedTextCollector {
  sealed trait Command
  final case class Subscribe(subscriber: ActorRef[Event]) extends Command
  final case class Unsubscribe(subscriber: ActorRef[Event]) extends Command
  final case class Record(chatMessage: ChatMessage) extends Command
  final case object Reset extends Command

  sealed trait Event
  private final case class ChatMessages(chatText: Seq[String]) extends Event

  // JSON
  private implicit val eventWrites: Writes[Event] = {
    case chatMessages: ChatMessages =>
      Json.obj("chatText" -> chatMessages.chatText)
  }

  def apply(
    chatMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command],
    rejectedMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command]
  ): Behavior[Command] = Behaviors.setup { ctx =>
    new ModeratedTextCollector(
      chatMessageBroadcaster, rejectedMessageBroadcaster,
      ctx.messageAdapter[ChatMessageBroadcaster.Event] {
        case ChatMessageBroadcaster.New(chatMessage: ChatMessage) => Record(chatMessage)
      }
    ).initial()
  }

  /**
   * Publishes broadcast as JSON
   */
  object JsonPublisher {
    def apply(
      subscriber: ActorRef[JsValue], approvalRouter: ActorRef[Command]
    ): Behavior[Event] = Behaviors.setup { ctx: ActorContext[Event] =>
      ctx.watch(approvalRouter)
      approvalRouter ! Subscribe(ctx.self)

      JsonWriter(subscriber)
    }
  }
}
private class ModeratedTextCollector(
  chatMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command],
  rejectedMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command],
  adapter: ActorRef[ChatMessageBroadcaster.Event]
) {
  import ModeratedTextCollector.*

  private def paused(
    text: IndexedSeq[String]
  ): Behavior[Command] = Behaviors.receive { (ctx: ActorContext[Command], cmd: Command) =>
    cmd match {
      case Reset => paused(IndexedSeq())

      case Subscribe(subscriber: ActorRef[Event]) =>
        ctx.log.info(s"+1 ${ctx.self.path.name} subscriber (=1)")
        subscriber ! ChatMessages(text)
        ctx.watchWith(subscriber, Unsubscribe(subscriber))
        chatMessageBroadcaster ! ChatMessageBroadcaster.Subscribe(adapter)
        running(text, Set(subscriber))

      // These are not expected during paused state
      case Record(chatMessage: ChatMessage) =>
        ctx.log.warn(s"received unexpected record in paused state - $chatMessage")
        Behaviors.unhandled

      case Unsubscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"received unexpected unsubscription in paused state - ${subscriber.path}")
        Behaviors.unhandled
    }
  }

  private def running(
    text: IndexedSeq[String], subscribers: Set[ActorRef[Event]]
  ): Behavior[Command] = Behaviors.receive { (ctx: ActorContext[Command], cmd: Command) =>
    cmd match {
      case Record(chatMessage: ChatMessage) =>
        if (chatMessage.sender != "") {
          rejectedMessageBroadcaster ! ChatMessageBroadcaster.Record(chatMessage)
          Behaviors.same
        } else {
          val newText: IndexedSeq[String] = text :+ chatMessage.text
          for (subscriber: ActorRef[Event] <- subscribers) {
            subscriber ! ChatMessages(newText)
          }
          running(newText, subscribers)
        }

      case Reset =>
        for (subscriber: ActorRef[Event] <- subscribers) {
          subscriber ! ChatMessages(IndexedSeq())
        }
        running(IndexedSeq(), subscribers)

      case Subscribe(subscriber: ActorRef[Event]) if !subscribers.contains(subscriber) =>
        ctx.log.info(s"+1 ${ctx.self.path.name} subscriber (=${subscribers.size + 1})")
        subscriber ! ChatMessages(text)
        ctx.watchWith(subscriber, Unsubscribe(subscriber))
        running(text, subscribers + subscriber)

      case Subscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"attempted to subscribe duplicate ${ctx.self.path.name} subscriber - ${subscriber.path}")
        Behaviors.unhandled

      case Unsubscribe(subscriber: ActorRef[Event]) if subscribers.contains(subscriber) =>
        ctx.log.info(s"-1 ${ctx.self.path.name} subscriber (=${subscribers.size - 1})")
        ctx.unwatch(subscriber)
        val remainingSubscribers: Set[ActorRef[Event]] = subscribers - subscriber
        if (remainingSubscribers.nonEmpty) {
          running(text, remainingSubscribers)
        } else {
          chatMessageBroadcaster ! ChatMessageBroadcaster.Unsubscribe(adapter)
          paused(text)
        }

      case Unsubscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"attempted to unsubscribe unknown ${ctx.self.path.name} subscriber - ${subscriber.path}")
        Behaviors.unhandled
    }
  }

  def initial(): Behavior[Command] =  paused(IndexedSeq())
}
