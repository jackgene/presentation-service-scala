package com.jackleow.presentation.service.interactive

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.*
import akka.stream.{BoundedSourceQueue, Materializer}
import com.jackleow.akka.stream.scaladsl.*
import com.jackleow.presentation.collection.{FifoBoundedSet, MultiSet}
import com.jackleow.presentation.config.Configuration
import com.jackleow.presentation.service.SubscriptionSupport
import com.jackleow.presentation.tokenizing.{MappedKeywordsTokenizer, NormalizedWordsTokenizer, Tokenizer}
import com.typesafe.scalalogging.StrictLogging

//import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
import scala.util.chaining.*

object AkkaStreamInteractiveService:
  extension[Out, Mat] (source: Source[Out, Mat])
    private def prependingLastEmitted()(implicit materializer: Materializer): Source[Out, Mat] =
      source
        .conflate:
          (_, next: Out) => next
        .extrapolate:
          Iterator.continually(_)
        .runWith(BroadcastHub.sink(1))
        .drop(2) // Defeats Akka buffering
        .take(1)
        .pipe(source.prependLazy)

//    private def prependingLastEmitted()(implicit materializer: Materializer): Source[Out, Mat] =
//      val lastEmitted: AtomicReference[Out] = new AtomicReference[Out]()
//      source.runForeach(lastEmitted.set)
//
//      Source
//        .lazySingle:
//          () => Option(lastEmitted.get)
//        .collect:
//          case Some(value: Out) => value
//        .pipe(source.prepend)

  private case object Reset

