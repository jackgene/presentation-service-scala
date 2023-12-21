package com.jackleow.presentation

import kyo.*
import kyo.concurrent.atomics.*
import kyo.consoles.*
import kyo.direct.*
import kyo.ios.*
import kyo.logs.*
import kyo.routes.*
import kyo.server.*
import scopt.OParser
import sttp.tapir.*
import sttp.tapir.server.netty.NettyConfig

import java.io.File
import scala.io.Source

object Main extends KyoApp:
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

  run:
    OParser.parse(parser, args, Arguments()) match
      case None => System.exit(1)

      case Some(Arguments(htmlFile: File, _)) if !htmlFile.exists() || htmlFile.isDirectory =>
        defer:
          await(Consoles.printlnErr(s"Deck file not found: ${htmlFile.getAbsolutePath}"))
        System.exit(1)

      case Some(args: Arguments) =>
        defer:
          val indexContent: String =
            await(IOs(Source.fromFile(args.htmlFile)).map(_.mkString))
          val counter: AtomicInt = await(Atomics.initInt(0))
          val binding: NettyKyoServerBinding = await:
            Routes.run(NettyKyoServer(NettyConfig.defaultNoStreaming.port(args.port)))(
              Routes.add(
                endpoint.get.in("").out(htmlBodyUtf8)
              )(_ => indexContent)

              andThen

              Routes.add(
                endpoint.get.in("hello" / path[String]).out(stringBody)
              )((name: String) => s"Hello, $name!")

              andThen

              Routes.add(
                endpoint.get.in("increment").out(stringBody)
              )(_ => counter.incrementAndGet.map(_.toString))

              andThen

              Routes.add(
                endpoint.get.in("decrement").out(stringBody)
              )(_ => counter.decrementAndGet.map(_.toString))
            )
          await(Logs.info(s"Server listening on http://${binding.hostName}:${binding.port}"))
          ()
// Alternatively:
//    for
//      counter: AtomicInt <- Atomics.initInt(0)
//      _ <- Consoles.println("Server starting...")
//      _ <- Routes.run(NettyKyoServer(NettyConfig.defaultNoStreaming.port(8973)))(
//          Routes.add(
//            endpoint.get.in("hello" / path[String]).out(stringBody)
//          )((name: String) => s"Hello, $name!")
//
//          andThen
//
//          Routes.add(
//            endpoint.get.in("increment").out(stringBody)
//          )(_ => counter.incrementAndGet.map(_.toString))
//
//          andThen
//
//          Routes.add(
//            endpoint.get.in("decrement").out(stringBody)
//          )(_ => counter.decrementAndGet.map(_.toString))
//      )
//    yield ()
