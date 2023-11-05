package actors

import actors.common.JsonWriter
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import play.api.libs.json.{JsValue, Json, Writes}

/**
 * Accumulates transcription texts, and broadcast them to subscribers.
 */
object TranscriptionBroadcaster:
  enum Command:
    case NewTranscriptionText(text: String)
    case Subscribe(subscriber: ActorRef[Event])
    case Unsubscribe(subscriber: ActorRef[Event])

  object Event:
    // JSON
    private[TranscriptionBroadcaster] given writes: Writes[Event] =
      case transcription: Transcription =>
        Json.obj("transcriptionText" -> transcription.text)
  enum Event:
    private[TranscriptionBroadcaster] case Transcription(text: String)

  private def running(
    subscribers: Set[ActorRef[Event]]
  ): Behavior[Command] =
    Behaviors.receive: (ctx: ActorContext[Command], cmd: Command) =>
      cmd match
        case Command.NewTranscriptionText(text: String) =>
          ctx.log.info(s"Received transcription text: $text")
          for subscriber: ActorRef[Event] <- subscribers do
            subscriber ! Event.Transcription(text)
          running(subscribers)

        case Command.Subscribe(subscriber: ActorRef[Event]) if !subscribers.contains(subscriber) =>
          ctx.log.info(s"+1 subscriber (=${subscribers.size + 1})")
          ctx.watchWith(subscriber, Command.Unsubscribe(subscriber))
          running(subscribers + subscriber)

        case Command.Subscribe(subscriber: ActorRef[Event]) =>
          ctx.log.warn(s"attempted to subscribe duplicate subscriber - ${subscriber.path}")
          Behaviors.unhandled

        case Command.Unsubscribe(subscriber: ActorRef[Event]) if subscribers.contains(subscriber) =>
          ctx.log.info(s"-1 subscriber (=${subscribers.size - 1})")
          ctx.unwatch(subscriber)
          running(subscribers - subscriber)

        case Command.Unsubscribe(subscriber: ActorRef[Event]) =>
          ctx.log.warn(s"attempted to unsubscribe unknown subscriber - ${subscriber.path}")
          Behaviors.unhandled

  def apply(): Behavior[Command] = running(Set())

  /**
   * Publishes broadcast as JSON
   */
  object JsonPublisher:
    def apply(
      subscriber: ActorRef[JsValue], broadcaster: ActorRef[Command]
    ): Behavior[Event] =
      Behaviors.setup: (ctx: ActorContext[Event]) =>
        ctx.watch(broadcaster)
        broadcaster ! Command.Subscribe(ctx.self)

        JsonWriter(subscriber)
