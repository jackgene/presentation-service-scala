package com.jackleow.presentation.service.transcription.model

import spray.json.DefaultJsonProtocol.*
import spray.json.RootJsonWriter

object Transcription:
  given jsonFormat: RootJsonWriter[Transcription] =
    jsonFormat1(Transcription.apply)

final case class Transcription(transcriptionText: String)
