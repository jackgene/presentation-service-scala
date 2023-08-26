package controllers

import actors.*
import actors.adapter.*
import actors.tokenizing.{MappedKeywordsTokenizer, NormalizedWordsTokenizer}
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
  private val chatMessages: ActorRef[ChatMessageBroadcaster.Command] =
    system.spawn(ChatMessageBroadcaster(), "chat")
  private val rejectedMessages: ActorRef[ChatMessageBroadcaster.Command] =
    system.spawn(ChatMessageBroadcaster(), "rejected")
  private val languagePoll: ActorRef[SendersByTokenCounter.Command] =
    system.spawn(
      SendersByTokenCounter(
        extractTokens = MappedKeywordsTokenizer(
          cfg.get[Map[String, String]]("presentation.languagePoll.languageByKeyword")
        ),
        tokensPerSender = cfg.get[Int]("presentation.languagePoll.maxVotesPerPerson"),
        chatMessages, rejectedMessages
      ),
      "language-poll"
    )
  private val wordCloud: ActorRef[SendersByTokenCounter.Command] =
    system.spawn(
      SendersByTokenCounter(
        extractTokens = NormalizedWordsTokenizer(
          cfg.get[Seq[String]]("presentation.wordCloud.stopWords").toSet,
          cfg.get[Int]("presentation.wordCloud.minWordLength"),
          cfg.get[Int]("presentation.wordCloud.maxWordLength")
        ),
        tokensPerSender = cfg.get[Int]("presentation.wordCloud.maxWordsPerPerson"),
        chatMessages, rejectedMessages
      ),
      "word-cloud"
    )
  private val chattiest: ActorRef[MessagesBySenderCounter.Command] =
    system.spawn(
      MessagesBySenderCounter(chatMessages), "chattiest"
    )
  private val questions: ActorRef[ModeratedTextCollector.Command] =
    system.spawn(
      ModeratedTextCollector(chatMessages, rejectedMessages), "question"
    )
  private val transcriptions: ActorRef[TranscriptionBroadcaster.Command] =
    system.spawn(
      TranscriptionBroadcaster(), "transcriptions"
    )

  def languagePollEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { _: RequestHeader =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { webSocketClient: ActorRef[JsValue] =>
        SendersByTokenCounter.JsonPublisher(webSocketClient, languagePoll)
      }
    )
  }

  def wordCloudEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { _: RequestHeader =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { webSocketClient: ActorRef[JsValue] =>
        SendersByTokenCounter.JsonPublisher(webSocketClient, wordCloud)
      }
    )
  }

  def chattiestEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { _: RequestHeader =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { webSocketClient: ActorRef[JsValue] =>
        MessagesBySenderCounter.JsonPublisher(webSocketClient, chattiest)
      }
    )
  }

  def questionEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { _: RequestHeader =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { webSocketClient: ActorRef[JsValue] =>
        ModeratedTextCollector.JsonPublisher(webSocketClient, questions)
      }
    )
  }

  def transcriptionEvent(): WebSocket = WebSocket.accept[JsValue, JsValue] { _: RequestHeader =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { webSocketClient: ActorRef[JsValue] =>
        TranscriptionBroadcaster.JsonPublisher(webSocketClient, transcriptions)
      }
    )
  }

  def moderationEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { _: RequestHeader =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { webSocketClient: ActorRef[JsValue] =>
        ChatMessageBroadcaster.JsonPublisher(webSocketClient, rejectedMessages)
      }
    )
  }

  def chat(route: String, text: String): Action[Unit] = Action(parse.empty) { _: Request[Unit] =>
    route match {
      case RoutePattern(sender, recipient) =>
        chatMessages ! ChatMessageBroadcaster.Record(ChatMessage(sender, recipient, text))
        NoContent
      case IgnoredRoutePattern() => NoContent
      case _ => BadRequest
    }
  }

  def reset(): Action[Unit] = Action(parse.empty) { _: Request[Unit] =>
    languagePoll ! SendersByTokenCounter.Reset
    wordCloud ! SendersByTokenCounter.Reset
    chattiest ! MessagesBySenderCounter.Reset
    questions ! ModeratedTextCollector.Reset
    NoContent
  }

  def transcription(text: String): Action[Unit] = Action(parse.empty) { _: Request[Unit] =>
    transcriptions ! TranscriptionBroadcaster.NewTranscriptionText(text)
    NoContent
  }
}
