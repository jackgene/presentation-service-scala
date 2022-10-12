package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import model.ChatMessage

object MessagesBySenderCounterActor {
  // Incoming messages
  case class Register(listener: ActorRef)

  // Outgoing messages
  case class Counts(sendersByCount: Map[Int,Seq[String]])

  def props(chatActor: ActorRef): Props = Props(new MessagesBySenderCounterActor(chatActor))
}
private class MessagesBySenderCounterActor(chatActor: ActorRef) extends Actor with ActorLogging {
  import MessagesBySenderCounterActor._

  chatActor ! ChatMessageActor.Register(self)

  private def running(senderFrequencies: Frequencies, listeners: Set[ActorRef]): Receive = {
    case ChatMessageActor.New(msg: ChatMessage) =>
      val sender: String = msg.sender
      val newSenderFrequencies: Frequencies = senderFrequencies.updated(sender, 1)
      for (listener: ActorRef <- listeners) {
        listener ! Counts(newSenderFrequencies.itemsByCount)
      }
      context.become(
        running(newSenderFrequencies, listeners)
      )

    case Register(listener: ActorRef) =>
      listener ! Counts(senderFrequencies.itemsByCount)
      context.watch(listener)
      context.become(
        running(senderFrequencies, listeners + listener)
      )
      log.info(s"+1 ${self.path.name} listener (=${listeners.size + 1})")

    case Terminated(listener: ActorRef) if listeners.contains(listener) =>
      context.become(
        running(senderFrequencies, listeners - listener)
      )
      log.info(s"-1 ${self.path.name} listener (=${listeners.size - 1})")
  }

  override def receive: Receive = running(Frequencies(), Set())
}
