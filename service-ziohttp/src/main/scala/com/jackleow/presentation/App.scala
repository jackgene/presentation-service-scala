package com.jackleow.presentation

import com.jackleow.presentation.service.interactive.InteractiveService
import com.jackleow.presentation.service.interactive.model.*
import com.jackleow.presentation.service.transcription.TranscriptionBroadcaster
import com.jackleow.presentation.service.transcription.model.Transcription
import com.jackleow.zio.http.noContent
import com.jackleow.zio.stream.mapToJsonWebSocketFrames
import zio.*
import zio.http.*
import zio.http.ChannelEvent.UserEvent.HandshakeComplete
import zio.http.ChannelEvent.UserEventTriggered
import zio.http.codec.PathCodec.empty
import zio.stream.*

import java.nio.file
import scala.util.matching.Regex

object App:
  private val contentTypeHtml: Headers = Headers(
    Header.ContentType(MediaType.text.html, charset = Option(Charsets.Utf8))
  )
  private val RoutePattern: Regex = """(.*) to (Everyone|You)(?: \(Direct Message\))?""".r
  private val IgnoredRoutePattern: Regex = "You to .*".r

  def apply(htmlPath: file.Path): HttpApp[InteractiveService & TranscriptionBroadcaster] =
    Routes(
      // Deck
      Method.GET / empty -> handler:
        Response(
          headers = contentTypeHtml,
          body = Body.fromStream(ZStream.fromPath(htmlPath))
        )
      ,
      Method.GET / "event" / "question" -> handler:
        Handler
          .webSocket: (channel: WebSocketChannel) =>
            channel.receiveAll:
              case UserEventTriggered(HandshakeComplete) =>
                for
                  questions: UStream[ChatMessages] <- InteractiveService.questions
                  _ <- questions
                    .mapToJsonWebSocketFrames
                    .runForeach(channel.send)
                    .fork
                yield ()

              case _ => ZIO.unit
          .toResponse
      ,
      Method.GET / "event" / "language-poll" -> handler:
        Handler
          .webSocket: (channel: WebSocketChannel) =>
            channel.receiveAll:
              case UserEventTriggered(HandshakeComplete) =>
                for
                  counts: UStream[Counts] <- InteractiveService.languagePoll
                  _ <- counts
                    .mapToJsonWebSocketFrames
                    .runForeach(channel.send)
                    .fork
                yield ()

              case _ => ZIO.unit
          .toResponse
      ,
      Method.GET / "event" / "word-cloud" -> handler:
        Handler
          .webSocket: (channel: WebSocketChannel) =>
            channel.receiveAll:
              case UserEventTriggered(HandshakeComplete) =>
                for
                  counts: UStream[Counts] <- InteractiveService.wordCloud
                  _ <- counts
                    .mapToJsonWebSocketFrames
                    .runForeach(channel.send)
                    .fork
                yield ()

              case _ => ZIO.unit
          .toResponse
      ,
      Method.GET / "event" / "transcription" -> handler:
        Handler
          .webSocket: (channel: WebSocketChannel) =>
            channel.receiveAll:
              case UserEventTriggered(HandshakeComplete) =>
                for
                  transcriptions: UStream[Transcription] <-
                    TranscriptionBroadcaster.transcriptions
                  _ <- transcriptions
                    .mapToJsonWebSocketFrames
                    .runForeach(channel.send)
                    .fork
                yield ()

              case _ => ZIO.unit
          .toResponse
      ,

      // Moderation
      Method.GET / "moderator" -> handler:
        Response(
          headers = contentTypeHtml,
          body = Body.fromStream(
            ZStream.fromResource("html/moderator.html")
          )
        )
      ,
      Method.GET / "moderator" / "event" -> handler:
        Handler
          .webSocket: (channel: WebSocketChannel) =>
            channel.receiveAll:
              case UserEventTriggered(HandshakeComplete) =>
                for
                  rejectedMessages: UStream[ChatMessage] <-
                    InteractiveService.rejectedMessages
                  _ <- rejectedMessages
                    .mapToJsonWebSocketFrames
                    .runForeach(channel.send)
                    .fork
                yield ()

              case _ => ZIO.unit
          .toResponse
      ,
      Method.POST / "chat" -> handler: (req: Request) =>
        (req.url.queryParams.get("route"), req.url.queryParams.get("text")) match
          case (Some(route: String), Some(text: String)) =>
            route match
              case RoutePattern(sender, recipient) =>
                for
                  _ <- InteractiveService.receiveChatMessage(
                    ChatMessage(sender, recipient, text)
                  )
                yield Response.noContent
              case IgnoredRoutePattern() =>
                ZIO.succeed(Response.noContent)
              case _ =>
                ZIO.succeed(Response.badRequest)
          case (Some(_), None) =>
            ZIO.succeed(Response.badRequest("Missing parameter: text"))
          case (None, Some(_)) =>
            ZIO.succeed(Response.badRequest("Missing parameter: route"))
          case (None, None) =>
            ZIO.succeed(Response.badRequest("Missing parameters: route, text"))
      ,
      Method.GET / "reset" -> handler:
        for
          _ <- InteractiveService.reset()
        yield Response.noContent
      ,

      // Transcription
      Method.GET / "transcriber" -> handler:
        Response(
          headers = contentTypeHtml,
          body = Body.fromStream(
            ZStream.fromResource("html/transcriber.html")
          )
        )
      ,

      Method.POST / "transcription" -> handler: (req: Request) =>
        req.url.queryParams.get("text") match
          case Some(text: String) =>
            TranscriptionBroadcaster
              .broadcast(text)
              .map(_ => Response.noContent)
          case None =>
            ZIO.succeed(Response.badRequest("Missing parameter: text"))
      ,
    ).toHttpApp
