package com.jackleow.presentation

import com.jackleow.presentation.config.Configuration
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderFailures
import scopt.OParser

import java.io.File

private case class Arguments(
  htmlFile: File = new File(""),
  port: Int = 8973
)
private val builder = OParser.builder[Arguments]
private val parser =
  import builder.*
  OParser.sequence(
    programName("presentation-service"),
    head("Presentation Service in Akka HTTP"),
    opt[File]("html-path")
      .required()
      .action:
        (file: File, args: Arguments) =>
          args.copy(htmlFile = file)
      .text("Presentation HTML file path"),
    opt[Int]('p', "port")
      .action:
        (port: Int, args: Arguments) =>
          args.copy(port = port)
      .text("HTTP server port"),
  )

@main def startServer(rawArgs: String*): Unit =
  (
    OParser.parse(parser, rawArgs, Arguments()),
    ConfigSource.default.load[Configuration]
  ) match
    case (None, _) =>
      System.exit(1)

    case (Some(Arguments(htmlFile: File, _)), _)
        if !htmlFile.exists() || htmlFile.isDirectory =>
      Console.err.println(s"Deck file not found: ${htmlFile.getAbsolutePath}")
      System.exit(1)

    case (_, Left(failures: ConfigReaderFailures)) =>
      Console.err.println(
        s"Unable to load configuration:\n${failures.prettyPrint(1)}"
      )
      System.exit(1)

    case (Some(args: Arguments), Right(configuration: Configuration)) =>
      new App(configuration, args.htmlFile, args.port)
      ()
