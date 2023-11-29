package com.jackleow.presentation.service.transcription

import com.jackleow.presentation.infrastructure.AkkaModule
import com.typesafe.scalalogging.StrictLogging

/**
 * Akka Stream implementation of `TranscriptionModule`.
 */
trait AkkaStreamTranscriptionModule
  extends TranscriptionModule with StrictLogging:

  this: AkkaModule =>

  override val transcriptionService: TranscriptionService =
    new AkkaStreamTranscriptionService
