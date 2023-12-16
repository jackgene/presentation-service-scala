package com.jackleow.presentation.service.interactive.model

import zio.json.{DeriveJsonEncoder, JsonEncoder}

object ChatMessages:
  given jsonEncoder: JsonEncoder[ChatMessages] =
    DeriveJsonEncoder.gen[ChatMessages]

final case class ChatMessages(chatText: Seq[String])
