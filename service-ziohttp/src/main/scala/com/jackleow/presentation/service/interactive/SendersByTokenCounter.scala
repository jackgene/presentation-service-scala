package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.collection.{FifoBoundedSet, MultiSet}
import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.{ChatMessage, Counts}
import com.jackleow.presentation.tokenizing.Tokenizer
import com.jackleow.zio.Named
import zio.*
import zio.stream.*

object SendersByTokenCounter:
  def make(
    name: String, extractTokens: Tokenizer, maxTokensPerSender: Int
  ): URIO[
    (SubscriberCountingHub[ChatMessage] Named "chat") & (SubscriberCountingHub[ChatMessage] Named "rejected"),
    SendersByTokenCounter
  ] =
    val emptyTokensBySender: Map[String, FifoBoundedSet[String]] =
      Map().withDefaultValue(FifoBoundedSet(maxTokensPerSender))
    for
      chatMessagesHub <- ZIO.service[SubscriberCountingHub[ChatMessage] Named "chat"]
      rejectedMessagesHub <- ZIO.service[SubscriberCountingHub[ChatMessage] Named "rejected"]
      countsRef <- SubscriptionRef.make((emptyTokensBySender, MultiSet[String]()))
      subscribers <- SubscriptionRef.make(0)
    yield SendersByTokenCounterLive(
      name, extractTokens, emptyTokensBySender,
      chatMessagesHub.get.elements, rejectedMessagesHub,
      countsRef, subscribers
    )

trait SendersByTokenCounter:
  /**
   * Stream of Counts.
   */
  def counts: UIO[UStream[Counts]]

  /**
   * Reset Counts
   */
  def reset(): UIO[Unit]
