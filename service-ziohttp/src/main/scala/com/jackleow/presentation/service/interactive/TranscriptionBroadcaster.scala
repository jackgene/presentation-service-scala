package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.interactive.TranscriptionBroadcaster.Transcription
import zio.*
import zio.json.{DeriveJsonEncoder, JsonEncoder}
import zio.stream.*

object TranscriptionBroadcaster:
  final case class Transcription(transcriptionText: String)
  implicit val encoder: JsonEncoder[Transcription] = DeriveJsonEncoder.gen[Transcription]

  def make: UIO[TranscriptionBroadcaster] =
    for
      hub: Hub[Transcription] <- Hub.dropping(1)
      subscribers: Ref[Int] <- Ref.make(0)
    yield TranscriptionBroadcaster(hub, subscribers)

final class TranscriptionBroadcaster private (
  hub: Hub[Transcription], subscribersRef: Ref[Int]
):
  val transcriptions: UStream[Transcription] =
    ZStream
      .acquireReleaseWith(
        for
          subscribers: Int <- subscribersRef.updateAndGet(_ + 1)
          _ <- ZIO.log(s"+1 subscriber (=$subscribers)")
        yield ()
      ): _ =>
        for
          subscribers: Int <- subscribersRef.updateAndGet(_ - 1)
          _ <- ZIO.log(s"-1 subscriber (=$subscribers)")
        yield ()
      .flatMap: _ =>
        ZStream.fromHub(hub)

  def broadcast(transcriptionText: String): UIO[Boolean] =
    for
      _ <- ZIO.log(s"Received transcription text: $transcriptionText")
      success: Boolean <- hub.publish(Transcription(transcriptionText))
    yield success
