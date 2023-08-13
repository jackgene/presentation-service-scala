package actors

import actors.common.{JsonWriter, RateLimiter}
import actors.counter.{FifoBoundedSet, MultiSet}
import actors.tokenizing.Tokenizer
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import model.ChatMessage
import play.api.libs.functional.syntax.*
import play.api.libs.json.{JsPath, JsValue, Json, Writes}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/**
 * Count senders grouping by (filtered and transformed) message text.
 */
object SendersByTokenCounter {
  sealed trait Command
  final case class Subscribe(subscriber: ActorRef[Event]) extends Command
  final case class Unsubscribe(subscriber: ActorRef[Event]) extends Command
  final case class Record(chatMessage: ChatMessage) extends Command
  final case object Reset extends Command

  sealed trait Event
  final case class ChatMessageAndTokens(
    chatMessage: ChatMessage,
    tokens: Seq[String]
  ) {
    override def toString: String = s"$chatMessage -> ${tokens.mkString("[", ",", "]")}"
  }
  object Counts {
    def apply(
      chatMessagesAndTokens: IndexedSeq[ChatMessageAndTokens],
      tokensBySender: Map[String, FifoBoundedSet[String]],
      tokens: MultiSet[String]
    ): Counts = new Counts(
      chatMessagesAndTokens,
      tokensBySender.view.mapValues(_.toSeq).toMap,
      tokens.elementsByCount.toSeq // JSON keys must be strings
    )
  }
  final case class Counts(
    chatMessagesAndTokens: IndexedSeq[ChatMessageAndTokens],
    tokensBySender: Map[String, Seq[String]],
    tokensAndCounts: Seq[(Int, Seq[String])]
  ) extends Event

  // JSON
  private implicit val chatMessageAndTokensWrites: Writes[ChatMessageAndTokens] =
    (
      (JsPath \ "chatMessage").write[ChatMessage] and
      (JsPath \ "tokens").write[Seq[String]]
    )(unlift(ChatMessageAndTokens.unapply))
  private implicit val eventWrites: Writes[Event] = {
    case counts: Counts =>
      Json.obj(
        "chatMessagesAndTokens" -> counts.chatMessagesAndTokens,
        "tokensBySender" -> counts.tokensBySender,
        "tokensAndCounts" -> counts.tokensAndCounts
      )
  }

