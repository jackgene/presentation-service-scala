package com.jackleow.presentation.service.transcription.model

import zio.json.{DeriveJsonEncoder, JsonEncoder}

object Transcription:
  given encoder: JsonEncoder[Transcription] =
    DeriveJsonEncoder.gen[Transcription]
final case class Transcription(transcriptionText: String)
