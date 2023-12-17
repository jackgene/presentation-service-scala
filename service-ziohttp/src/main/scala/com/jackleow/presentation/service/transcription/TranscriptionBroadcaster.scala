package com.jackleow.presentation.service.transcription

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.transcription.model.Transcription
import zio.*
import zio.stream.*

object TranscriptionBroadcaster:
  def live: ULayer[TranscriptionBroadcaster] =
    ZLayer:
      for
        hub <- SubscriberCountingHub.make[Transcription, "transcription"]
      yield TranscriptionBroadcasterLive(hub)

  def transcriptions: URIO[TranscriptionBroadcaster, UStream[Transcription]] =
    ZIO.serviceWith[TranscriptionBroadcaster](_.transcriptions)

  def broadcast(transcriptionText: String): URIO[TranscriptionBroadcaster, Boolean] =
    ZIO.serviceWithZIO[TranscriptionBroadcaster](_.broadcast(transcriptionText))

trait TranscriptionBroadcaster:
  /**
   * Stream of transcriptions.
   */
  def transcriptions: UStream[Transcription]

  /**
   * Broadcasts a new transcription (e.g., from a speech-to-text tool).
   *
   * @param transcriptionText the transcription text
   * @return if the transcription was successfully enqueued
   */
  def broadcast(transcriptionText: String): UIO[Boolean]
