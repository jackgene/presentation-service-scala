package actors

import actors.ChatMessageBroadcaster.ChatMessage
import actors.common.JsonWriter
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import play.api.libs.json.{JsValue, Json, Writes}

/**
 * Actor that collects text from the moderation tool.
 *
 * Specifically, accepts text for messages where the sender is an empty string.
 */
object ModeratedTextCollector:
  enum Command:
    case Subscribe(subscriber: ActorRef[Event])
    case Unsubscribe(subscriber: ActorRef[Event])
    case Record(chatMessage: ChatMessage)
    case Reset

  object Event:
    // JSON
    private[ModeratedTextCollector] given writes: Writes[Event] =
      case chatMessages: ChatMessages =>
        Json.obj("chatText" -> chatMessages.chatText)
  enum Event:
    private[ModeratedTextCollector] case ChatMessages(chatText: Seq[String])

  def apply(
    chatMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command],
    rejectedMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command]
  ): Behavior[Command] =
    Behaviors.setup: (ctx: ActorContext[Command]) =>
      new ModeratedTextCollector(
        chatMessageBroadcaster, rejectedMessageBroadcaster,
        ctx.messageAdapter[ChatMessageBroadcaster.Event]:
          case ChatMessageBroadcaster.Event.New(chatMessage: ChatMessage) => Command.Record(chatMessage)
      ).initial

  /**
   * Publishes broadcast as JSON
   */
  object JsonPublisher:
    def apply(
      subscriber: ActorRef[JsValue], moderatedTextCollector: ActorRef[Command]
    ): Behavior[Event] =
      Behaviors.setup: (ctx: ActorContext[Event]) =>
        ctx.watch(moderatedTextCollector)
        moderatedTextCollector ! Command.Subscribe(ctx.self)

        JsonWriter(subscriber)

private class ModeratedTextCollector(
  chatMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command],
  rejectedMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command],
  adapter: ActorRef[ChatMessageBroadcaster.Event]
):
  import ModeratedTextCollector.*

  private def paused(
    text: IndexedSeq[String]
  ): Behavior[Command] =
    Behaviors.receive: (ctx: ActorContext[Command], cmd: Command) =>
      cmd match
        case Command.Reset => paused(IndexedSeq())

        case Command.Subscribe(subscriber: ActorRef[Event]) =>
          ctx.log.info(s"+1 ${ctx.self.path.name} subscriber (=1)")
          subscriber ! Event.ChatMessages(text)
          ctx.watchWith(subscriber, Command.Unsubscribe(subscriber))
          chatMessageBroadcaster ! ChatMessageBroadcaster.Command.Subscribe(adapter)
          running(text, Set(subscriber))

        // These are not expected during paused state
        case Command.Record(chatMessage: ChatMessage) =>
          ctx.log.warn(s"received unexpected record in paused state - $chatMessage")
          Behaviors.unhandled

        case Command.Unsubscribe(subscriber: ActorRef[Event]) =>
          ctx.log.warn(s"received unexpected unsubscription in paused state - ${subscriber.path}")
          Behaviors.unhandled

  private def running(
    text: IndexedSeq[String], subscribers: Set[ActorRef[Event]]
  ): Behavior[Command] =
    Behaviors.receive: (ctx: ActorContext[Command], cmd: Command) =>
      cmd match
        case Command.Record(chatMessage: ChatMessage) =>
          if chatMessage.sender != "" then
            rejectedMessageBroadcaster ! ChatMessageBroadcaster.Command.Record(chatMessage)
            Behaviors.same
          else
            val newText: IndexedSeq[String] = text :+ chatMessage.text
            for subscriber: ActorRef[Event] <- subscribers do
              subscriber ! Event.ChatMessages(newText)
            running(newText, subscribers)

        case Command.Reset =>
          for subscriber: ActorRef[Event] <- subscribers do
            subscriber ! Event.ChatMessages(IndexedSeq())
          running(IndexedSeq(), subscribers)

        case Command.Subscribe(subscriber: ActorRef[Event]) if !subscribers.contains(subscriber) =>
          ctx.log.info(s"+1 ${ctx.self.path.name} subscriber (=${subscribers.size + 1})")
          subscriber ! Event.ChatMessages(text)
          ctx.watchWith(subscriber, Command.Unsubscribe(subscriber))
          running(text, subscribers + subscriber)

        case Command.Subscribe(subscriber: ActorRef[Event]) =>
          ctx.log.warn(s"attempted to subscribe duplicate ${ctx.self.path.name} subscriber - ${subscriber.path}")
          Behaviors.unhandled

        case Command.Unsubscribe(subscriber: ActorRef[Event]) if subscribers.contains(subscriber) =>
          ctx.log.info(s"-1 ${ctx.self.path.name} subscriber (=${subscribers.size - 1})")
          ctx.unwatch(subscriber)
          val remainingSubscribers: Set[ActorRef[Event]] = subscribers - subscriber
          if remainingSubscribers.nonEmpty then
            running(text, remainingSubscribers)
          else
            chatMessageBroadcaster ! ChatMessageBroadcaster.Command.Unsubscribe(adapter)
            paused(text)

        case Command.Unsubscribe(subscriber: ActorRef[Event]) =>
          ctx.log.warn(s"attempted to unsubscribe unknown ${ctx.self.path.name} subscriber - ${subscriber.path}")
          Behaviors.unhandled

  val initial: Behavior[Command] = paused(IndexedSeq())
