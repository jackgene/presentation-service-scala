package actors

import actors.counter.MultiSet
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import model.ChatMessage

object MessagesBySenderCounterActor {
  // Incoming messages
  case class Register(listener: ActorRef)

  // Outgoing messages
  case class Counts(sendersByCount: Map[Int,Seq[String]])

  def props(chatActor: ActorRef): Props = Props(
    new MessagesBySenderCounterActor(chatActor)
  )
}
private class MessagesBySenderCounterActor(chatActor: ActorRef)
    extends Actor with ActorLogging {
  import MessagesBySenderCounterActor.*

  chatActor ! ChatMessageActor.Register(self)

  private def running(
    senders: MultiSet[String], listeners: Set[ActorRef]
  ): Receive = {
    case ChatMessageActor.New(msg: ChatMessage) =>
      val sender: String = msg.sender
      val newSenders: MultiSet[String] =
        senders + sender
      for (listener: ActorRef <- listeners) {
        listener ! Counts(newSenders.elementsByCount)
      }
      context.become(
        running(newSenders, listeners)
      )

    case Register(listener: ActorRef) =>
      listener ! Counts(senders.elementsByCount)
      context.watch(listener)
      context.become(
        running(senders, listeners + listener)
      )
      log.info(s"+1 ${self.path.name} listener (=${listeners.size + 1})")

    case Terminated(listener: ActorRef) if listeners.contains(listener) =>
      context.become(
        running(senders, listeners - listener)
      )
      log.info(s"-1 ${self.path.name} listener (=${listeners.size - 1})")
  }

  override def receive: Receive = running(MultiSet[String](), Set())
}
