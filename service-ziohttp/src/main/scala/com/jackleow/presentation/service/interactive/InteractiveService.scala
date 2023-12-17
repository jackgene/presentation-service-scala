package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.*
import zio.*
import zio.stream.*

object InteractiveService:
  def live: URLayer[
    SubscriberCountingHub[ChatMessage, "chat"] & SubscriberCountingHub[ChatMessage, "rejected"] & ModeratedTextCollector["question"],
    InteractiveService
  ] =
    ZLayer.fromFunction(InteractiveServiceLive.apply _)

  def receiveChatMessage(chatMessage: ChatMessage): URIO[InteractiveService, Boolean] =
    ZIO.serviceWithZIO[InteractiveService](_.receiveChatMessage(chatMessage))

  def reset(): URIO[InteractiveService, Unit] =
    ZIO.serviceWithZIO[InteractiveService](_.reset())

  def languagePoll: URIO[InteractiveService, UStream[Counts]] =
    ZIO.serviceWith[InteractiveService](_.languagePoll)

  def wordCloud: URIO[InteractiveService, UStream[Counts]] =
    ZIO.serviceWith[InteractiveService](_.wordCloud)

  def questions: URIO[InteractiveService, UStream[ChatMessages]] =
    ZIO.serviceWithZIO[InteractiveService](_.questions)

  def rejectedMessages: URIO[InteractiveService, UStream[ChatMessage]] =
    ZIO.serviceWith[InteractiveService](_.rejectedMessages)

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
  def languagePoll: UStream[Counts]

  /**
   * Stream of word cloud counts.
   */
  def wordCloud: UStream[Counts]

  /**
   * Stream of questions.
   */
  def questions: UIO[UStream[ChatMessages]]

  /**
   * Stream of rejected chat messages.
   */
  def rejectedMessages: UStream[ChatMessage]
