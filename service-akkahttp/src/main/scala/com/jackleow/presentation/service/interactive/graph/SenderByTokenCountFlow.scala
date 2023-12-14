package com.jackleow.presentation.service.interactive.graph

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.jackleow.akka.stream.scaladsl.*
import com.jackleow.presentation.collection.{FifoBoundedSet, MultiSet}
import com.jackleow.presentation.service.interactive.model.*
import com.jackleow.presentation.tokenizing.Tokenizer
import com.typesafe.scalalogging.StrictLogging

private[graph] object SenderByTokenCountFlow extends StrictLogging:
  def apply(
    extractTokens: Tokenizer, maxTokensPerSender: Int,
    hasActiveSubscriptionsSource: Source[Boolean, Counter],
    rejectedMessagesSink: Sink[ChatMessage, NotUsed]
  ): Flow[ChatMessage | Reset.type, Counts, Counter] =
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
