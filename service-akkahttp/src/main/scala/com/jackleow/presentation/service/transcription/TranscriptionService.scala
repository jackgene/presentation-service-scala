package com.jackleow.presentation.service.transcription

import akka.NotUsed
import akka.stream.scaladsl.Flow
import spray.json.DefaultJsonProtocol.*
import spray.json.RootJsonFormat

import scala.concurrent.Future

/**
 * Receives and broadcasts transcriptions.
 */
object TranscriptionService {
  final case class Transcription(transcriptionText: String)

  implicit val transcriptionFormat: RootJsonFormat[Transcription] =
    jsonFormat1(Transcription.apply)
}
trait TranscriptionService {
  import TranscriptionService.*

  /**
   * Receives a new transcription (e.g., from a speech-to-text tool).
   *
   * @param text the transcription text
   * @return if the transcription was successfully enqueued
   */
  def receiveTranscription(text: String): Future[Unit]

  /**
   * Returns a `Flow` from `Nothing` to `Transcription`s.
   *
   * @return the transcriptions
   */
  def transcriptions: Flow[Any, Transcription, NotUsed]
}
