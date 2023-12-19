package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.ChatMessage
import com.jackleow.zio.*
import zio.*

object ChatMessageBroadcaster:
  val live: ULayer[SubscriberCountingHub[ChatMessage] Named "chat"] =
    ZLayer:
      SubscriberCountingHub.make[ChatMessage]("chat")
    .withName["chat"]
