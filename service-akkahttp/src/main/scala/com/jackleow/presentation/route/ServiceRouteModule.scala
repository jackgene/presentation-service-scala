package com.jackleow.presentation.route

import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.{Directives, Route}
import com.jackleow.presentation.infrastructure.AkkaModule
import com.jackleow.presentation.service.interactive.InteractiveModule
import com.jackleow.presentation.service.interactive.InteractiveService.ChatMessage
import com.jackleow.presentation.service.transcription.TranscriptionModule
import spray.json.*

import java.io.File
import scala.util.{Failure, Success}
import scala.util.matching.Regex

trait ServiceRouteModule extends RouteModule {
  this: AkkaModule & InteractiveModule & TranscriptionModule =>

  private val RoutePattern: Regex = """(.*) to (Everyone|You)(?: \(Direct Message\))?""".r
  private val IgnoredRoutePattern: Regex = "You to .*".r

  override def routes(htmlFile: File): Route = {
    redirectToNoTrailingSlashIfPresent(StatusCodes.Found) {
      // Deck
      pathSingleSlash {
        getFromFile(htmlFile, ContentTypes.`text/html(UTF-8)`)
      }
      ~
      path("event" / "question") {
        handleWebSocketMessages(
          interactiveService.questions.
            map(_.toJson.compactPrint).
            map(TextMessage(_))
        )
      }
      ~
      path("event" / "transcription") {
        handleWebSocketMessages(
          transcriptionService.transcriptions.
            map(_.toJson.compactPrint).
            map(TextMessage(_))
        )
      }
      ~
      path("moderator") {
        getFromResource("html/moderator.html")
      }
      ~
      path("moderator" / "event") {
        handleWebSocketMessages(
          interactiveService.rejectedMessages.
            map(_.toJson.compactPrint).
            map(TextMessage(_))
        )
      }
      ~
      // Moderation
      path("chat") {
        post {
          parameters("route", "text") { (route: String, text: String) =>
            route match {
              case RoutePattern(sender, recipient) =>
                interactiveService.receiveChatMessage(ChatMessage(sender, recipient, text))
                complete(StatusCodes.NoContent, HttpEntity.Empty)
              case IgnoredRoutePattern() =>
                complete(StatusCodes.NoContent, HttpEntity.Empty)
              case _ =>
                complete(StatusCodes.BadRequest, HttpEntity.Empty)
            }
          }
        }
      }
      ~
      // Transcription
      path("transcriber") {
        getFromResource("html/transcriber.html")
      }
      ~
      path("transcription") {
        post {
          parameters("text") { (text: String) =>
            onComplete(transcriptionService.receiveTranscription(text)) {
              case Success(_) => complete(StatusCodes.NoContent, HttpEntity.Empty)
              case Failure(_) => complete(StatusCodes.TooManyRequests, HttpEntity.Empty)
            }
          }
        }
      }
    }
  }
}
