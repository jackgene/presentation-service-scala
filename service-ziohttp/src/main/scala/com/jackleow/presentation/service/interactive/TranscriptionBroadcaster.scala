package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.interactive.TranscriptionBroadcaster.Transcription
import zio.json.{DeriveJsonEncoder, JsonEncoder}
import zio.stream.{UStream, ZStream}
import zio.{Hub, UIO}

object TranscriptionBroadcaster:
  final case class Transcription(transcriptionText: String)
  implicit val encoder: JsonEncoder[Transcription] = DeriveJsonEncoder.gen[Transcription]

  def make: UIO[TranscriptionBroadcaster] =
    for
      hub <- Hub.dropping(1)
    yield TranscriptionBroadcaster(hub)

final class TranscriptionBroadcaster private (hub: Hub[Transcription]):
  val transcriptions: UStream[Transcription] = ZStream.fromHub(hub)

  def broadcast(transcriptionText: String): UIO[Boolean] =
    hub.publish(Transcription(transcriptionText))
