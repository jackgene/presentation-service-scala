package com.jackleow.presentation

import com.jackleow.presentation.service.interactive.ChatMessageBroadcaster.ChatMessage
import com.jackleow.presentation.service.interactive.InteractiveService
import com.jackleow.presentation.service.transcription.TranscriptionBroadcaster
import com.jackleow.zio.http.noContent
import zio.*
import zio.http.*
import zio.http.ChannelEvent.UserEvent.HandshakeComplete
import zio.http.ChannelEvent.UserEventTriggered
import zio.http.codec.PathCodec.empty
import zio.json.EncoderOps
import zio.stream.ZStream

import java.nio.file
import scala.util.matching.Regex

object PresentationApp {
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
      Method.GET / "event" / "transcription" -> handler:
        Handler
          .webSocket: (channel: WebSocketChannel) =>
            channel.receiveAll:
              case UserEventTriggered(HandshakeComplete) =>
                ZIO.serviceWithZIO[TranscriptionBroadcaster]:
                  (broadcaster: TranscriptionBroadcaster) =>
                    broadcaster
                      .transcriptions
                      .map(_.toJson)
                      .map(WebSocketFrame.text)
                      .map(ChannelEvent.read)
                      .runForeach(channel.send)
                      .fork

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
      Method.POST / "chat" -> handler: (req: Request) =>
        (req.url.queryParams.get("route"), req.url.queryParams.get("text")) match
          case (Some(route: String), Some(text: String)) =>
            route match
              case RoutePattern(sender, recipient) =>
                ZIO.serviceWithZIO[InteractiveService]:
                  (service: InteractiveService) =>
                    service
                      .receiveChatMessage(ChatMessage(sender, recipient, text))
                      .tap: (succeeded: Boolean) =>
                        ZIO.log(if succeeded then "SUCCESS" else "FAILED")
                      .map(_ => Response.noContent)
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
        Response.noContent
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
            ZIO.serviceWithZIO[TranscriptionBroadcaster]:
              (broadcaster: TranscriptionBroadcaster) =>
                broadcaster
                  .broadcast(text)
                  .map(_ => Response.noContent)
          case None =>
            ZIO.succeed(Response.badRequest("Missing parameter: text"))
      ,
    ).toHttpApp
}
