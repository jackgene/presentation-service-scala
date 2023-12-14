package com.jackleow.presentation.service.transcription

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.jackleow.presentation.service.transcription.model.Transcription

import scala.concurrent.Future

/**
 * Defines transcription dependencies.
 */
trait TranscriptionModule:
  /**
   * Receives and broadcasts transcriptions.
   */
  trait TranscriptionService:
    /**
     * Broadcasts a new transcription (e.g., from a speech-to-text tool).
     *
     * @param text the transcription text
     * @return if the transcription was successfully enqueued
     */
    def broadcastTranscription(text: String): Future[Unit]

    /**
     * Returns a `Flow` from `Nothing` to `Transcription`s.
     *
     * @return the transcriptions
     */
    def transcriptions: Flow[Any, Transcription, NotUsed]

  def transcriptionService: TranscriptionService
