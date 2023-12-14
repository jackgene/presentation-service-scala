package com.jackleow.presentation.service.interactive

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.jackleow.presentation.service.interactive.model.*

import scala.concurrent.Future

/**
 * Defines interactive service dependencies.
 */
trait InteractiveModule:
  /**
   * Receives chat messages, perform filtering and aggregations, and
   * broadcasting the results to support interactive components such as:
   * - Questions
   * - Polls
   * - Word Clouds
   */
  trait InteractiveService:
    /**
     * Receives a new chat message.
     *
     * @param chatMessage the chat message
     * @return if the chat message was successfully enqueued
     */
    def receiveChatMessage(chatMessage: ChatMessage): Future[Unit]

    /**
     * Resets all state in this service.
     *
     * @return if the chat message was successfully enqueued
     */
    def reset(): Future[Unit]

    /**
     * Flow that produces language poll counts.
     *
     * Closes when sink closes.
     */
    def languagePoll: Flow[Any, Counts, NotUsed]

    /**
     * Flow that produces word cloud counts.
     *
     * Closes when sink closes.
     */
    def wordCloud: Flow[Any, Counts, NotUsed]

    /**
     * Flow that produces questions.
     *
     * Closes when sink closes.
     */
    def questions: Flow[Any, ChatMessages, NotUsed]

    /**
     * Flow that produces rejected chat messages.
     *
     * Closes when sink closes.
     */
    def rejectedMessages: Flow[Any, ChatMessage, NotUsed]

  def interactiveService: InteractiveService
