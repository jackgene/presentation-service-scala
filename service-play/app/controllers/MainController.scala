package controllers

import actors.*
import actors.adapter.*
import actors.tokenizing.{mappedKeywordsTokenizer, normalizedWordsTokenizer}
import akka.actor.ActorSystem
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter.*
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import model.ChatMessage
import play.api.Configuration
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc.*

import scala.util.matching.Regex

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
class MainController (cc: ControllerComponents, cfg: Configuration)
    (implicit system: ActorSystem, mat: Materializer)
    extends AbstractController(cc) {
  private val RoutePattern: Regex = """(.*) to (Everyone|Me)(?: \(Direct Message\))?""".r
  private val IgnoredRoutePattern: Regex = "Me to .*".r
  private val chatMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command] =
    system.spawn(ChatMessageBroadcaster(), "chat")
  private val rejectedMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command] =
    system.spawn(ChatMessageBroadcaster(), "rejected")
  private val languagePollCounter: ActorRef[SendersByTokenCounter.Command] =
    system.spawn(
      SendersByTokenCounter(
        extractTokens = mappedKeywordsTokenizer(
          cfg.get[Map[String, String]]("presentation.languagePoll.languageByKeyword")
        ),
        tokensPerSender = cfg.get[Int]("presentation.languagePoll.maxVotesPerPerson"),
        chatMessageBroadcaster, rejectedMessageBroadcaster
      ),
      "language-poll"
    )
  private val wordCloudCounter: ActorRef[SendersByTokenCounter.Command] =
    system.spawn(
      SendersByTokenCounter(
        extractTokens = normalizedWordsTokenizer(
          cfg.get[Seq[String]]("presentation.wordCloud.stopWords").toSet,
          cfg.get[Int]("presentation.wordCloud.minWordLength")
        ),
        tokensPerSender = cfg.get[Int]("presentation.wordCloud.maxWordsPerPerson"),
        chatMessageBroadcaster, rejectedMessageBroadcaster
      ),
      "word-cloud"
    )
  private val chattiestCounter: ActorRef[MessagesBySenderCounter.Command] =
    system.spawn(
      MessagesBySenderCounter(chatMessageBroadcaster), "chattiest"
    )
  private val questionBroadcaster: ActorRef[ApprovalRouter.Command] =
    system.spawn(
      ApprovalRouter(chatMessageBroadcaster, rejectedMessageBroadcaster), "question"
    )
  private val transcriptionBroadcaster: ActorRef[TranscriptionBroadcaster.Command] =
    system.spawn(
      TranscriptionBroadcaster(), "transcriptions"
    )

  def languagePollEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { _: RequestHeader =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { webSocketClient: ActorRef[JsValue] =>
        SendersByTokenCounter.JsonPublisher(webSocketClient, languagePollCounter)
      }
    )
  }

  def wordCloudEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { _: RequestHeader =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { webSocketClient: ActorRef[JsValue] =>
        SendersByTokenCounter.JsonPublisher(webSocketClient, wordCloudCounter)
      }
    )
  }

  def chattiestEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { _: RequestHeader =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { webSocketClient: ActorRef[JsValue] =>
        MessagesBySenderCounter.JsonPublisher(webSocketClient, chattiestCounter)
      }
    )
  }

  def questionEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { _: RequestHeader =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { webSocketClient: ActorRef[JsValue] =>
        ApprovalRouter.JsonPublisher(webSocketClient, questionBroadcaster)
      }
    )
  }

  def transcriptionEvent(): WebSocket = WebSocket.accept[JsValue, JsValue] { _: RequestHeader =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { webSocketClient: ActorRef[JsValue] =>
        TranscriptionBroadcaster.JsonPublisher(webSocketClient, transcriptionBroadcaster)
      }
    )
  }

  def moderationEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { _: RequestHeader =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { webSocketClient: ActorRef[JsValue] =>
        ChatMessageBroadcaster.JsonPublisher(webSocketClient, rejectedMessageBroadcaster)
      }
    )
  }

  def chat(route: String, text: String): Action[Unit] = Action(parse.empty) { _: Request[Unit] =>
    route match {
      case RoutePattern(sender, recipient) =>
        chatMessageBroadcaster ! ChatMessageBroadcaster.Record(ChatMessage(sender, recipient, text))
        NoContent
      case IgnoredRoutePattern() => NoContent
      case _ => BadRequest
    }
  }

  def reset(): Action[Unit] = Action(parse.empty) { _: Request[Unit] =>
    languagePollCounter ! SendersByTokenCounter.Reset
    wordCloudCounter ! SendersByTokenCounter.Reset
    chattiestCounter ! MessagesBySenderCounter.Reset
    questionBroadcaster ! ApprovalRouter.Reset
    NoContent
  }

  def transcription(text: String): Action[Unit] = Action(parse.empty) { _: Request[Unit] =>
    transcriptionBroadcaster ! TranscriptionBroadcaster.NewTranscriptionText(text)
    NoContent
  }
}
