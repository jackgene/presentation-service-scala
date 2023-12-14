package com.jackleow.presentation.service.interactive.model

import spray.json.*

object ChatMessage:
  given jsonWriter: RootJsonWriter[ChatMessage] =
    (chatMessage: ChatMessage) => JsObject(
      "s" -> JsString(chatMessage.sender),
      "r" -> JsString(chatMessage.recipient),
      "t" -> JsString(chatMessage.text)
    )

final case class ChatMessage(
  sender: String,
  recipient: String,
  text: String
):
  override def toString: String = s"$sender to $recipient: $text"
