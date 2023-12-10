package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.interactive.TranscriptionBroadcaster.Transcription
import com.jackleow.zio.stream.countRunning
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
      .fromHub(hub)
      .countRunning(
        subscribersRef,
        (subscribers: Int) => ZIO.log(s"+1 subscriber (=$subscribers)"),
        (subscribers: Int) => ZIO.log(s"-1 subscriber (=$subscribers)"),
      )

  def broadcast(transcriptionText: String): UIO[Boolean] =
    for
      _ <- ZIO.log(s"Received transcription text: $transcriptionText")
      success: Boolean <- hub.publish(Transcription(transcriptionText))
    yield success
