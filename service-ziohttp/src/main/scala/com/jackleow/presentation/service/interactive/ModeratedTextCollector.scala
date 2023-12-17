package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.*
import zio.*
import zio.stream.*

object ModeratedTextCollector:
  def live[N <: String](using name: ValueOf[N], tag: Tag[N]): URLayer[
    SubscriberCountingHub[ChatMessage, "chat"] & SubscriberCountingHub[ChatMessage, "rejected"],
    ModeratedTextCollector[N]
  ] =
    ZLayer:
      for
        incomingEventHub <- ZIO.service[SubscriberCountingHub[ChatMessage, "chat"]]
        rejectedMessagesHub <- ZIO.service[SubscriberCountingHub[ChatMessage, "rejected"]]
        moderatedMessagesRef <- SubscriptionRef.make(Seq[String]())
        subscribers <- SubscriptionRef.make(0)
      yield ModeratedTextCollectorLive[N](
        valueOf[N], incomingEventHub.elements, rejectedMessagesHub,
        moderatedMessagesRef, subscribers
      )

trait ModeratedTextCollector[N <: String]:
  /**
   * Stream of moderated ChatMessages.
   */
  def moderatedMessages: UIO[UStream[ChatMessages]]

  /**
   * Clear all moderated ChatMessages.
   */
  def reset(): UIO[Unit]