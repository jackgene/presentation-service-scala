package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import model.ChatMessage
import play.api.libs.json._

object ApprovalRouterActor {
  // Incoming messages
  case class Register(listener: ActorRef)
  case object Reset

  // Outgoing messages
  case class ChatMessages(text: Seq[String])

  // JSON
  private implicit val chatMessagesWrites: Writes[ChatMessages] =
    (chatMessages: ChatMessages) => Json.obj("chatText" -> chatMessages.text)

  def props(chatMessageActor: ActorRef, rejectedMessageActor: ActorRef): Props =
    Props(new ApprovalRouterActor(chatMessageActor, rejectedMessageActor: ActorRef))

  // WebSocket actor
  object WebSocketActor {
    def props(webSocketClient: ActorRef, messages: ActorRef): Props =
      Props(new WebSocketActor(webSocketClient, messages))
  }
  class WebSocketActor(webSocketClient: ActorRef, messages: ActorRef)
      extends Actor with ActorLogging {
    messages ! ApprovalRouterActor.Register(listener = self)

    override def receive: Receive = {
      case chatMsgs: ApprovalRouterActor.ChatMessages =>
        webSocketClient ! Json.toJson(chatMsgs)
    }
  }
}
private class ApprovalRouterActor(
    chatMessageActor: ActorRef, rejectedMessageActor: ActorRef)
    extends Actor with ActorLogging {
  import ApprovalRouterActor._

  private def paused(text: IndexedSeq[String]): Receive = {
    case Reset =>
      context.become(paused(IndexedSeq()))

    case Register(listener: ActorRef) =>
      chatMessageActor ! ChatMessageActor.Register(self)
      listener ! ChatMessages(text)
      context.watch(listener)
      context.become(
        running(text, Set(listener))
      )
      log.info(s"+1 ${self.path.name} listener (=1)")
  }

  private def running(text: IndexedSeq[String], listeners: Set[ActorRef]): Receive = {
    case event @ ChatMessageActor.New(msg: ChatMessage) =>
      if (msg.sender != "") {
        rejectedMessageActor ! event
      } else {
        val newText: IndexedSeq[String] = text :+ msg.text
        for (listener: ActorRef <- listeners) {
          listener ! ChatMessages(newText)
        }
        context.become(running(newText, listeners))
      }

    case Reset =>
      for (listener: ActorRef <- listeners) {
        listener ! ChatMessages(IndexedSeq())
      }
      context.become(
        running(IndexedSeq(), listeners)
      )

    case Register(listener: ActorRef) =>
      listener ! ChatMessages(text)
      context.watch(listener)
      context.become(
        running(text, listeners + listener)
      )
      log.info(s"+1 ${self.path.name} listener (=${listeners.size + 1})")

    case Terminated(listener: ActorRef) if listeners.contains(listener) =>
      val remainingListeners: Set[ActorRef] = listeners - listener
      if (remainingListeners.nonEmpty) {
        context.become(
          running(text, remainingListeners)
        )
      } else {
        chatMessageActor ! ChatMessageActor.Unregister(self)
        context.become(
          paused(text)
        )
      }
      log.info(s"-1 ${self.path.name} listener (=${listeners.size - 1})")
  }

  override def receive: Receive = paused(IndexedSeq())
}
