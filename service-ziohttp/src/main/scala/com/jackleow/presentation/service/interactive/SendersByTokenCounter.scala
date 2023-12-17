package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.collection.{FifoBoundedSet, MultiSet}
import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.{ChatMessage, Counts}
import com.jackleow.presentation.tokenizing.Tokenizer
import zio.*
import zio.stream.*

object SendersByTokenCounter:
  def make[N <: String](
    extractTokens: Tokenizer, maxTokensPerSender: Int
  )(using name: ValueOf[N], tag: Tag[N]): URIO[
    SubscriberCountingHub[ChatMessage, "chat"] & SubscriberCountingHub[ChatMessage, "rejected"],
    SendersByTokenCounter[N]
  ] =
    val emptyTokensBySender: Map[String, FifoBoundedSet[String]] =
      Map().withDefaultValue(FifoBoundedSet(maxTokensPerSender))
    for
      incomingEventHub <- ZIO.service[SubscriberCountingHub[ChatMessage, "chat"]]
      rejectedMessagesHub <- ZIO.service[SubscriberCountingHub[ChatMessage, "rejected"]]
      countsRef <- SubscriptionRef.make((emptyTokensBySender, MultiSet[String]()))
      subscribers <- SubscriptionRef.make(0)
    yield SendersByTokenCounterLive[N](
      valueOf[N], extractTokens, emptyTokensBySender,
      incomingEventHub.elements, rejectedMessagesHub,
      countsRef, subscribers
    )

trait SendersByTokenCounter[N <: String]:
  /**
   * Stream of Counts.
   */
  def counts: UIO[UStream[Counts]]

  /**
   * Reset Counts
   */
  def reset(): UIO[Unit]
