package com.jackleow.presentation

import com.jackleow.presentation.service.interactive.TranscriptionBroadcaster
import zio.*
import zio.http.*
import zio.http.ChannelEvent.UserEvent.HandshakeComplete
import zio.http.ChannelEvent.UserEventTriggered
import zio.http.codec.PathCodec.empty
import zio.json.EncoderOps
import zio.stream.ZStream

import java.nio.file

object Server {
  private val contentTypeHtml: Headers = Headers(
    Header.ContentType(MediaType.text.html,
      charset = Option(Charsets.Utf8))
  )

  private def app(htmlPath: file.Path): HttpApp[TranscriptionBroadcaster] =
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

      // Modeeration
      Method.GET / "moderator" -> handler:
        Response(
          headers = contentTypeHtml,
          body = Body.fromStream(
            ZStream.fromResource("html/moderator.html")
          )
        )
      ,
      Method.GET / "reset" -> handler:
        Response.status(Status.NoContent)
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
                  .map(_ => Response.status(Status.NoContent))
          case None =>
            ZIO.succeed(Response.badRequest("Missing parameter: text"))
      ,
    ).toHttpApp

  def make(htmlPath: file.Path, port: Int): ZIO[Any, Throwable, Nothing] =
    http.Server
      .serve(app(htmlPath))
      .provide(
        http.Server.defaultWithPort(port),
        ZLayer.fromZIO(TranscriptionBroadcaster.make),
      )
}
