package com.jackleow.zio

final case class Named[A, N <: Singleton & String](get: A)