class AkkaStreamInteractiveService(
  languagePollConfig: Configuration.LanguagePoll,
  wordCloudConfig: Configuration.WordCloud
)(implicit val system: ActorSystem[Nothing])
  extends InteractiveService with SubscriptionSupport with StrictLogging:

  import AkkaStreamInteractiveService.*
  import InteractiveService.*

  private implicit val ec: ExecutionContext = system.executionContext
  private val extractLanguagePollTokens: Tokenizer = MappedKeywordsTokenizer(
    languagePollConfig.languageByKeyword
  )
  private val maxLanguagePollVotesPerPerson: Int =
    languagePollConfig.maxVotesPerPerson
  private val extractWordCloudTokens: Tokenizer = NormalizedWordsTokenizer(
    wordCloudConfig.stopWords,
    wordCloudConfig.minWordLength,
    wordCloudConfig.maxWordLength
  )
  private val maxWordCloudWordsPerPerson: Int =
    wordCloudConfig.maxWordsPerPerson

  // Source
  private val inputQueueSource: Source[ChatMessage | Reset.type, BoundedSourceQueue[ChatMessage | Reset.type]] =
    Source
      .queue[ChatMessage | Reset.type](64)
      .wireTap:
        case chatMessage: ChatMessage =>
          logger.info(s"Received chat message $chatMessage")
        case _ =>

  // Sinks
  private def sendersByTokenCounter(
    extractTokens: Tokenizer, maxTokensPerSender: Int,
    hasActiveSubscriptionsSource: Source[Boolean, Counter],
    rejectedMessagesSink: Sink[ChatMessage, NotUsed]
  ): Sink[ChatMessage | Reset.type, (Counter, Source[Counts, NotUsed])] =
    val emptyTokensBySender: Map[String, FifoBoundedSet[String]] =
      Map().withDefaultValue(FifoBoundedSet(maxTokensPerSender))

    Flow[ChatMessage | Reset.type]
      .viaMat(
        Flow.activeFilter[ChatMessage | Reset.type, Counter](
          hasActiveSubscriptionsSource,
          _ != Reset
        )
      )(Keep.right)
      .map[(ChatMessage, Seq[String]) | Reset.type]:
        case chatMessage: ChatMessage =>
          chatMessage -> extractTokens(chatMessage.text)
        case Reset => Reset
      .divertTo(
        Flow[(ChatMessage, Seq[String]) | Reset.type]
          .collect:
            case (chatMessage: ChatMessage, _) => chatMessage
          .to(rejectedMessagesSink),
        {
          case (_, tokens: Seq[String]) => tokens.isEmpty
          case Reset => false
        }
      )
      .map[(Option[String], Seq[String]) | Reset.type]:
        case (chatMessage: ChatMessage, tokens: Seq[String]) =>
          Option(chatMessage.sender).filter(_ != "") -> tokens
        case Reset => Reset
      .scan(
        (emptyTokensBySender, MultiSet[String]())
      ):
        case (
          (tokensBySender: Map[String, FifoBoundedSet[String]], tokenCounts: MultiSet[String]),
          (senderOpt: Option[String], extractedTokens: Seq[String])
        ) =>
          logger.info(s"Extracted tokens ${extractedTokens.mkString("\"", "\", \"", "\"")}")
          val prioritizedTokens: Seq[String] = extractedTokens.reverse
          val (
            newTokensBySender: Map[String, FifoBoundedSet[String]],
            addedTokens: Set[String],
            removedTokens: Set[String]
          ) = senderOpt match
            case Some(sender: String) =>
              val (tokens: FifoBoundedSet[String], updates: Seq[FifoBoundedSet.Effect[String]]) =
                tokensBySender(sender).addAll(prioritizedTokens)
              val addedTokens: Set[String] = updates.reverse
                .map:
                  case FifoBoundedSet.Added(token: String) => token
                  case FifoBoundedSet.AddedEvicting(token: String, _) => token
                .toSet
              val removedTokens: Set[String] = updates
                .collect:
                  case FifoBoundedSet.AddedEvicting(_, token: String) => token
                .toSet

              (tokensBySender.updated(sender, tokens), addedTokens, removedTokens)

            case None => (tokensBySender, prioritizedTokens.toSet, Set[String]())
          val newTokenCounts: MultiSet[String] = addedTokens
            .foldLeft(
              removedTokens.foldLeft(tokenCounts):
                (accum: MultiSet[String], oldToken: String) =>
                  accum - oldToken
            ):
              (accum: MultiSet[String], newToken: String) =>
                accum + newToken

          (newTokensBySender, newTokenCounts)

        case (_, Reset) =>
          (emptyTokensBySender, MultiSet[String]())
      .map:
        case (_, tokenCounts: MultiSet[String]) => Counts(tokenCounts)
      .toMat(BroadcastHub.sink(1))(Keep.both)

  private val (
    rejectedMessagesSink: Sink[ChatMessage, NotUsed],
    rejectedMessagesSubscriptionCounter: Counter,
    rejectedMessagesSource: Source[ChatMessage, NotUsed]
  ) =
    MergeHub.source[ChatMessage]
      .wireTap:
        (chatMessage: ChatMessage) =>
          logger.info(s"Received rejected message $chatMessage")
      .viaMat(Flow.activeFilter(hasActiveSubscriptionsSource("rejected")))(Keep.both)
      .toMat(BroadcastHub.sink[ChatMessage](1))(Keep.both)
      .run()
      .pipe:
        case ((snk, subCount), src) => (snk, subCount, src)
  private val languagePollSink: Sink[ChatMessage | Reset.type, (Counter, Source[Counts, NotUsed])] =
    sendersByTokenCounter(
      extractLanguagePollTokens, maxLanguagePollVotesPerPerson,
      hasActiveSubscriptionsSource("language-poll"),
      rejectedMessagesSink
    )
  private val wordCloudSink: Sink[ChatMessage | Reset.type, (Counter, Source[Counts, NotUsed])] =
    sendersByTokenCounter(
      extractWordCloudTokens, maxWordCloudWordsPerPerson,
      hasActiveSubscriptionsSource("word-cloud"),
      rejectedMessagesSink
    )
  private val questionsSink: Sink[ChatMessage | Reset.type, (Counter, Source[ChatMessages, NotUsed])] =
    Flow[ChatMessage | Reset.type]
      .viaMat(
        Flow.activeFilter[ChatMessage | Reset.type, Counter](
          hasActiveSubscriptionsSource("question"),
          _ != Reset
        )
      )(Keep.right)
      .divertTo(
        Flow[ChatMessage | Reset.type]
          .collect:
            case chatMessage: ChatMessage => chatMessage
          .to(rejectedMessagesSink),
        {
          case ChatMessage(sender: String, _, _) => sender != ""
          case Reset => false
        }
      )
      .scan[List[String]](Nil):
        case (texts: Seq[String], ChatMessage(_, _, text: String)) =>
          texts :+ text
        case (_, Reset) => Nil
      .map:
        ChatMessages(_)
      .toMat(BroadcastHub.sink(1))(Keep.both)

  // Fully connected graph
  private val (
    inputQueue: BoundedSourceQueue[ChatMessage | Reset.type],
    languagePollSubscriptionCounter: Counter,
    languagePollSource: Source[Counts, NotUsed],
    wordCloudSubscriptionCounter: Counter,
    wordCloudSource: Source[Counts, NotUsed],
    questionsSubscriptionCounter: Counter,
    questionsSource: Source[ChatMessages, NotUsed]
  ) =
    inputQueueSource
      .alsoToMat(languagePollSink)(Keep.both)
      .alsoToMat(wordCloudSink)(Keep.both)
      .alsoToMat(questionsSink)(Keep.both)
      .to(Sink.ignore)
      .mapMaterializedValue:
        // Flatten
        case (((x, (a, b)), (d, e)), (y, z)) => (x, a, b, d, e, y, z)
      .run()

  override def receiveChatMessage(chatMessage: ChatMessage): Future[Unit] =
    Future
      .successful:
        inputQueue.offer(chatMessage)
      .filter:
        _.isEnqueued
      .map:
        _ => ()

  override def reset(): Future[Unit] =
    Future
      .successful:
        inputQueue.offer(Reset)
      .filter:
        _.isEnqueued
      .map:
        _ => ()

  override val languagePoll: Flow[Any, Counts, NotUsed] =
    subscriptionTrackingFlow(
      languagePollSource.prependingLastEmitted(),
      languagePollSubscriptionCounter
    )

  override val wordCloud: Flow[Any, Counts, NotUsed] =
    subscriptionTrackingFlow(
      wordCloudSource.prependingLastEmitted(),
      wordCloudSubscriptionCounter
    )

  override val questions: Flow[Any, ChatMessages, NotUsed] =
    subscriptionTrackingFlow(
      questionsSource.prependingLastEmitted(),
      questionsSubscriptionCounter
    )

  override def rejectedMessages: Flow[Any, ChatMessage, NotUsed] =
    subscriptionTrackingFlow(
      rejectedMessagesSource,
      rejectedMessagesSubscriptionCounter
    )
