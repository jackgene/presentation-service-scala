package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import model.ChatMessage
import play.api.libs.json.Json

object ModerationWebSocketActor {
  def props(webSocketClient: ActorRef, chatMessageActor: ActorRef): Props =
    Props(new ModerationWebSocketActor(webSocketClient, chatMessageActor))
}
class ModerationWebSocketActor(webSocketClient: ActorRef, chatMessageActor: ActorRef)
    extends Actor with ActorLogging {
  chatMessageActor ! ChatMessageActor.Register(listener = self)

  override def receive: Receive = {
    case ChatMessageActor.New(msg: ChatMessage) =>
      webSocketClient ! Json.toJson(msg)
  }
}