  def apply(
    extractTokens: Tokenizer, tokensPerSender: Int,
    chatMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command],
    rejectedMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command]
  ): Behavior[Command] = Behaviors.setup { ctx =>
    new SendersByTokenCounter(
      extractTokens, tokensPerSender,
      chatMessageBroadcaster, rejectedMessageBroadcaster,
      ctx.messageAdapter[ChatMessageBroadcaster.Event] {
        case ChatMessageBroadcaster.New(chatMessage: ChatMessage) => Record(chatMessage)
      }
    ).initial()
  }

  /**
   * Publishes broadcast as JSON - Rate Limited
   */
  object JsonPublisher {
    private val MinPeriodBetweenMessages: FiniteDuration = 100.milliseconds

    def apply(
      subscriber: ActorRef[JsValue], counter: ActorRef[Command]
    ): Behavior[Event] = Behaviors.setup { ctx: ActorContext[Event] =>
      ctx.watch(counter)
      val rateLimitedCounter = ctx.spawn(
        RateLimiter(ctx.self, MinPeriodBetweenMessages), "rate-limiter"
      )
      counter ! Subscribe(rateLimitedCounter)

      JsonWriter(subscriber)
    }
  }
}
private class SendersByTokenCounter(
  extractTokens: Tokenizer, tokensPerSender: Int,
  chatMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command],
  rejectedMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command],
  adapter: ActorRef[ChatMessageBroadcaster.Event]
) {
  import SendersByTokenCounter.*

  private val emptyTokensBySender: Map[String, FifoBoundedSet[String]] =
    Map().withDefaultValue(FifoBoundedSet(tokensPerSender))

  private def paused(
    chatMessagesAndTokens: IndexedSeq[ChatMessageAndTokens],
    tokensBySender: Map[String, FifoBoundedSet[String]], tokens: MultiSet[String]
  ): Behavior[Command] = Behaviors.receive { (ctx: ActorContext[Command], cmd: Command) =>
    cmd match {
      case Reset =>
        paused(IndexedSeq(), emptyTokensBySender, MultiSet[String]())

      case Subscribe(subscriber: ActorRef[Event]) =>
        ctx.log.info(s"+1 ${ctx.self.path.name} subscriber (=1)")
        subscriber ! Counts(chatMessagesAndTokens, tokensBySender, tokens)
        ctx.watchWith(subscriber, Unsubscribe(subscriber))
        chatMessageBroadcaster ! ChatMessageBroadcaster.Subscribe(adapter)
        running(chatMessagesAndTokens, tokensBySender, tokens, Set(subscriber))

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
    chatMessagesAndTokens: IndexedSeq[ChatMessageAndTokens],
    tokensBySender: Map[String, FifoBoundedSet[String]], tokens: MultiSet[String],
    subscribers: Set[ActorRef[Event]]
  ): Behavior[Command] = Behaviors.receive { (ctx: ActorContext[Command], cmd: Command) =>
    cmd match {
      case Record(chatMessage: ChatMessage) =>
        val senderOpt: Option[String] = Option(chatMessage.sender).filter(_ != "")
        val extractedTokens: Seq[String] = extractTokens(chatMessage.text)

        val (
          newChatMessagesAndTokens: IndexedSeq[ChatMessageAndTokens],
          newTokensBySender: Map[String, FifoBoundedSet[String]],
          newTokens: MultiSet[String]
        ) =
          extractedTokens match {
            case Nil =>
              ctx.log.info("No token extracted")
              rejectedMessageBroadcaster ! ChatMessageBroadcaster.Record(chatMessage)
              (
                chatMessagesAndTokens :+ ChatMessageAndTokens(chatMessage, Nil),
                tokensBySender, tokens
              )

            case extractedTokens =>
              ctx.log.info(s"Extracted tokens ${extractedTokens.mkString("\"", "\", \"", "\"")}")
              val newChatMessagesAndTokens: IndexedSeq[ChatMessageAndTokens] =
                chatMessagesAndTokens :+ ChatMessageAndTokens(chatMessage, extractedTokens)
              val (
                newTokensBySender: Map[String, FifoBoundedSet[String]],
                addedTokens: Set[String],
                removedTokens: Set[String]
              ) = senderOpt match {
                case Some(sender: String) =>
                  val (tokens: FifoBoundedSet[String], updates: Seq[FifoBoundedSet.Effect[String]]) =
                    tokensBySender(sender).addAll(extractedTokens)
                  val addedTokens: Set[String] = extractedTokens.zip(updates).
                    collect {
                      case (token: String, FifoBoundedSet.Added()) => token
                      case (token: String, FifoBoundedSet.AddedEvicting(_)) => token
                    }.
                    toSet
                  val removedTokens: Set[String] = updates.
                    collect {
                      case FifoBoundedSet.AddedEvicting(token: String) => token
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
                    accum - oldToken
                  }
                ) { (accum: MultiSet[String], newToken: String) =>
                  accum + newToken
                }

              (newChatMessagesAndTokens, newTokensBySender, newTokens)
          }

        for (subscriber: ActorRef[Event] <- subscribers) {
          subscriber ! Counts(newChatMessagesAndTokens, newTokensBySender, newTokens)
        }
        running(newChatMessagesAndTokens, newTokensBySender, newTokens, subscribers)

      case Reset =>
        for (subscriber: ActorRef[Event] <- subscribers) {
          subscriber ! Counts(IndexedSeq(), emptyTokensBySender, MultiSet[String]())
        }
        running(IndexedSeq(), emptyTokensBySender, MultiSet[String](), subscribers)

      case Subscribe(subscriber: ActorRef[Event]) if !subscribers.contains(subscriber) =>
        ctx.log.info(s"+1 ${ctx.self.path.name} subscriber (=${subscribers.size + 1})")
        subscriber ! Counts(chatMessagesAndTokens, tokensBySender, tokens)
        ctx.watchWith(subscriber, Unsubscribe(subscriber))
        running(chatMessagesAndTokens, tokensBySender, tokens, subscribers + subscriber)

      case Subscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"attempted to subscribe duplicate ${ctx.self.path.name} subscriber - ${subscriber.path}")
        Behaviors.unhandled

      case Unsubscribe(subscriber: ActorRef[Event]) if subscribers.contains(subscriber) =>
        ctx.log.info(s"-1 ${ctx.self.path.name} subscriber (=${subscribers.size - 1})")
        ctx.unwatch(subscriber)
        val remainingSubscribers: Set[ActorRef[Event]] = subscribers - subscriber
        if (remainingSubscribers.nonEmpty) {
          running(chatMessagesAndTokens, tokensBySender, tokens, remainingSubscribers)
        } else {
          chatMessageBroadcaster ! ChatMessageBroadcaster.Unsubscribe(adapter)
          paused(chatMessagesAndTokens, tokensBySender, tokens)
        }

      case Unsubscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"attempted to unsubscribe unknown ${ctx.self.path.name} subscriber - ${subscriber.path}")
        Behaviors.unhandled
    }
  }

  def initial(): Behavior[Command] = paused(
    IndexedSeq(), emptyTokensBySender, MultiSet[String]()
  )
}
