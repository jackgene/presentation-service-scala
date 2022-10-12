package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import model.ChatMessage

object ChatMessageActor {
  // Incoming messages
  case class Register(listener: ActorRef)
  case class Unregister(listener: ActorRef)

  // Incoming and Outgoing messages
  case class New(chatMessage: ChatMessage)

  val props: Props = Props(new ChatMessageActor)
}
private class ChatMessageActor extends Actor with ActorLogging {
  import ChatMessageActor._

  private def running(listeners: Set[ActorRef]): Receive = {
    case event @ New(chatMessage: ChatMessage) =>
      log.info(s"Received ${self.path.name} message - ${chatMessage}")
      for (listener: ActorRef <- listeners) {
        listener ! event
      }

    case Register(listener: ActorRef) =>
      context.watch(listener)
      context.become(
        running(listeners + listener)
      )
      log.info(
        s"+1 ${self.path.name} message listener (=${listeners.size + 1})"
      )

    case Unregister(listener: ActorRef) =>
      context.become(
        running(listeners - listener)
      )
      log.info(
        s"-1 ${self.path.name} message listener (=${listeners.size - 1})"
      )

    case Terminated(listener: ActorRef) if listeners.contains(listener) =>
      self ! Unregister(listener)
  }

  override val receive: Receive = running(Set())
}
