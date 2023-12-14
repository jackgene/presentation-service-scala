package com.jackleow.presentation.service.interactive.graph

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.*
import com.jackleow.akka.stream.scaladsl.*
import com.jackleow.presentation.infrastructure.AkkaModule
import com.jackleow.presentation.service.SubscriptionSupport
import com.jackleow.presentation.service.interactive.model.*
import com.typesafe.scalalogging.StrictLogging

trait DefaultRejectedMessageModule extends RejectedMessageModule
  with SubscriptionSupport
  with StrictLogging:
  this: AkkaModule =>

  override val (
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
      .mapMaterializedValue:
        case ((snk, subCount), src) => (snk, subCount, src)
      .run()
