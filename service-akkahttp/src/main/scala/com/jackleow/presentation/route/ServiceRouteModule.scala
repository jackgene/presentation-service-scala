package com.jackleow.presentation.route

import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.{Directives, Route, StandardRoute}
import com.jackleow.akka.stream.scaladsl.{sample, toJsonWebSocketMessage}
import com.jackleow.presentation.infrastructure.AkkaModule
import com.jackleow.presentation.service.interactive.InteractiveModule
import com.jackleow.presentation.service.interactive.InteractiveService.ChatMessage
import com.jackleow.presentation.service.transcription.TranscriptionModule

import java.io.File
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex

object ServiceRouteModule:
  private val completeWith204or429: (Try[Any]) => StandardRoute =
    case Success(_) => complete(StatusCodes.NoContent, HttpEntity.Empty)
    case Failure(_) => complete(StatusCodes.TooManyRequests, HttpEntity.Empty)

trait ServiceRouteModule extends RouteModule:
  this: AkkaModule & InteractiveModule & TranscriptionModule =>

  import ServiceRouteModule.*

  private val RoutePattern: Regex = """(.*) to (Everyone|You)(?: \(Direct Message\))?""".r
  private val IgnoredRoutePattern: Regex = "You to .*".r

  override def routes(htmlFile: File): Route =
    redirectToNoTrailingSlashIfPresent(StatusCodes.Found):
      // Deck
      pathSingleSlash:
        getFromFile(htmlFile, ContentTypes.`text/html(UTF-8)`)
      ~
      path("event" / "language-poll"):
        handleWebSocketMessages(
          interactiveService.languagePoll
            .sample(10, 1.second)
            .toJsonWebSocketMessage
        )
      ~
      path("event" / "word-cloud"):
        handleWebSocketMessages(
          interactiveService.wordCloud
            .sample(10, 1.second)
            .toJsonWebSocketMessage
        )
      ~
      path("event" / "question"):
        handleWebSocketMessages(
          interactiveService.questions
            .toJsonWebSocketMessage
        )
      ~
      path("event" / "transcription"):
        handleWebSocketMessages(
          transcriptionService.transcriptions
            .toJsonWebSocketMessage
        )
      ~
      path("moderator"):
        getFromResource("html/moderator.html")
      ~
      path("moderator" / "event"):
        handleWebSocketMessages(
          interactiveService.rejectedMessages
            .toJsonWebSocketMessage
        )
      ~
      // Moderation
      path("chat"):
        post:
          parameters("route", "text"):
            (route: String, text: String) =>
              route match
                case RoutePattern(sender, recipient) =>
                  onComplete(
                    interactiveService.receiveChatMessage(ChatMessage(sender, recipient, text))
                  )(completeWith204or429)
                case IgnoredRoutePattern() =>
                  complete(StatusCodes.NoContent, HttpEntity.Empty)
                case _ =>
                  complete(StatusCodes.BadRequest, HttpEntity.Empty)
      ~
      path("reset"):
        get:
          onComplete(interactiveService.reset())(completeWith204or429)
      ~
      // Transcription
      path("transcriber"):
        getFromResource("html/transcriber.html")
      ~
      path("transcription"):
        post:
          parameters("text"):
            (text: String) =>
              onComplete(
                transcriptionService.receiveTranscription(text)
              )(completeWith204or429)
