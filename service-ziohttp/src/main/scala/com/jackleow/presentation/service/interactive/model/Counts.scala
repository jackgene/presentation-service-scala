package com.jackleow.presentation.service.interactive.model

import com.jackleow.presentation.collection.MultiSet
import zio.json.{DeriveJsonEncoder, JsonEncoder, jsonField}

object Counts:
  private given multiSetJsonEncoder: JsonEncoder[MultiSet[String]] =
    JsonEncoder[Seq[(Int, Seq[String])]].contramap(_.elementsByCount.toList)

  given jsonEncoder: JsonEncoder[Counts] =
    DeriveJsonEncoder.gen[Counts]

final case class Counts(
  @jsonField("tokensAndCounts") tokens: MultiSet[String]
)
