package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.*
import com.jackleow.zio.Named
import zio.*
import zio.stream.*

object ModeratedTextCollector:
  def live(name: String): URLayer[
    (SubscriberCountingHub[ChatMessage] Named "chat") & (SubscriberCountingHub[ChatMessage] Named "rejected"),
    ModeratedTextCollector
  ] =
    ZLayer:
      for
        chatMessagesHub <- ZIO.service[SubscriberCountingHub[ChatMessage] Named "chat"]
        rejectedMessagesHub <- ZIO.service[SubscriberCountingHub[ChatMessage] Named "rejected"]
        moderatedMessagesRef <- SubscriptionRef.make(Seq[String]())
        subscribers <- SubscriptionRef.make(0)
      yield ModeratedTextCollectorLive(
        name, chatMessagesHub.get.elements, rejectedMessagesHub,
        moderatedMessagesRef, subscribers
      )

trait ModeratedTextCollector:
  /**
   * Stream of moderated ChatMessages.
   */
  def moderatedMessages: UIO[UStream[ChatMessages]]

  /**
   * Clear all moderated ChatMessages.
   */
  def reset(): UIO[Unit]