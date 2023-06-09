package actors

import actors.common.JsonWriter
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import play.api.libs.json.{JsValue, Json, Writes}

/**
 * Accumulates transcription texts, and broadcast them to subscribers.
 */
object TranscriptionBroadcaster {
  sealed trait Command
  final case class NewTranscriptionText(text: String) extends Command
  final case class Subscribe(subscriber: ActorRef[Event]) extends Command
  final case class Unsubscribe(subscriber: ActorRef[Event]) extends Command

  sealed trait Event
  private final case class Transcription(text: String) extends Event

  // JSON
  private implicit val eventWrites: Writes[Event] = {
    case transcription: Transcription =>
      Json.obj("transcriptionText" -> transcription.text)
  }

  private def running(
    text: String, subscribers: Set[ActorRef[Event]]
  ): Behavior[Command] = Behaviors.receive { (ctx: ActorContext[Command], cmd: Command) =>
    cmd match {
      case NewTranscriptionText(text: String) =>
        ctx.log.info(s"Received transcription text: $text")
        for (subscriber: ActorRef[Event] <- subscribers) {
          subscriber ! Transcription(text)
        }
        running(text, subscribers)

      case Subscribe(subscriber: ActorRef[Event]) if !subscribers.contains(subscriber) =>
        ctx.log.info(s"+1 subscriber (=${subscribers.size + 1})")
        subscriber ! Transcription(text)
        ctx.watchWith(subscriber, Unsubscribe(subscriber))
        running(text, subscribers + subscriber)

      case Subscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"attempted to subscribe duplicate subscriber - ${subscriber.path}")
        Behaviors.unhandled

      case Unsubscribe(subscriber: ActorRef[Event]) if subscribers.contains(subscriber) =>
        ctx.log.info(s"-1 subscriber (=${subscribers.size - 1})")
        ctx.unwatch(subscriber)
        running(text, subscribers - subscriber)

      case Unsubscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"attempted to unsubscribe unknown subscriber - ${subscriber.path}")
        Behaviors.unhandled
    }
  }

  def apply(): Behavior[Command] = running("", Set())

  /**
   * Publishes broadcast as JSON
   */
  object JsonPublisher {
    def apply(
      subscriber: ActorRef[JsValue], broadcaster: ActorRef[Command]
    ): Behavior[Event] = Behaviors.setup { ctx: ActorContext[Event] =>
      ctx.watch(broadcaster)
      broadcaster ! Subscribe(ctx.self)

      JsonWriter(subscriber)
    }
  }
}