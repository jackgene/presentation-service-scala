package actors

import actors.common.JsonWriter
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import play.api.libs.functional.syntax.*
import play.api.libs.json.*

/**
 * Broadcasts chat messages to any subscriber.
 *
 * Subscribers can then use the chat messages to drive:
 * - Polls
 * - Word Clouds
 * - Statistics on chat messages
 * - Q&A questions
 * - Etc
 */
object ChatMessageBroadcaster {
  case class ChatMessage(
    sender: String,
    recipient: String,
    text: String
  ) {
    override def toString: String = s"$sender to $recipient: $text"
  }

  sealed trait Command
  final case class Subscribe(subscriber: ActorRef[Event]) extends Command
  final case class Unsubscribe(subscriber: ActorRef[Event]) extends Command
  final case class Record(chatMessage: ChatMessage) extends Command

  sealed trait Event
  final case class New(chatMessage: ChatMessage) extends Event

  // JSON
  implicit val writes: Writes[ChatMessage] = (
    (JsPath \ "s").write[String] and
    (JsPath \ "r").write[String] and
    (JsPath \ "t").write[String]
  )(unlift(ChatMessage.unapply))
  private implicit val eventWrites: Writes[Event] = {
    case New(chatMessage: ChatMessage) => Json.toJson(chatMessage)
  }

  private def running(
    subscribers: Set[ActorRef[Event]]
  ): Behavior[Command] = Behaviors.receive { (ctx: ActorContext[Command], cmd: Command) =>
    cmd match {
      case Record(chatMessage: ChatMessage) =>
        ctx.log.info(s"Received ${ctx.self.path.name} message - $chatMessage")
        for (subscriber: ActorRef[Event] <- subscribers) {
          subscriber ! New(chatMessage)
        }
        Behaviors.same

      case Subscribe(subscriber: ActorRef[Event]) if !subscribers.contains(subscriber) =>
        ctx.log.info(s"+1 ${ctx.self.path.name} subscriber (=${subscribers.size + 1})")
        ctx.watchWith(subscriber, Unsubscribe(subscriber))
        running(subscribers + subscriber)

      case Subscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"attempted to subscribe duplicate ${ctx.self.path.name} subscriber - ${subscriber.path}")
        Behaviors.unhandled

      case Unsubscribe(subscriber: ActorRef[Event]) if subscribers.contains(subscriber) =>
        ctx.log.info(s"-1 ${ctx.self.path.name} subscriber (=${subscribers.size - 1})")
        ctx.unwatch(subscriber)
        running(subscribers - subscriber)

      case Unsubscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"attempted to unsubscribe unknown ${ctx.self.path.name} subscriber - ${subscriber.path}")
        Behaviors.unhandled
    }
  }

  def apply(): Behavior[Command] = running(Set())

  object JsonPublisher {
    def apply(
      subscriber: ActorRef[JsValue], broadcaster: ActorRef[Command]
    ): Behavior[Event] = Behaviors.setup { (ctx: ActorContext[Event]) =>
      ctx.watch(broadcaster)
      broadcaster ! Subscribe(ctx.self)

      JsonWriter(subscriber)
    }
  }
}
