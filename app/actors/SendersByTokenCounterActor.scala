package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import model.ChatMessage
import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object SendersByTokenCounterActor {
  // Incoming messages
  case class Register(listener: ActorRef)
  case object Reset

  // Outgoing messages
  case class ChatMessageAndTokens(
    chatMessage: ChatMessage,
    tokens: Seq[String]
  ) {
    override def toString: String = s"${chatMessage} -> ${tokens.mkString("[", ",", "]")}"
  }
  case class Counts(
    chatMessagesAndTokens: IndexedSeq[ChatMessageAndTokens],
    tokensBySender: Map[String, FifoFixedSizeSet[String]],
    tokensByCount: Frequencies
  )

  // JSON
  private implicit val chatMessageAndTokensWrites: Writes[ChatMessageAndTokens] =
    (
      (JsPath \ "chatMessage").write[ChatMessage] and
      (JsPath \ "tokens").write[Seq[String]]
    )(unlift(ChatMessageAndTokens.unapply))
  private implicit val countsWrites: Writes[Counts] =
    (counts: Counts) => Json.obj(
      "chatMessagesAndTokens" -> counts.chatMessagesAndTokens,
      "tokensBySender" -> counts.tokensBySender.view.mapValues(_.toSeq),
      "tokensByCount" -> counts.tokensByCount.itemsByCount.toSeq // JSON keys must be strings
    )

  def props(
      extractToken: String => Seq[String],
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
      case counts: SendersByTokenCounterActor.Counts =>
        context.system.scheduler.scheduleOnce(BatchPeriod, self, Send)
        context.become(awaitingSend(counts))
    }

    private def awaitingSend(counts: SendersByTokenCounterActor.Counts): Receive = {
      case counts: SendersByTokenCounterActor.Counts =>
        context.become(awaitingSend(counts))

      case Send =>
        webSocketClient ! Json.toJson(counts)
        context.become(idle)
    }

    override val receive: Receive = idle
  }
}
private class SendersByTokenCounterActor(
    extractToken: String => Seq[String],
    chatMessageActor: ActorRef, rejectedMessageActor: ActorRef)
    extends Actor with ActorLogging {
  import SendersByTokenCounterActor._

  private val emptyTokensBySender: Map[String, FifoFixedSizeSet[String]] =
    Map().withDefaultValue(FifoFixedSizeSet.sized(3))

  private def paused(
      chatMessagesAndTokens: IndexedSeq[ChatMessageAndTokens],
      tokensBySender: Map[String, FifoFixedSizeSet[String]], tokenCount: Frequencies):
      Receive = {
    case Reset =>
      context.become(
        paused(IndexedSeq(), emptyTokensBySender, Frequencies())
      )

    case Register(listener: ActorRef) =>
      chatMessageActor ! ChatMessageActor.Register(self)
      listener ! Counts(
        chatMessagesAndTokens, tokensBySender, tokenCount
      )
      context.watch(listener)
      context.become(
        running(
          chatMessagesAndTokens, tokensBySender, tokenCount, Set(listener)
        )
      )
      log.info(
        "+1 senders by token count listener (=1)"
      )
  }

  private def running(
      chatMessagesAndTokens: IndexedSeq[ChatMessageAndTokens],
      tokensBySender: Map[String, FifoFixedSizeSet[String]], tokenFrequencies: Frequencies,
      listeners: Set[ActorRef]): Receive = {
    case event @ ChatMessageActor.New(msg: ChatMessage) =>
      val senderOpt: Option[String] = Option(msg.sender).filter { _ != "" }
      val newTokens: Seq[String] = extractToken(msg.text)

      newTokens match {
        case Nil =>
          log.info("No token extracted")
          rejectedMessageActor ! event
          context.become(
            running(
              chatMessagesAndTokens :+ ChatMessageAndTokens(msg, Nil),
              tokensBySender, tokenFrequencies, listeners
            )
          )
        case newTokens =>
          log.info(s"Extracted tokens ${newTokens.mkString("\"", "\", \"", "\"")}")
          val newChatMessagesAndTokens: IndexedSeq[ChatMessageAndTokens] =
            chatMessagesAndTokens :+ ChatMessageAndTokens(msg, newTokens)
          val (
            newTokensBySender: Map[String, FifoFixedSizeSet[String]],
            updated: Option[Seq[String]]
          ) = senderOpt match {
            case Some(sender: String) =>
              val (tokens: FifoFixedSizeSet[String], updated: Option[Seq[String]]) =
                tokensBySender(sender).addAll(newTokens)
              (tokensBySender.updated(sender, tokens), updated)

            case None => (tokensBySender, Some(Nil))
          }
          val newTokenFrequencies: Frequencies = updated match {
            case Some(oldTokens: Seq[String]) =>
              val newTokenFrequencies = newTokens.
                foldLeft(
                  oldTokens.foldLeft(tokenFrequencies) { (freqs: Frequencies, oldToken: String) =>
                    freqs.updated(oldToken, -1)
                  }
                ) { (freqs: Frequencies, newToken: String) =>
                  freqs.updated(newToken, 1)
                }
              for (listener: ActorRef <- listeners) {
                listener ! Counts(
                  newChatMessagesAndTokens, newTokensBySender, newTokenFrequencies
                )
              }
              newTokenFrequencies

            case None => tokenFrequencies
          }

          context.become(
            running(
              newChatMessagesAndTokens, newTokensBySender, newTokenFrequencies, listeners
            )
          )
      }

    case Reset =>
      for (listener: ActorRef <- listeners) {
        listener ! Counts(IndexedSeq(), emptyTokensBySender, Frequencies())
      }
      context.become(
        running(
          IndexedSeq(), emptyTokensBySender, Frequencies(), listeners
        )
      )

    case Register(listener: ActorRef) =>
      listener ! Counts(chatMessagesAndTokens, tokensBySender, tokenFrequencies)
      context.watch(listener)
      context.become(
        running(
          chatMessagesAndTokens, tokensBySender, tokenFrequencies,
          listeners + listener
        )
      )
      log.info(s"+1 ${self.path.name} listener (=${listeners.size + 1})")

    case Terminated(listener: ActorRef) if listeners.contains(listener) =>
      val remainingListeners: Set[ActorRef] = listeners - listener
      if (remainingListeners.nonEmpty) {
        context.become(
          running(
            chatMessagesAndTokens, tokensBySender, tokenFrequencies,
            remainingListeners
          )
        )
      } else {
        chatMessageActor ! ChatMessageActor.Unregister(self)
        context.become(
          paused(chatMessagesAndTokens, tokensBySender, tokenFrequencies)
        )
      }
      log.info(s"-1 ${self.path.name} listener (=${listeners.size - 1})")
  }

  override def receive: Receive = paused(
    IndexedSeq(), emptyTokensBySender, Frequencies()
  )
}
