package com.jackleow.akka.stream.scaladsl

import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.scaladsl.{Flow, Keep, Source}
import spray.json.*

import scala.concurrent.duration.FiniteDuration

extension (flow: Flow.type)
  def activeFilter[T, Mat](
    actives: Source[Boolean, Mat],
    filterOnly: T => Boolean = (_: T) => true
  ): Flow[T, T, Mat] =
    Flow[T]
      .map(Right(_)).mergeMat(actives.map(Left(_)))(Keep.right)
      .scan[(Boolean, Option[T])](false -> None):
        case (_, Left(newActive: Boolean)) =>
          newActive -> None

        case ((false, _), Right(out: T)) if filterOnly(out) =>
          false -> None

        case ((active: Boolean, _), Right(out: T)) =>
          active -> Some(out)
      .collect:
        case (_, Some(out: T)) => out

extension[In, Out, Mat] (flow: Flow[In, Out, Mat])
  def sample(elements: Int, per: FiniteDuration): Flow[In, Out, Mat] =
    flow
      .conflate:
        (_, next: Out) => next
      .throttle(elements, per)

  def toJsonWebSocketMessage(implicit writer: JsonWriter[Out]): Flow[In, TextMessage, Mat] =
    flow.
      map:
        (out: Out) =>
          TextMessage(out.toJson.compactPrint)