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
object ChatMessageBroadcaster:
  object ChatMessage:
    // JSON
    given writes: Writes[ChatMessage] = (
      (JsPath \ "s").write[String] and
      (JsPath \ "r").write[String] and
      (JsPath \ "t").write[String]
    )(msg => (msg.sender, msg.recipient, msg.text))
  case class ChatMessage(
    sender: String,
    recipient: String,
    text: String
  ):
    override def toString: String = s"$sender to $recipient: $text"

  enum Command {
    case Subscribe(subscriber: ActorRef[Event])
    case Unsubscribe(subscriber: ActorRef[Event])
    case Record(chatMessage: ChatMessage)
  }

  object Event:
    // JSON
    private[ChatMessageBroadcaster] given writes: Writes[Event] =
      case New(chatMessage: ChatMessage) => Json.toJson(chatMessage)
  enum Event:
    case New(chatMessage: ChatMessage)

  private def running(
    subscribers: Set[ActorRef[Event]]
  ): Behavior[Command] =
    Behaviors.receive: (ctx: ActorContext[Command], cmd: Command) =>
      cmd match
        case Command.Record(chatMessage: ChatMessage) =>
          ctx.log.info(s"Received ${ctx.self.path.name} message - $chatMessage")
          for subscriber: ActorRef[Event] <- subscribers do
            subscriber ! Event.New(chatMessage)
          Behaviors.same

        case Command.Subscribe(subscriber: ActorRef[Event]) if !subscribers.contains(subscriber) =>
          ctx.log.info(s"+1 ${ctx.self.path.name} subscriber (=${subscribers.size + 1})")
          ctx.watchWith(subscriber, Command.Unsubscribe(subscriber))
          running(subscribers + subscriber)

        case Command.Subscribe(subscriber: ActorRef[Event]) =>
          ctx.log.warn(s"attempted to subscribe duplicate ${ctx.self.path.name} subscriber - ${subscriber.path}")
          Behaviors.unhandled

        case Command.Unsubscribe(subscriber: ActorRef[Event]) if subscribers.contains(subscriber) =>
          ctx.log.info(s"-1 ${ctx.self.path.name} subscriber (=${subscribers.size - 1})")
          ctx.unwatch(subscriber)
          running(subscribers - subscriber)

        case Command.Unsubscribe(subscriber: ActorRef[Event]) =>
          ctx.log.warn(s"attempted to unsubscribe unknown ${ctx.self.path.name} subscriber - ${subscriber.path}")
          Behaviors.unhandled

  def apply(): Behavior[Command] = running(Set())

  object JsonPublisher:
    def apply(
      subscriber: ActorRef[JsValue], broadcaster: ActorRef[Command]
    ): Behavior[Event] =
      Behaviors.setup: (ctx: ActorContext[Event]) =>
        ctx.watch(broadcaster)
        broadcaster ! Command.Subscribe(ctx.self)

        JsonWriter(subscriber)
