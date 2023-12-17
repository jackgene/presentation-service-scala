package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.ChatMessage
import zio.*

object RejectedMessageBroadcaster:
  val live: ULayer[SubscriberCountingHub[ChatMessage, "rejected"]] =
    ZLayer:
      SubscriberCountingHub.make[ChatMessage, "rejected"]
