package com.jackleow.presentation

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.configuration.Configuration
import com.jackleow.presentation.service.interactive.*
import com.jackleow.presentation.service.interactive.model.ChatMessage
import com.jackleow.presentation.service.transcription.TranscriptionBroadcaster
import zio.*
import zio.cli.*
import zio.cli.HelpDoc.Span.text
import zio.http.Server

import java.nio.file.Path

object Main extends ZIOCliDefault:
  private val options: Options[(Path, Int)] =
    Options.file("html-path", Exists.Yes) ++
    Options.integer("port").map(_.toInt).withDefault(8973)
  private val command: Command[(Path, Int)] =
    Command("presentation-service", options, Args.none)
  override val cliApp: CliApp[Any, Throwable, (Path, Int)] = CliApp.make(
    name = "Presentation",
    version = "1.0",
    summary = text("Presentation Service Application"),
    command = command
  ):
    case (htmlPath: Path, port: Int) =>
      val httpServer: TaskLayer[Server] =
        Server
          .defaultWithPort(port)
          .tap: (env: ZEnvironment[Server]) =>
            ZIO.log(s"Server online at http://localhost:${env.get.port}/")
      Server
        .serve(App(htmlPath))
        .provide(
//          ZLayer.Debug.tree,
          Configuration.live,
          ZLayer(SubscriberCountingHub.make[ChatMessage, "chat"]),
          ZLayer(SubscriberCountingHub.make[ChatMessage, "rejected"]),
          LanguagePollCounter.live,
          WordCloudCounter.live,
          ModeratedTextCollector.live["question"],
          InteractiveService.live,
          TranscriptionBroadcaster.live,
          httpServer
        )
