package com.jackleow.presentation.service.interactive

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.jackleow.presentation.collection.MultiSet
import spray.json.DefaultJsonProtocol.*
import spray.json.{JsArray, JsNumber, JsObject, JsString, JsValue, RootJsonWriter}

import scala.concurrent.Future

object InteractiveService {
  final case class ChatMessage(
    sender: String,
    recipient: String,
    text: String
  ) {
    override def toString: String = s"$sender to $recipient: $text"
  }

  implicit object chatMessageFormat extends RootJsonWriter[ChatMessage] {
    override def write(chatMessage: ChatMessage): JsValue = JsObject(
      "s" -> JsString(chatMessage.sender),
      "r" -> JsString(chatMessage.recipient),
      "t" -> JsString(chatMessage.text)
    )
  }

  final case class Counts(tokens: MultiSet[String])
  implicit object countsFormat extends RootJsonWriter[Counts] {
    override def write(counts: Counts): JsValue = JsObject(
      // JSON keys must be strings
      "tokensAndCounts" -> JsArray(
        counts.tokens.elementsByCount.
          map { (count: Int, elems: Seq[String]) =>
            JsArray(
              JsNumber(count),
              JsArray(elems.map(JsString(_)): _*)
            )
          }.
          toList: _*
      )
    )
  }

  final case class ChatMessages(chatText: Seq[String])

  implicit val chatMessagesFormat: RootJsonWriter[ChatMessages] =
    jsonFormat1(ChatMessages.apply)

  case object Reset
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

  def reset(): Future[Unit]

  def languagePoll: Flow[Any, Counts, NotUsed]

  def wordCloud: Flow[Any, Counts, NotUsed]

  def questions: Flow[Any, ChatMessages, NotUsed]

  def rejectedMessages: Flow[Any, ChatMessage, NotUsed]
}
