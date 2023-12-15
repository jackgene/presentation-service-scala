package com.jackleow.presentation

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior}
import akka.http.scaladsl.Http
import com.jackleow.presentation.config.*
import com.jackleow.presentation.infrastructure.AkkaModule
import com.jackleow.presentation.route.DefaultRouteModule
import com.jackleow.presentation.service.interactive.DefaultInteractiveModule
import com.jackleow.presentation.service.interactive.graph.*
import com.jackleow.presentation.service.transcription.DefaultTranscriptionModule
import com.typesafe.scalalogging.StrictLogging

import java.io.File
import scala.util.{Failure, Success}

final class App(override val configuration: Configuration, htmlFile: File, port: Int)
  extends StrictLogging
  with ConfigurationModule
  with AkkaModule
  with DefaultTranscriptionModule
  with DefaultRejectedMessageModule
  with DefaultLanguagePollModule
  with DefaultWordCloudModule
  with DefaultQuestionsModule
  with DefaultInteractiveModule
  with DefaultRouteModule:

  override given system: ActorSystem[Nothing] =
    ActorSystem[Nothing](rootBehavior, "presentation-service")

  private def rootBehavior: Behavior[Nothing] =
    Behaviors.setup[Nothing]:
      (ctx: ActorContext[Nothing]) =>
        startHttpServer(htmlFile, port)(using ctx.system)
        Behaviors.empty

  private def startHttpServer(htmlFile: File, port: Int)(
    using system: ActorSystem[?]
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
