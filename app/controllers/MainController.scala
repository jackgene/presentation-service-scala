package controllers

import actors._
import actors.tokenizing.{mappedKeywordsTokenizer, normalizedWordsTokenizer}
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import javax.inject._
import model.ChatMessage
import play.api.Configuration
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class MainController @Inject() (cc: ControllerComponents, cfg: Configuration)
    (implicit system: ActorSystem, mat: Materializer)
    extends AbstractController(cc) {
  private val RoutePattern = """(.*) to (Everyone|Me)(?: \(Direct Message\))?""".r
  private val IgnoredRoutePattern = "Me to .*".r
  private val chatMsgActor: ActorRef =
    system.actorOf(ChatMessageActor.props, "chat")
  private val rejectedMsgActor: ActorRef =
    system.actorOf(ChatMessageActor.props, "rejected")
  private val languagePollActor: ActorRef =
    system.actorOf(
      SendersByTokenCounterActor.props(
        mappedKeywordsTokenizer(
          cfg.get[Map[String, String]]("presentation.languagePoll.languageByKeyword")
        ),
        chatMsgActor, rejectedMsgActor
      ),
      "language-poll"
    )
  private val wordCloudActor: ActorRef =
    system.actorOf(
      SendersByTokenCounterActor.props(
        normalizedWordsTokenizer(
          cfg.get[Seq[String]]("presentation.wordCloud.stopWords").toSet,
          cfg.get[Int]("presentation.wordCloud.minWordLength")
        ),
        chatMsgActor, rejectedMsgActor
      ),
      "word-cloud"
    )
  private val questionActor: ActorRef =
    system.actorOf(
      ApprovalRouterActor.props(chatMsgActor, rejectedMsgActor),
      "question"
    )
  private val transcriptionActor: ActorRef =
    system.actorOf(TranscriptionActor.props, "transcriptions")

  def languagePollEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { _: RequestHeader =>
    ActorFlow.actorRef { webSocketClient: ActorRef =>
      SendersByTokenCounterActor.WebSocketActor.props(webSocketClient, languagePollActor)
    }
  }

  def wordCloudEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { _: RequestHeader =>
    ActorFlow.actorRef { webSocketClient: ActorRef =>
      SendersByTokenCounterActor.WebSocketActor.props(webSocketClient, wordCloudActor)
    }
  }

  def questionEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { _: RequestHeader =>
    ActorFlow.actorRef { webSocketClient: ActorRef =>
      ApprovalRouterActor.WebSocketActor.props(webSocketClient, questionActor)
    }
  }

  def transcriptionEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { _: RequestHeader =>
    ActorFlow.actorRef { webSocketClient: ActorRef =>
      TranscriptionActor.WebSocketActor.props(webSocketClient, transcriptionActor)
    }
  }

  def moderationEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { _: RequestHeader =>
    ActorFlow.actorRef { webSocketClient: ActorRef =>
      ModerationWebSocketActor.props(webSocketClient, rejectedMsgActor)
    }
  }

  def chat(route: String, text: String): Action[Unit] = Action(parse.empty) { _: Request[Unit] =>
    route match {
      case RoutePattern(sender, recipient) =>
        chatMsgActor ! ChatMessageActor.New(ChatMessage(sender, recipient, text))
        NoContent
      case IgnoredRoutePattern() => NoContent
      case _ => BadRequest
    }
  }

  def reset(): Action[Unit] = Action(parse.empty) { _: Request[Unit] =>
    languagePollActor ! SendersByTokenCounterActor.Reset
    wordCloudActor ! SendersByTokenCounterActor.Reset
    questionActor ! ApprovalRouterActor.Reset
    NoContent
  }

  def transcription(text: String): Action[Unit] = Action(parse.empty) { _: Request[Unit] =>
    transcriptionActor ! TranscriptionActor.NewTranscriptionText(text)
    NoContent
  }
}
