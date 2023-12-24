package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.*
import com.jackleow.zio.Named
import zio.*
import zio.stream.*

private final case class InteractiveServiceLive(
  chatMessagesHub: SubscriberCountingHub[ChatMessage] Named "chat",
  rejectedMessageHub: SubscriberCountingHub[ChatMessage] Named "rejected",
  languagePollCounter: SendersByTokenCounter Named "language-poll",
  wordCloudCounter: SendersByTokenCounter Named "word-cloud",
  questionsCollector: ModeratedTextCollector
) extends InteractiveService:
  override def receiveChatMessage(chatMessage: ChatMessage): UIO[Boolean] =
    for
      _ <- ZIO.log(s"Received chat message - $chatMessage")
      success: Boolean <- chatMessagesHub.get.publish(chatMessage)
    yield success

  override def reset(): UIO[Unit] =
    for
      _ <- languagePollCounter.get.reset()
      _ <- wordCloudCounter.get.reset()
      _ <- questionsCollector.reset()
    yield ()

  override def languagePoll: UIO[UStream[Counts]] =
    languagePollCounter.get.counts

  override def wordCloud: UIO[UStream[Counts]] =
    wordCloudCounter.get.counts

  override def questions: UIO[UStream[ChatMessages]] =
    questionsCollector.moderatedMessages

  override def rejectedMessages: UIO[UStream[ChatMessage]] =
    ZIO.succeed(rejectedMessageHub.get.elements)

