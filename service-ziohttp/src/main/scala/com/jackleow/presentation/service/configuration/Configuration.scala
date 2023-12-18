package com.jackleow.presentation.service.configuration

import com.jackleow.presentation.service.configuration.model.PresentationConfiguration
import zio.*
import zio.config.*
import zio.config.typesafe.TypesafeConfigSource

object Configuration:
  val live: Layer[ReadError[String], PresentationConfiguration] =
    ZLayer:
      read(
        PresentationConfiguration
          .configDescriptor
          .mapKey(toKebabCase)

        from

        TypesafeConfigSource
          .fromResourcePath
          .at(path"presentation")
      )
