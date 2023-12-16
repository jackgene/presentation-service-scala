package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.*
import zio.{UIO, ZIO}
import zio.stream.UStream

private final case class InteractiveServiceLive(
  incomingEventHub: SubscriberCountingHub[ChatMessage, "chat"],
  rejectedMessageHub: SubscriberCountingHub[ChatMessage, "rejected"],
  questionsCollector: ModeratedTextCollector
) extends InteractiveService:
  override def receiveChatMessage(chatMessage: ChatMessage): UIO[Boolean] =
    for
      _ <- ZIO.log(s"Received chat message - $chatMessage")
      success: Boolean <- incomingEventHub.publish(chatMessage)
    yield success

  override def reset(): UIO[Unit] =
    questionsCollector.reset()

  override def languagePoll: UStream[Counts] = ???

  override def wordCloud: UStream[Counts] = ???

  override def questions: UIO[UStream[ChatMessages]] = questionsCollector.moderatedMessages

  override def rejectedMessages: UStream[ChatMessage] = rejectedMessageHub.elements

