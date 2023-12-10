package com.jackleow.zio.stream

import zio.{Ref, UIO, ZIO}
import zio.stream.ZStream

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
