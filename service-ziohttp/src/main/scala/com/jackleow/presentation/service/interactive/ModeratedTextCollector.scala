package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.*
import zio.stream.{SubscriptionRef, UStream}
import zio.{Ref, UIO, URLayer, ZIO, ZLayer}

object ModeratedTextCollector:
  def live(name: String): URLayer[
    SubscriberCountingHub[ChatMessage | Reset.type] & SubscriberCountingHub[ChatMessage],
    ModeratedTextCollector
  ] =
    ZLayer:
      for
        incomingEventHub <- ZIO.service[SubscriberCountingHub[ChatMessage | Reset.type]]
        rejectedMessagesHub <- ZIO.service[SubscriberCountingHub[ChatMessage]]
        moderatedMessagesRef <- SubscriptionRef.make(Seq[String]())
        subscribers <- SubscriptionRef.make(0)
      yield ModeratedTextCollectorLive(
        name, incomingEventHub.elements, rejectedMessagesHub,
        moderatedMessagesRef, subscribers
      )

trait ModeratedTextCollector:
  def moderatedMessages: UIO[UStream[ChatMessages]]