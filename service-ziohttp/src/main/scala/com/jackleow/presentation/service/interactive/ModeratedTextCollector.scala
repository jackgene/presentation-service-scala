package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.*
import zio.stream.{SubscriptionRef, UStream}
import zio.{Ref, UIO, URLayer, ZIO, ZLayer}

object ModeratedTextCollector:
  def live(name: String): URLayer[
    SubscriberCountingHub[ChatMessage, "chat"] & SubscriberCountingHub[ChatMessage, "rejected"],
    ModeratedTextCollector
  ] =
    ZLayer:
      for
        incomingEventHub <- ZIO.service[SubscriberCountingHub[ChatMessage, "chat"]]
        rejectedMessagesHub <- ZIO.service[SubscriberCountingHub[ChatMessage, "rejected"]]
        moderatedMessagesRef <- SubscriptionRef.make(Seq[String]())
        subscribers <- SubscriptionRef.make(0)
      yield ModeratedTextCollectorLive(
        name, incomingEventHub.elements, rejectedMessagesHub,
        moderatedMessagesRef, subscribers
      )

trait ModeratedTextCollector:
  def moderatedMessages: UIO[UStream[ChatMessages]]
  def reset(): UIO[Unit]