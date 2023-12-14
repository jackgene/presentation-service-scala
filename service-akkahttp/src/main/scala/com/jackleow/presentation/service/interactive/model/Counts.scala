package com.jackleow.presentation.service.interactive.model

import com.jackleow.presentation.collection.MultiSet
import spray.json.{JsArray, JsNumber, JsObject, JsString, JsValue, RootJsonWriter}

object Counts:
  given jsonWriter: RootJsonWriter[Counts] =
    (counts: Counts) => JsObject(
      // JSON keys must be strings
      "tokensAndCounts" -> JsArray(
        counts.tokens.elementsByCount
          .map:
            (count: Int, elems: Seq[String]) =>
              JsArray(
                JsNumber(count),
                JsArray(elems.map(JsString(_)): _*)
              )
          .toList: _*
      )
    )

final case class Counts(tokens: MultiSet[String])
