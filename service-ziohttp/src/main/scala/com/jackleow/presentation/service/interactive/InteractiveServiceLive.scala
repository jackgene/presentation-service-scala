package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.*
import com.jackleow.zio.Named
import zio.*
import zio.stream.*

private final case class InteractiveServiceLive(
  namedChatMessagesHub: SubscriberCountingHub[ChatMessage] Named "chat",
  namedRejectedMessagesHub: SubscriberCountingHub[ChatMessage] Named "rejected",
  namedLanguagePollCounter: SendersByTokenCounter Named "language-poll",
  namedWordCloudCounter: SendersByTokenCounter Named "word-cloud",
  questionsCollector: ModeratedTextCollector
) extends InteractiveService:
  private val chatMessagesHub: SubscriberCountingHub[ChatMessage] =
    namedChatMessagesHub.get
  private val rejectedMessagesHub: SubscriberCountingHub[ChatMessage] =
    namedRejectedMessagesHub.get
  private val languagePollCounter: SendersByTokenCounter =
    namedLanguagePollCounter.get
  private val wordCloudCounter: SendersByTokenCounter =
    namedWordCloudCounter.get

  override def receiveChatMessage(chatMessage: ChatMessage): UIO[Boolean] =
    for
      _ <- ZIO.log(s"Received chat message - $chatMessage")
      success: Boolean <- chatMessagesHub.publish(chatMessage)
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
    ZIO.succeed(rejectedMessagesHub.elements)

