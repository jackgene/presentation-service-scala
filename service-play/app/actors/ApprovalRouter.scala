package actors

import actors.common.JsonWriter
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import model.ChatMessage
import play.api.libs.json.{JsValue, Json, Writes}

/**
 * Actor that accepts messages only from the moderation tool.
 *
 * Specifically, accepts messages where the sender is an emptpy string.
 */
object ApprovalRouter {
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
  ): Behavior[Command] =
    new ApprovalRouter(chatMessageBroadcaster, rejectedMessageBroadcaster).initial()

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

  /**
   * Translates [[ChatMessageBroadcaster.Event]]s to [[Event]]s.
   */
  private object ChatBroadcastAdapter {
    private type BroadcastEvent = ChatMessageBroadcaster.Event
    private type BroadcastCommand = ChatMessageBroadcaster.Command

    def apply(
      source: ActorRef[BroadcastCommand], destination: ActorRef[Command]
    ): Behavior[BroadcastEvent] = Behaviors.setup { ctx: ActorContext[BroadcastEvent] =>
      source ! ChatMessageBroadcaster.Subscribe(ctx.self)

      Behaviors.receive {
        case (_, ChatMessageBroadcaster.New(chatMessage: ChatMessage)) =>
          destination ! Record(chatMessage)
          Behaviors.same
      }
    }
  }
}
private class ApprovalRouter(
  chatMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command],
  rejectedMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command]
) {
  import ApprovalRouter.*

  private def paused(
    text: IndexedSeq[String]
  ): Behavior[Command] = Behaviors.receive { (ctx: ActorContext[Command], cmd: Command) =>
    cmd match {
      case Reset => paused(IndexedSeq())

      case Subscribe(subscriber: ActorRef[Event]) =>
        ctx.log.info(s"+1 ${ctx.self.path.name} subscriber (=1)")
        subscriber ! ChatMessages(text)
        ctx.watchWith(subscriber, Unsubscribe(subscriber))
        val adapter: ActorRef[ChatMessageBroadcaster.Event] = ctx.spawn(
          ChatBroadcastAdapter(chatMessageBroadcaster, ctx.self), "adapter"
        )
        running(text, Set(subscriber), adapter)

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
    text: IndexedSeq[String], subscribers: Set[ActorRef[Event]],
    adapter: ActorRef[ChatMessageBroadcaster.Event]
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
          running(newText, subscribers, adapter)
        }

      case Reset =>
        for (subscriber: ActorRef[Event] <- subscribers) {
          subscriber ! ChatMessages(IndexedSeq())
        }
        running(IndexedSeq(), subscribers, adapter)

      case Subscribe(subscriber: ActorRef[Event]) if !subscribers.contains(subscriber) =>
        ctx.log.info(s"+1 ${ctx.self.path.name} subscriber (=${subscribers.size + 1})")
        subscriber ! ChatMessages(text)
        ctx.watchWith(subscriber, Unsubscribe(subscriber))
        running(text, subscribers + subscriber, adapter)

      case Subscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"attempted to subscribe duplicate ${ctx.self.path.name} subscriber - ${subscriber.path}")
        Behaviors.unhandled

      case Unsubscribe(subscriber: ActorRef[Event]) if subscribers.contains(subscriber) =>
        ctx.log.info(s"-1 ${ctx.self.path.name} subscriber (=${subscribers.size - 1})")
        ctx.unwatch(subscriber)
        val remainingSubscribers: Set[ActorRef[Event]] = subscribers - subscriber
        if (remainingSubscribers.nonEmpty) {
          running(text, remainingSubscribers, adapter)
        } else {
          ctx.stop(adapter)
          paused(text)
        }

      case Unsubscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"attempted to unsubscribe unknown ${ctx.self.path.name} subscriber - ${subscriber.path}")
        Behaviors.unhandled
    }
  }

  def initial(): Behavior[Command] =  paused(IndexedSeq())
}
