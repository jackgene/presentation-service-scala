package com.jackleow.presentation.service.interactive.model

import zio.json.{DeriveJsonEncoder, JsonEncoder, jsonField}

object ChatMessage:
  given jsonEncoder: JsonEncoder[ChatMessage] =
    DeriveJsonEncoder.gen[ChatMessage]

final case class ChatMessage(
  @jsonField("s") sender: String,
  @jsonField("r") recipient: String,
  @jsonField("t") text: String
):
  override def toString: String = s"$sender to $recipient: $text"
