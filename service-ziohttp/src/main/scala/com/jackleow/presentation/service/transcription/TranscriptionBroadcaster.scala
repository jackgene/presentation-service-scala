package com.jackleow.presentation.service.transcription

import com.jackleow.presentation.service.transcription.TranscriptionBroadcaster.Transcription
import com.jackleow.zio.stream.countRunning
import zio.*
import zio.json.{DeriveJsonEncoder, JsonEncoder}
import zio.stream.*

object TranscriptionBroadcaster:
  object Transcription:
    given encoder: JsonEncoder[Transcription] =
      DeriveJsonEncoder.gen[Transcription]
  final case class Transcription(transcriptionText: String)

  def make: UIO[TranscriptionBroadcaster] =
    for
      hub: Hub[Transcription] <- Hub.dropping(1)
      subscribers: Ref[Int] <- Ref.make(0)
    yield DefaultTranscriptionBroadcaster(hub, subscribers)

  private final class DefaultTranscriptionBroadcaster (
    hub: Hub[Transcription], subscribersRef: Ref[Int]
  ) extends TranscriptionBroadcaster:
    override val transcriptions: UStream[Transcription] =
      ZStream
        .fromHub(hub)
        .countRunning(
          subscribersRef,
          (subscribers: Int) => ZIO.log(s"+1 subscriber (=$subscribers)"),
          (subscribers: Int) => ZIO.log(s"-1 subscriber (=$subscribers)"),
        )

    override def broadcast(transcriptionText: String): UIO[Boolean] =
      for
        _ <- ZIO.log(s"Received transcription text: $transcriptionText")
        success: Boolean <- hub.publish(Transcription(transcriptionText))
      yield success

trait TranscriptionBroadcaster:
  def transcriptions: UStream[Transcription]
  def broadcast(transcriptionText: String): UIO[Boolean]
