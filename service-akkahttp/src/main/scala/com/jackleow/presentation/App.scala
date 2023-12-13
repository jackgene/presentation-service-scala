package com.jackleow.presentation

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior}
import akka.http.scaladsl.Http
import com.jackleow.presentation.config.{Configuration, ConfigurationModule}
import com.jackleow.presentation.infrastructure.AkkaModule
import com.jackleow.presentation.route.ServiceRouteModule
import com.jackleow.presentation.service.interactive.AkkaStreamInteractiveModule
import com.jackleow.presentation.service.transcription.AkkaStreamTranscriptionModule
import com.typesafe.scalalogging.StrictLogging

import java.io.File
import scala.util.{Failure, Success}

final class App(override val configuration: Configuration, htmlFile: File, port: Int)
  extends ServiceRouteModule
  with AkkaStreamInteractiveModule
  with AkkaStreamTranscriptionModule
  with AkkaModule
  with ConfigurationModule
  with StrictLogging:

  override lazy val system: ActorSystem[Nothing] =
    ActorSystem[Nothing](rootBehavior, "presentation-service")

  private def rootBehavior: Behavior[Nothing] =
    Behaviors.setup[Nothing]:
      (ctx: ActorContext[Nothing]) =>
        startHttpServer(htmlFile, port)(ctx.system)
        Behaviors.empty

  private def startHttpServer(htmlFile: File, port: Int)(
    implicit system: ActorSystem[?]
  ): Unit =
    import system.executionContext
    logger.debug(s"Starting presentation service with configuration\n$configuration")

    val bindingFut = Http()
      .newServerAt("0.0.0.0", port)
      .bind(routes(htmlFile))
    bindingFut.onComplete:
      case Success(binding) =>
        val addr = binding.localAddress
        logger.info(s"Server online at http://${addr.getHostString}:${addr.getPort}/")
      case Failure(ex) =>
        logger.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
