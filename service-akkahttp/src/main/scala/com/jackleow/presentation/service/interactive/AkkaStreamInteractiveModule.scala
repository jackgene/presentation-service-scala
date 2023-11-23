package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.infrastructure.AkkaModule
import com.typesafe.scalalogging.StrictLogging

trait AkkaStreamInteractiveModule extends InteractiveModule with StrictLogging {
  this: AkkaModule =>

  override val interactiveService: InteractiveService = new AkkaStreamInteractiveService
}
