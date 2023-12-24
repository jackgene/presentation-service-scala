package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.collection.{FifoBoundedSet, MultiSet}
import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.{ChatMessage, Counts}
import com.jackleow.presentation.tokenizing.Tokenizer
import com.jackleow.zio.Named
import com.jackleow.zio.stream.*
import zio.*
import zio.stream.*

private final class SendersByTokenCounterLive(
  name: String, 
  extractTokens: Tokenizer,
  emptyTokensBySender: Map[String, FifoBoundedSet[String]],
  chatMessages: UStream[ChatMessage],
  rejectedMessagesBroadcaster: SubscriberCountingHub[ChatMessage] Named "rejected",
  countsRef: SubscriptionRef[(Map[String, FifoBoundedSet[String]], MultiSet[String])],
  subscribersRef: SubscriptionRef[Int]
) extends SendersByTokenCounter:
  override val counts: UIO[UStream[Counts]] =
    for
      subscribers: Int <- subscribersRef.get
      _ <-
        if subscribers == 0 then
          chatMessages
            .takeWhileActive(subscribersRef.changes.drop(1).map(_ > 0))
            .runForeach: (chatMessage: ChatMessage) =>
              extractTokens(chatMessage.text) match
                case Nil =>
                  for
                    _ <- ZIO.log("No token extracted")
                    _ <- rejectedMessagesBroadcaster.get.publish(chatMessage)
                  yield ()

                case extractedTokens: Seq[String] =>
                  for
                    _ <- ZIO.log(s"Extracted tokens ${extractedTokens.mkString("\"", "\", \"", "\"")}")
                    _ <- countsRef.update:
                      case (tokensBySender: Map[String, FifoBoundedSet[String]], tokenCounts: MultiSet[String]) =>
                        val senderOpt: Option[String] = Option(chatMessage.sender).filter(_ != "")
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
                        val newTokenCounts: MultiSet[String] = addedTokens.
                          foldLeft(
                            removedTokens.foldLeft(tokenCounts): (accum: MultiSet[String], oldToken: String) =>
                              accum - oldToken
                          ): (accum: MultiSet[String], newToken: String) =>
                            accum + newToken
      
                        (newTokensBySender, newTokenCounts)
                  yield ()
            .forkDaemon
        else ZIO.unit
    yield
      countsRef
        .changes
        .countRunning(
          subscribersRef,
          (subscribers: Int) => ZIO.log(s"+1 $name subscriber (=$subscribers)"),
          (subscribers: Int) => ZIO.log(s"-1 $name subscriber (=$subscribers)"),
        )
        .map:
          case (_, countsByToken: MultiSet[String]) =>
            Counts(countsByToken)

  override def reset(): UIO[Unit] =
    countsRef.set((emptyTokensBySender, MultiSet[String]()))
