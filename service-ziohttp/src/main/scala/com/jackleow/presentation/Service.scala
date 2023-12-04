package com.jackleow.presentation

import zio.*
import zio.http.*
import zio.http.ChannelEvent.UserEvent.HandshakeComplete
import zio.http.ChannelEvent.{Read, UserEventTriggered}
import zio.http.codec.PathCodec.empty
import zio.stream.ZStream

import java.nio.file

object Service {
  def apply(htmlPath: file.Path, port: Int): Service = new Service(htmlPath, port)
}
class Service(htmlPath: file.Path, port: Int) {
  private val contentTypeHtml: Headers = Headers(
    Header.ContentType(MediaType.text.html,
      charset = Option(Charsets.Utf8))
  )
  private val socketApp: WebSocketApp[Any] =
    Handler.webSocket { (channel: WebSocketChannel) =>
      channel.receiveAll {
        case UserEventTriggered(HandshakeComplete) =>
          channel.send(Read(WebSocketFrame.Text("FOCK")))
        case Read(WebSocketFrame.Text("FOO")) =>
          channel.send(Read(WebSocketFrame.Text("BAR")))
        case Read(WebSocketFrame.Text("BAR")) =>
          channel.send(Read(WebSocketFrame.Text("FOO")))
        case Read(WebSocketFrame.Text(text)) =>
          channel.send(Read(WebSocketFrame.Text(text))).repeatN(10)
        case other =>
          println(s"test?: $other")
          ZIO.unit
      }
    }

  private val app: HttpApp[Any] =
    Routes(
      // Deck
      Method.GET / empty ->
        handler(
          Response(
            headers = contentTypeHtml,
            body = Body.fromStream(ZStream.fromPath(htmlPath))
          )
        ),

      // Modeeration
      Method.GET / "moderator" ->
        handler(
          Response(
            headers = contentTypeHtml,
            body = Body.fromStream(
              ZStream.fromResource("html/moderator.html")
            )
          )
        ),
      Method.GET / "reset" ->
        handler(Response.status(Status.NoContent)),

      // Transcription
      Method.GET / "transcriber" ->
        handler(
          Response(
            headers = contentTypeHtml,
            body = Body.fromStream(
              ZStream.fromResource("html/transcriber.html")
            )
          )
        ),

      Method.GET / "subscriptions" ->
        handler(socketApp.toResponse),
    ).toHttpApp

  def start(): ZIO[Any, Throwable, Nothing] =
    Server.serve(app).provide(Server.defaultWithPort(port))
}
