package model

import play.api.libs.json._
import play.api.libs.functional.syntax._

object SenderAndToken {
  implicit val writes: Writes[SenderAndToken] = (
    (JsPath \ "sender").write[String] and
    (JsPath \ "token").write[String]
  )(unlift(SenderAndToken.unapply))
}
case class SenderAndToken(
  sender: String,
  token: String
) {
  override def toString: String = s"${sender}: ${token}"
}