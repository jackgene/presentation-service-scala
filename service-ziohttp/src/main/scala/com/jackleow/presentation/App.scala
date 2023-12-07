package com.jackleow.presentation

import zio.*
import zio.cli.*
import zio.cli.HelpDoc.Span.text

import java.nio.file.Path

object App extends ZIOCliDefault {
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
  ) {
    case (htmlPath: Path, port: Int) => Server.make(htmlPath, port)
  }
}
