package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.ChatMessage
import zio.*

object ChatMessageBroadcaster:
  val live: ULayer[SubscriberCountingHub[ChatMessage, "chat"]] =
    ZLayer:
      SubscriberCountingHub.make[ChatMessage, "chat"]
