package com.jackleow.presentation.service.transcription

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.transcription.model.Transcription
import zio.*
import zio.stream.*

private final class TranscriptionBroadcasterLive(
  hub: SubscriberCountingHub[Transcription, ?]
) extends TranscriptionBroadcaster:
  override val transcriptions: UStream[Transcription] = hub.elements

  override def broadcast(transcriptionText: String): UIO[Boolean] =
    for
      _ <- ZIO.log(s"Received transcription text: $transcriptionText")
      success: Boolean <- hub.publish(Transcription(transcriptionText))
    yield success
