package com.jackleow.presentation

import com.jackleow.presentation.service.interactive
import com.jackleow.presentation.service.interactive.{ChatMessageBroadcaster, InteractiveService}
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
      Server
        .serve(PresentationApp(htmlPath))
        .provide(
          Server
            .defaultWithPort(port)
            .tap: (env: ZEnvironment[Server]) =>
              ZIO.log(s"Server online at http://localhost:${env.get.port}/"),
          ZLayer.fromZIO(
            for
              chat: ChatMessageBroadcaster <- ChatMessageBroadcaster.make("chat")
              rejected: ChatMessageBroadcaster <- ChatMessageBroadcaster.make("rejected")
              service: interactive.InteractiveService <- InteractiveService.make(chat)
            yield service
          ),
          ZLayer.fromZIO(TranscriptionBroadcaster.make),
        )
