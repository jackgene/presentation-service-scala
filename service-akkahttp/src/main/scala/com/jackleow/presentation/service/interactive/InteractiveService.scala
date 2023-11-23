package com.jackleow.presentation.service.interactive

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.jackleow.presentation.collection.MultiSet
import spray.json.DefaultJsonProtocol.*
import spray.json.{DeserializationException, JsObject, JsString, JsValue, RootJsonFormat}

import scala.concurrent.Future

object InteractiveService {
  final case class ChatMessage(
    sender: String,
    recipient: String,
    text: String
  ) {
    override def toString: String = s"$sender to $recipient: $text"
  }

  implicit object chatMessageFormat extends RootJsonFormat[ChatMessage] {
    override def write(chatMessage: ChatMessage): JsValue = JsObject(
      "s" -> JsString(chatMessage.sender),
      "r" -> JsString(chatMessage.recipient),
      "t" -> JsString(chatMessage.text)
    )

    override def read(json: JsValue): ChatMessage =
      json.asJsObject.getFields("s", "r", "t") match {
        case Seq(JsString(sender), JsString(recipient), JsString(text)) =>
          ChatMessage(sender, recipient, text)
        case _ =>
          throw DeserializationException(
            """Expected JSON object with "s", "r", and "t" fields."""
          )
      }
  }

  final case class Counts(tokens: MultiSet[String])

  final case class ModeratedText(chatText: Seq[String])

  implicit val moderatedTextFormat: RootJsonFormat[ModeratedText] =
    jsonFormat1(ModeratedText.apply)
}
trait InteractiveService {
  import InteractiveService.*

  /**
   * Receives a new chat message.
   *
   * @param chatMessage the chat message
   * @return if the chat message was successfully enqueued
   */
  def receiveChatMessage(chatMessage: ChatMessage): Future[Unit]

  def languagePoll: Flow[Any, Counts, NotUsed]

  def wordCloud: Flow[Any, Counts, NotUsed]

  def questions: Flow[Any, ModeratedText, NotUsed]

  def rejectedMessages: Flow[Any, ChatMessage, NotUsed]
}
