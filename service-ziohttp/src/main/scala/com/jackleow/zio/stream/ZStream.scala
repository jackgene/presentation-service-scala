package com.jackleow.zio.stream

import zio.*
import zio.http.{ChannelEvent, WebSocketFrame}
import zio.json.{EncoderOps, JsonEncoder}
import zio.stream.*

extension[R, E, A] (stream: ZStream[R, E, A])
  def countRunning(
    countRef: Ref[Int],
    onStart: Int => UIO[Unit] = _ => ZIO.unit,
    onComplete: Int => UIO[Unit] = _ => ZIO.unit,
  ): ZStream[R, E, A] =
    ZStream
      .acquireReleaseWith(
        for
          count: Int <- countRef.updateAndGet(_ + 1)
          _ <- onStart(count)
        yield ()
      ): _ =>
        for
          count: Int <- countRef.updateAndGet(_ - 1)
          _ <- onComplete(count)
        yield ()
      .flatMap(_ => stream)

  def takeWhileActive(actives: UStream[Boolean]): ZStream[R, E, A] =
    actives
      .mergeEither(stream)
      .collectWhile:
        case activeSubs @ Left(true) => activeSubs
        case a @ Right(_) => a
      .collect:
        case Right(a: A) => a

  def mapToJsonWebSocketFrames(using JsonEncoder[A]): ZStream[R, E, ChannelEvent[WebSocketFrame]] =
    stream
      .map(_.toJson)
      .map(WebSocketFrame.text)
      .map(ChannelEvent.read)
