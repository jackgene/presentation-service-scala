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
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderFailures
import scopt.OParser

import java.io.File
import scala.util.{Failure, Success}

class App(override val configuration: Configuration, htmlFile: File, port: Int)
    extends ServiceRouteModule
    with AkkaStreamInteractiveModule
    with AkkaStreamTranscriptionModule
    with AkkaModule
    with ConfigurationModule
    with StrictLogging {

  override lazy val system: ActorSystem[Nothing] =
    ActorSystem[Nothing](rootBehavior, "presentation-service")

  private def rootBehavior: Behavior[Nothing] =
    Behaviors.setup[Nothing] { (ctx: ActorContext[Nothing]) =>
      startHttpServer(htmlFile, port)(ctx.system)
      Behaviors.empty
    }

  private def startHttpServer(htmlFile: File, port: Int)(
    implicit system: ActorSystem[?]
  ): Unit = {
    import system.executionContext

    val bindingFut = Http()
      .newServerAt("0.0.0.0", port)
      .bind(routes(htmlFile))
    bindingFut.onComplete {
      case Success(binding) =>
        val addr = binding.localAddress
        logger.info(s"Server online at http://${addr.getHostString}:${addr.getPort}/")
      case Failure(ex) =>
        logger.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }
}
object App extends StrictLogging {
  private case class Arguments(
    htmlFile: File = new File(""),
    port: Int = 8973
  )
  private val builder = OParser.builder[Arguments]
  private val parser = {
    import builder.*
    OParser.sequence(
      programName("presentation-service"),
      head("Presentation Service in Akka HTTP"),
      opt[File]("html-path")
        .required()
        .action {
          (file: File, args: Arguments) => args.copy(htmlFile = file)
      }
        .text("Presentation HTML file path"),
      opt[Int]('p', "port")
        .action {
          (port: Int, args: Arguments) => args.copy(port = port)
      }
        .text("HTTP server port"),
    )
  }

  def main(rawArgs: Array[String]): Unit =
    (
      OParser.parse(parser, rawArgs, Arguments()),
      ConfigSource.default.load[Configuration]
    ) match {
      case (Some(args: Arguments), Right(configuration: Configuration)) =>
        val _ = new App(configuration, args.htmlFile, args.port)

      case (_, Left(failures: ConfigReaderFailures)) =>
        logger.error(
          s"Unable to load configuration:\n${failures.prettyPrint(1)}"
        )

      case _ =>
    }
}
