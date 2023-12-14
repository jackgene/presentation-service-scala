package com.jackleow.presentation.service.interactive.model

import spray.json.DefaultJsonProtocol.*
import spray.json.RootJsonWriter

object ChatMessages:
  given jsonFormat: RootJsonWriter[ChatMessages] =
    jsonFormat1(ChatMessages.apply)

final case class ChatMessages(chatText: Seq[String])
