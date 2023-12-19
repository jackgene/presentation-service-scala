package com.jackleow.zio

import zio.*

import scala.language.postfixOps

extension[RIn, E, ROut] (zlayer: ZLayer[RIn, E, ROut])
  def withName[N <: String](using name: ValueOf[N], tagN: Tag[N], tagROut: Tag[ROut]): ZLayer[RIn, E, Named[ROut, N]] =
    zlayer.map: (zenv: ZEnvironment[ROut]) =>
      ZEnvironment:
        new Named[ROut, N]:
          override val get: ROut = zenv.get
//
//extension[R, E, A] (zio: ZIO[R, E, A])
//  def withName[N <: String](using name: ValueOf[N], tagN: Tag[N]): ZIO[R, E, Named[A, N]] =
//    zio.map: (a: A) =>
//      new Named[A, N]:
//        override val get: A = a
