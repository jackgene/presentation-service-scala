package com.jackleow.zio

import zio.*

import scala.language.postfixOps

extension[RIn, E, ROut] (zlayer: ZLayer[RIn, E, ROut])
  def withName[N <: Singleton & String](using tagROut: Tag[ROut], tagN: Tag[N]): ZLayer[RIn, E, Named[ROut, N]] =
    zlayer.map: (zenv: ZEnvironment[ROut]) =>
      ZEnvironment:
        Named[ROut, N](zenv.get)
