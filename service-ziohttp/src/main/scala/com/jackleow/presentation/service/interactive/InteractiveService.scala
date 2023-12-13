package com.jackleow.presentation.service.interactive

import zio.{UIO, ZIO}

object InteractiveService:
  def make(chatMessageBroadcaster: ChatMessageBroadcaster): UIO[InteractiveService] =
    ZIO.succeed(new DefaultInteractiveService(chatMessageBroadcaster))

  private final class DefaultInteractiveService(
    chatMessageBroadcaster: ChatMessageBroadcaster
  ) extends InteractiveService:
    override def receiveChatMessage(
      chatMessage: ChatMessageBroadcaster.ChatMessage
    ): UIO[Boolean] =
      chatMessageBroadcaster.broadcast(chatMessage)

trait InteractiveService:
  def receiveChatMessage(
    chatMessage: ChatMessageBroadcaster.ChatMessage
  ): UIO[Boolean]
