package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import model.{ChatMessage, SenderAndToken}
import play.api.libs.json.Json

import scala.concurrent.duration.{DurationInt, FiniteDuration}


object SendersByTokenCounterActor {
  // Incoming messages
  case class Register(listener: ActorRef)
  case object Reset

  // Outgoing messages
  case class Counts(tokensByCount: Map[Int,Seq[String]])

  def props(
      extractToken: String => Option[String],
      chatMessageActor: ActorRef, rejectedMessageActor: ActorRef):
      Props =
    Props(
      new SendersByTokenCounterActor(
        extractToken, chatMessageActor, rejectedMessageActor
      )
    )

  // WebSocket actor
  object WebSocketActor {
    object Send

    val BatchPeriod: FiniteDuration = 250.milliseconds

    def props(webSocketClient: ActorRef, counts: ActorRef): Props =
      Props(new WebSocketActor(webSocketClient, counts))
  }
  class WebSocketActor(webSocketClient: ActorRef, counts: ActorRef)
      extends Actor with ActorLogging {
    import WebSocketActor._
    import context.dispatcher
    counts ! SendersByTokenCounterActor.Register(listener = self)

    private val idle: Receive = {
      case SendersByTokenCounterActor.Counts(tokensByCount: Map[Int,Seq[String]]) =>
        context.system.scheduler.scheduleOnce(BatchPeriod, self, Send)
        context.become(awaitingSend(tokensByCount))
    }

    private def awaitingSend(tokensByCount: Map[Int,Seq[String]]): Receive = {
      case SendersByTokenCounterActor.Counts(tokensByCount: Map[Int,Seq[String]]) =>
        context.become(awaitingSend(tokensByCount))

      case Send =>
        webSocketClient ! Json.toJson(tokensByCount.toSeq) // JSON keys must be strings
        context.become(idle)
    }

    override val receive: Receive = idle
  }
}
private class SendersByTokenCounterActor(
    extractToken: String => Option[String],
    chatMessageActor: ActorRef, rejectedMessageActor: ActorRef)
    extends Actor with ActorLogging {
  import SendersByTokenCounterActor._

  private val emptyTokensBySender: Map[String, FifoFixedSizeSet[String]] =
    Map().withDefaultValue(FifoFixedSizeSet.sized(3))

  private def paused(
      chatMessages: IndexedSeq[ChatMessage], sendersAndTokens: IndexedSeq[SenderAndToken],
      tokensBySender: Map[String, FifoFixedSizeSet[String]], tokenCount: Frequencies):
      Receive = {
    case Reset =>
      context.become(
        paused(IndexedSeq(), IndexedSeq(), emptyTokensBySender, Frequencies())
      )

    case Register(listener: ActorRef) =>
      chatMessageActor ! ChatMessageActor.Register(self)
      listener ! Counts(tokenCount.itemsByCount)
      context.watch(listener)
      context.become(
        running(
          chatMessages, sendersAndTokens, tokensBySender, tokenCount, Set(listener)
        )
      )
      log.info(
        "+1 senders by token count listener (=1)"
      )
  }

  private def running(
      chatMessages: IndexedSeq[ChatMessage], sendersAndTokens: IndexedSeq[SenderAndToken],
      tokensBySender: Map[String, FifoFixedSizeSet[String]], tokenFrequencies: Frequencies,
      listeners: Set[ActorRef]): Receive = {
    case event @ ChatMessageActor.New(msg: ChatMessage) =>
      val senderOpt: Option[String] = Option(msg.sender).filter { _ != "" }
      val newTokenOpt: Option[String] = extractToken(msg.text)

      newTokenOpt match {
        case Some(newToken: String) =>
          log.info(s"Extracted token \"${newToken}\"")
          val (
            newTokensBySender: Map[String, FifoFixedSizeSet[String]],
            updated: Option[Option[String]]
          ) = senderOpt match {
            case Some(sender: String) =>
              val (tokens: FifoFixedSizeSet[String], updated: Option[Option[String]]) =
                tokensBySender(sender).add(newToken)
              (tokensBySender.updated(sender, tokens), updated)

            case None => (tokensBySender, Some(None))
          }
          val newTokenFrequencies: Frequencies = updated match {
            case Some(oldTokenOpt: Option[String]) =>
              val newTokenFrequencies = oldTokenOpt.
                // Only remove old token if there's a valid new token replacing it
                foldLeft(tokenFrequencies) { (freqs: Frequencies, oldToken: String) =>
                  freqs.updated(oldToken, -1)
                }.
                updated(newToken, 1)
              for (listener: ActorRef <- listeners) {
                listener ! Counts(newTokenFrequencies.itemsByCount)
              }
              newTokenFrequencies

            case None => tokenFrequencies
          }

          context.become(
            running(
              chatMessages :+ msg,
              sendersAndTokens :+ SenderAndToken(msg.sender, newToken),
              newTokensBySender, newTokenFrequencies, listeners
            )
          )

        case None =>
          log.info("No token extracted")
          rejectedMessageActor ! event
          context.become(
            running(
              chatMessages :+ msg,
              sendersAndTokens, tokensBySender, tokenFrequencies, listeners
            )
          )
      }

    case Reset =>
      for (listener: ActorRef <- listeners) {
        listener ! Counts(Map())
      }
      context.become(
        running(
          IndexedSeq(), IndexedSeq(), emptyTokensBySender, Frequencies(), listeners
        )
      )

    case Register(listener: ActorRef) =>
      listener ! Counts(tokenFrequencies.itemsByCount)
      context.watch(listener)
      context.become(
        running(
          chatMessages, sendersAndTokens, tokensBySender, tokenFrequencies,
          listeners + listener
        )
      )
      log.info(s"+1 ${self.path.name} listener (=${listeners.size + 1})")

    case Terminated(listener: ActorRef) if listeners.contains(listener) =>
      val remainingListeners: Set[ActorRef] = listeners - listener
      if (remainingListeners.nonEmpty) {
        context.become(
          running(
            chatMessages, sendersAndTokens, tokensBySender, tokenFrequencies,
            remainingListeners
          )
        )
      } else {
        chatMessageActor ! ChatMessageActor.Unregister(self)
        context.become(
          paused(chatMessages, sendersAndTokens, tokensBySender, tokenFrequencies)
        )
      }
      log.info(s"-1 ${self.path.name} listener (=${listeners.size - 1})")
  }

  override def receive: Receive = paused(
    IndexedSeq(), IndexedSeq(), emptyTokensBySender, Frequencies()
  )
}
