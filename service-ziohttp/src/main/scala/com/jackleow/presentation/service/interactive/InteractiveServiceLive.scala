package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.*
import zio.*
import zio.stream.*

private final case class InteractiveServiceLive(
  incomingEventHub: SubscriberCountingHub[ChatMessage, "chat"],
  rejectedMessageHub: SubscriberCountingHub[ChatMessage, "rejected"],
  languagePollCounter: SendersByTokenCounter["language-poll"],
  wordCloudCounter: SendersByTokenCounter["word-cloud"],
  questionsCollector: ModeratedTextCollector["question"]
) extends InteractiveService:
  override def receiveChatMessage(chatMessage: ChatMessage): UIO[Boolean] =
    for
      _ <- ZIO.log(s"Received chat message - $chatMessage")
      success: Boolean <- incomingEventHub.publish(chatMessage)
    yield success

  override def reset(): UIO[Unit] =
    for
      _ <- languagePollCounter.reset()
      _ <- wordCloudCounter.reset()
      _ <- questionsCollector.reset()
    yield ()

  override def languagePoll: UIO[UStream[Counts]] =
    languagePollCounter.counts

  override def wordCloud: UIO[UStream[Counts]] =
    wordCloudCounter.counts

  override def questions: UIO[UStream[ChatMessages]] =
    questionsCollector.moderatedMessages

  override def rejectedMessages: UIO[UStream[ChatMessage]] =
    ZIO.succeed(rejectedMessageHub.elements)

