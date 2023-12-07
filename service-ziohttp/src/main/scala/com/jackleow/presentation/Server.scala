package com.jackleow.presentation

import zio.*
import zio.http.*
import zio.http.ChannelEvent.UserEvent.HandshakeComplete
import zio.http.ChannelEvent.{Read, UserEventTriggered}
import zio.http.codec.PathCodec.empty
import zio.stream.ZStream

import java.nio.file

object Server {
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
          ZIO.unit
      }
    }

  private def app(htmlPath: file.Path): HttpApp[Any] =
    Routes(
      // Deck
      Method.GET / empty ->
        handler(
          Response(
            headers = contentTypeHtml,
            body = Body.fromStream(ZStream.fromPath(htmlPath))
          )
        )
      ,

      // Modeeration
      Method.GET / "moderator" ->
        handler(
          Response(
            headers = contentTypeHtml,
            body = Body.fromStream(
              ZStream.fromResource("html/moderator.html")
            )
          )
        )
      ,
      Method.GET / "reset" ->
        handler(Response.status(Status.NoContent))
      ,

      // Transcription
      Method.GET / "transcriber" ->
        handler(
          Response(
            headers = contentTypeHtml,
            body = Body.fromStream(
              ZStream.fromResource("html/transcriber.html")
            )
          )
        )
      ,

      Method.GET / "subscriptions" ->
        handler(socketApp.toResponse)
      ,
    ).toHttpApp

  def make(htmlPath: file.Path, port: Int): ZIO[Any, Throwable, Nothing] =
    http.Server
      .serve(app(htmlPath))
      .provide(http.Server.defaultWithPort(port))
}
