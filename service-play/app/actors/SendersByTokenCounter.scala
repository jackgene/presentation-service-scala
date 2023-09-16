package actors

import actors.ChatMessageBroadcaster.ChatMessage
import actors.common.{JsonWriter, RateLimiter}
import actors.counter.{FifoBoundedSet, MultiSet}
import actors.tokenizing.Tokenizer
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import play.api.libs.json.{JsValue, Json, Writes}

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
  final case class Counts(tokens: MultiSet[String]) extends Event

  // JSON
  private implicit val eventWrites: Writes[Event] = {
    case counts: Counts =>
      Json.obj(
        // JSON keys must be strings
        "tokensAndCounts" -> counts.tokens.elementsByCount.toSeq
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
    tokensBySender: Map[String, FifoBoundedSet[String]], tokenCounts: MultiSet[String]
  ): Behavior[Command] = Behaviors.receive { (ctx: ActorContext[Command], cmd: Command) =>
    cmd match {
      case Reset =>
        paused(emptyTokensBySender, MultiSet[String]())

      case Subscribe(subscriber: ActorRef[Event]) =>
        ctx.log.info(s"+1 ${ctx.self.path.name} subscriber (=1)")
        subscriber ! Counts(tokenCounts)
        ctx.watchWith(subscriber, Unsubscribe(subscriber))
        chatMessageBroadcaster ! ChatMessageBroadcaster.Subscribe(adapter)
        running(tokensBySender, tokenCounts, Set(subscriber))

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
    tokensBySender: Map[String, FifoBoundedSet[String]], tokenCounts: MultiSet[String],
    subscribers: Set[ActorRef[Event]]
  ): Behavior[Command] = Behaviors.receive { (ctx: ActorContext[Command], cmd: Command) =>
    cmd match {
      case Record(chatMessage: ChatMessage) =>
        val extractedTokens: Seq[String] = extractTokens(chatMessage.text)

        val (
          newTokensBySender: Map[String, FifoBoundedSet[String]],
          newTokens: MultiSet[String]
        ) =
          extractedTokens match {
            case Nil =>
              ctx.log.info("No token extracted")
              rejectedMessageBroadcaster ! ChatMessageBroadcaster.Record(chatMessage)
              (tokensBySender, tokenCounts)

            case extractedTokens =>
              ctx.log.info(s"Extracted tokens ${extractedTokens.mkString("\"", "\", \"", "\"")}")
              val senderOpt: Option[String] = Option(chatMessage.sender).filter(_ != "")
              val prioritizedTokens: Seq[String] = extractedTokens.reverse
              val (
                newTokensBySender: Map[String, FifoBoundedSet[String]],
                addedTokens: Set[String],
                removedTokens: Set[String]
              ) = senderOpt match {
                case Some(sender: String) =>
                  val (tokens: FifoBoundedSet[String], updates: Seq[FifoBoundedSet.Effect[String]]) =
                    tokensBySender(sender).addAll(prioritizedTokens)
                  val addedTokens: Set[String] = updates.reverse.
                    map {
                      case FifoBoundedSet.Added(token: String) => token
                      case FifoBoundedSet.AddedEvicting(token: String, _) => token
                    }.
                    toSet
                  val removedTokens: Set[String] = updates.
                    collect {
                      case FifoBoundedSet.AddedEvicting(_, token: String) => token
                    }.
                    toSet

                  (tokensBySender.updated(sender, tokens), addedTokens, removedTokens)

                case None => (tokensBySender, prioritizedTokens.toSet, Set.empty)
              }
              val newTokenCounts: MultiSet[String] = addedTokens.
                foldLeft(
                  removedTokens.foldLeft(tokenCounts) { (accum: MultiSet[String], oldToken: String) =>
                    accum - oldToken
                  }
                ) { (accum: MultiSet[String], newToken: String) =>
                  accum + newToken
                }

              (newTokensBySender, newTokenCounts)
          }

        for (subscriber: ActorRef[Event] <- subscribers) {
          subscriber ! Counts(newTokens)
        }
        running(newTokensBySender, newTokens, subscribers)

      case Reset =>
        for (subscriber: ActorRef[Event] <- subscribers) {
          subscriber ! Counts(MultiSet[String]())
        }
        running(emptyTokensBySender, MultiSet[String](), subscribers)

      case Subscribe(subscriber: ActorRef[Event]) if !subscribers.contains(subscriber) =>
        ctx.log.info(s"+1 ${ctx.self.path.name} subscriber (=${subscribers.size + 1})")
        subscriber ! Counts(tokenCounts)
        ctx.watchWith(subscriber, Unsubscribe(subscriber))
        running(tokensBySender, tokenCounts, subscribers + subscriber)

      case Subscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"attempted to subscribe duplicate ${ctx.self.path.name} subscriber - ${subscriber.path}")
        Behaviors.unhandled

      case Unsubscribe(subscriber: ActorRef[Event]) if subscribers.contains(subscriber) =>
        ctx.log.info(s"-1 ${ctx.self.path.name} subscriber (=${subscribers.size - 1})")
        ctx.unwatch(subscriber)
        val remainingSubscribers: Set[ActorRef[Event]] = subscribers - subscriber
        if (remainingSubscribers.nonEmpty) {
          running(tokensBySender, tokenCounts, remainingSubscribers)
        } else {
          chatMessageBroadcaster ! ChatMessageBroadcaster.Unsubscribe(adapter)
          paused(tokensBySender, tokenCounts)
        }

      case Unsubscribe(subscriber: ActorRef[Event]) =>
        ctx.log.warn(s"attempted to unsubscribe unknown ${ctx.self.path.name} subscriber - ${subscriber.path}")
        Behaviors.unhandled
    }
  }

  def initial(): Behavior[Command] = paused(emptyTokensBySender, MultiSet[String]())
}
