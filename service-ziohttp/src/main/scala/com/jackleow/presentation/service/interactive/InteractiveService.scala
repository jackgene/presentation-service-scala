package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.*
import com.jackleow.zio.Named
import zio.*
import zio.stream.*

object InteractiveService:
  val live: ZLayer[
    (SubscriberCountingHub[ChatMessage] Named "chat") & (SubscriberCountingHub[ChatMessage] Named "rejected") &
      (SendersByTokenCounter Named "language-poll") & (SendersByTokenCounter Named "word-cloud") &
      ModeratedTextCollector,
    Nothing,
    InteractiveService
  ] =
    ZLayer.fromFunction(InteractiveServiceLive.apply _)

  def receiveChatMessage(chatMessage: ChatMessage): ZIO[InteractiveService, Nothing, Boolean] =
    ZIO.serviceWithZIO[InteractiveService](_.receiveChatMessage(chatMessage))

  def reset(): ZIO[InteractiveService, Nothing, Unit] =
    ZIO.serviceWithZIO[InteractiveService](_.reset())

  def languagePoll: ZIO[InteractiveService, Nothing, UStream[Counts]] =
    ZIO.serviceWithZIO[InteractiveService](_.languagePoll)

  def wordCloud: ZIO[InteractiveService, Nothing, UStream[Counts]] =
    ZIO.serviceWithZIO[InteractiveService](_.wordCloud)

  def questions: ZIO[InteractiveService, Nothing, UStream[ChatMessages]] =
    ZIO.serviceWithZIO[InteractiveService](_.questions)

  def rejectedMessages: ZIO[InteractiveService, Nothing, UStream[ChatMessage]] =
    ZIO.serviceWithZIO[InteractiveService](_.rejectedMessages)

trait InteractiveService:
  /**
   * Receives a new chat message.
   *
   * @param chatMessage the chat message
   * @return if the chat message was successfully enqueued
   */
  def receiveChatMessage(chatMessage: ChatMessage): UIO[Boolean]

  /**
   * Resets all state in this service.
   *
   * @return if the reset was successfully enqueued
   */
  def reset(): UIO[Unit]

  /**
   * Stream of language poll counts.
   */
  def languagePoll: UIO[UStream[Counts]]

  /**
   * Stream of word cloud counts.
   */
  def wordCloud: UIO[UStream[Counts]]

  /**
   * Stream of questions.
   */
  def questions: UIO[UStream[ChatMessages]]

  /**
   * Stream of rejected chat messages.
   */
  def rejectedMessages: UIO[UStream[ChatMessage]]
