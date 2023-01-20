package actors

import actors.counter.{FifoFixedSizedSet, MultiSet}
import actors.tokenizing.Tokenizer
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import model.ChatMessage
import play.api.libs.json.*
import play.api.libs.functional.syntax.*

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
    override def toString: String = s"$chatMessage -> ${tokens.mkString("[", ",", "]")}"
  }
  case class Counts(
    chatMessagesAndTokens: IndexedSeq[ChatMessageAndTokens],
    tokensBySender: Map[String, FifoFixedSizedSet[String]],
    tokens: MultiSet[String]
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
      "tokensAndCounts" -> counts.tokens.elementsByCount.toSeq // JSON keys must be strings
    )

  def props(
    extractToken: String => Seq[String],
    chatMessageActor: ActorRef, rejectedMessageActor: ActorRef
  ): Props =
    Props(
      new SendersByTokenCounterActor(
        extractToken, chatMessageActor, rejectedMessageActor
      )
    )

  // WebSocket actor
  object WebSocketActor {
    object Send

    private val BatchPeriod: FiniteDuration = 250.milliseconds

    def props(webSocketClient: ActorRef, counts: ActorRef): Props =
      Props(new WebSocketActor(webSocketClient, counts))
  }

  class WebSocketActor(
    webSocketClient: ActorRef, counts: ActorRef
  ) extends Actor with ActorLogging {
    import WebSocketActor.*
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
  tokenize: Tokenizer,
  chatMessageActor: ActorRef, rejectedMessageActor: ActorRef
) extends Actor with ActorLogging {
  import SendersByTokenCounterActor.*

  private val maxTokensPerSender: Int = 3
  private val emptyTokensBySender: Map[String, FifoFixedSizedSet[String]] =
    Map().withDefaultValue(FifoFixedSizedSet(maxTokensPerSender))

  private def paused(
    chatMessagesAndTokens: IndexedSeq[ChatMessageAndTokens],
    tokensBySender: Map[String, FifoFixedSizedSet[String]], tokens: MultiSet[String]
  ): Receive = {
    case Reset =>
      context.become(
        paused(IndexedSeq(), emptyTokensBySender, MultiSet[String]())
      )

    case Register(listener: ActorRef) =>
      chatMessageActor ! ChatMessageActor.Register(self)
      listener ! Counts(
        chatMessagesAndTokens, tokensBySender, tokens
      )
      context.watch(listener)
      context.become(
        running(
          chatMessagesAndTokens, tokensBySender, tokens, Set(listener)
        )
      )
      log.info(
        "+1 senders by token count listener (=1)"
      )
  }

  private def running(
    chatMessagesAndTokens: IndexedSeq[ChatMessageAndTokens],
    tokensBySender: Map[String, FifoFixedSizedSet[String]], tokens: MultiSet[String],
    listeners: Set[ActorRef]
  ): Receive = {
    case event@ChatMessageActor.New(msg: ChatMessage) =>
      val senderOpt: Option[String] = Option(msg.sender).filter {
        _ != ""
      }
      val extractedTokens: Seq[String] = tokenize(msg.text)

      val (
        newChatMessagesAndTokens: IndexedSeq[ChatMessageAndTokens],
        newTokensBySender: Map[String, FifoFixedSizedSet[String]],
        newTokens: MultiSet[String]
      ) =
        extractedTokens match {
          case Nil =>
            log.info("No token extracted")
            rejectedMessageActor ! event
            (
              chatMessagesAndTokens :+ ChatMessageAndTokens(msg, Nil),
              tokensBySender, tokens
            )

          case extractedTokens =>
            log.info(s"Extracted tokens ${extractedTokens.mkString("\"", "\", \"", "\"")}")
            val newChatMessagesAndTokens: IndexedSeq[ChatMessageAndTokens] =
              chatMessagesAndTokens :+ ChatMessageAndTokens(msg, extractedTokens)
            val (
              newTokensBySender: Map[String, FifoFixedSizedSet[String]],
              addedTokens: Set[String],
              removedTokens: Set[String]
            ) = senderOpt match {
              case Some(sender: String) =>
                val (tokens: FifoFixedSizedSet[String], updates: Seq[FifoFixedSizedSet.Effect[String]]) =
                  tokensBySender(sender).addAll(extractedTokens)
                val addedTokens: Set[String] = extractedTokens.zip(updates).
                  collect {
                    case (token: String, FifoFixedSizedSet.Added()) => token
                    case (token: String, FifoFixedSizedSet.AddedEvicting(_)) => token
                  }.
                  toSet
                val removedTokens: Set[String] = updates.
                  collect {
                    case FifoFixedSizedSet.AddedEvicting(token: String) => token
                  }.
                  toSet
                (
                  tokensBySender.updated(sender, tokens),
                  addedTokens -- removedTokens,
                  removedTokens -- addedTokens
                )

              case None => (tokensBySender, extractedTokens.toSet, Set.empty)
            }
            val newTokens: MultiSet[String] = addedTokens.
              foldLeft(
                removedTokens.foldLeft(tokens) { (accum: MultiSet[String], oldToken: String) =>
                  accum.decremented(oldToken)
                }
              ) { (accum: MultiSet[String], newToken: String) =>
                accum.incremented(newToken)
              }

            (newChatMessagesAndTokens, newTokensBySender, newTokens)
        }

      for (listener: ActorRef <- listeners) {
        listener ! Counts(
          newChatMessagesAndTokens, newTokensBySender, newTokens
        )
      }
      context.become(
        running(
          newChatMessagesAndTokens, newTokensBySender, newTokens, listeners
        )
      )

    case Reset =>
      for (listener: ActorRef <- listeners) {
        listener ! Counts(IndexedSeq(), emptyTokensBySender, MultiSet[String]())
      }
      context.become(
        running(
          IndexedSeq(), emptyTokensBySender, MultiSet[String](), listeners
        )
      )

    case Register(listener: ActorRef) =>
      listener ! Counts(chatMessagesAndTokens, tokensBySender, tokens)
      context.watch(listener)
      context.become(
        running(
          chatMessagesAndTokens, tokensBySender, tokens,
          listeners + listener
        )
      )
      log.info(s"+1 ${self.path.name} listener (=${listeners.size + 1})")

    case Terminated(listener: ActorRef) if listeners.contains(listener) =>
      val remainingListeners: Set[ActorRef] = listeners - listener
      if (remainingListeners.nonEmpty) {
        context.become(
          running(
            chatMessagesAndTokens, tokensBySender, tokens,
            remainingListeners
          )
        )
      } else {
        chatMessageActor ! ChatMessageActor.Unregister(self)
        context.become(
          paused(chatMessagesAndTokens, tokensBySender, tokens)
        )
      }
      log.info(s"-1 ${self.path.name} listener (=${listeners.size - 1})")
  }

  override def receive: Receive = paused(
    IndexedSeq(), emptyTokensBySender, MultiSet[String]()
  )
}
