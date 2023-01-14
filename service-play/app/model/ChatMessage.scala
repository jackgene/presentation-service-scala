package model

import play.api.libs.json._
import play.api.libs.functional.syntax._

object ChatMessage {
  implicit val writes: Writes[ChatMessage] = (
      (JsPath \ "s").write[String] and
      (JsPath \ "r").write[String] and
      (JsPath \ "t").write[String]
  )(unlift(ChatMessage.unapply))
}
case class ChatMessage(
  sender: String,
  recipient: String,
  text: String
) {
  override def toString: String = s"${sender} to ${recipient}: ${text}"
}