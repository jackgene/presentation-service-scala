package com.jackleow.presentation.service.configuration

import com.jackleow.presentation.service.configuration.model.PresentationConfiguration
import zio.*
import zio.config.*
import zio.config.typesafe.TypesafeConfigProvider

object Configuration:
  val live: Layer[Config.Error, PresentationConfiguration] =
    ZLayer:
      ZIO.withConfigProvider(
        TypesafeConfigProvider.fromResourcePath().kebabCase
      )(ZIO.config(PresentationConfiguration.config))
