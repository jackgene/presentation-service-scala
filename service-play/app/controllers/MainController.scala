package controllers

import actors.*
import actors.ChatMessageBroadcaster.ChatMessage
import actors.adapter.sourceBehavior
import akka.actor.ActorSystem
import akka.actor.typed.ActorRef
import akka.stream.scaladsl.{Flow, Sink}
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc.*

import scala.util.matching.Regex

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
final class MainController (
  chatMessages: ActorRef[ChatMessageBroadcaster.Command],
  rejectedMessages: ActorRef[ChatMessageBroadcaster.Command],
  languagePoll: ActorRef[SendersByTokenCounter.Command],
  wordCloud: ActorRef[SendersByTokenCounter.Command],
  chattiest: ActorRef[MessagesBySenderCounter.Command],
  questions: ActorRef[ModeratedTextCollector.Command],
  transcriptions: ActorRef[TranscriptionBroadcaster.Command],
  chatMessageKafkaProducer: ActorRef[ChatMessageKafkaProducer.Command],
  cc: ControllerComponents
)(using system: ActorSystem) extends AbstractController(cc) {
  private val RoutePattern: Regex = """(.*) to (Everyone|You)(?: \(Direct Message\))?""".r
  private val IgnoredRoutePattern: Regex = "You to .*".r

  def languagePollEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { (_: RequestHeader) =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { (webSocketClient: ActorRef[JsValue]) =>
        SendersByTokenCounter.JsonPublisher(webSocketClient, languagePoll)
      }
    )
  }

  def wordCloudEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { (_: RequestHeader) =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { (webSocketClient: ActorRef[JsValue]) =>
        // Produce ChatMessage in Kafka
        chatMessageKafkaProducer ! ChatMessageKafkaProducer.Activate(webSocketClient)
        SendersByTokenCounter.JsonPublisher(webSocketClient, wordCloud)
      }
    )
  }

  def chattiestEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { (_: RequestHeader) =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { (webSocketClient: ActorRef[JsValue]) =>
        MessagesBySenderCounter.JsonPublisher(webSocketClient, chattiest)
      }
    )
  }

  def questionEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { (_: RequestHeader) =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { (webSocketClient: ActorRef[JsValue]) =>
        ModeratedTextCollector.JsonPublisher(webSocketClient, questions)
      }
    )
  }

  def transcriptionEvent(): WebSocket = WebSocket.accept[JsValue, JsValue] { (_: RequestHeader) =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { (webSocketClient: ActorRef[JsValue]) =>
        TranscriptionBroadcaster.JsonPublisher(webSocketClient, transcriptions)
      }
    )
  }

  def moderationEvent(): WebSocket = WebSocket.accept[JsValue,JsValue] { (_: RequestHeader) =>
    Flow.fromSinkAndSource(
      Sink.ignore,
      ActorFlow.sourceBehavior { (webSocketClient: ActorRef[JsValue]) =>
        ChatMessageBroadcaster.JsonPublisher(webSocketClient, rejectedMessages)
      }
    )
  }

  def chat(route: String, text: String): Action[Unit] = Action(parse.empty) { (_: Request[Unit]) =>
    route match {
      case RoutePattern(sender, recipient) =>
        chatMessages ! ChatMessageBroadcaster.Command.Record(ChatMessage(sender, recipient, text))
        NoContent
      case IgnoredRoutePattern() => NoContent
      case sender: String =>
        // With larger Zoom meetings, the "route" is just the sender
        chatMessages ! ChatMessageBroadcaster.Command.Record(ChatMessage(sender, "Everyone", text))
        NoContent
    }
  }

  def reset(): Action[Unit] = Action(parse.empty) { (_: Request[Unit]) =>
    languagePoll ! SendersByTokenCounter.Command.Reset
    wordCloud ! SendersByTokenCounter.Command.Reset
    chattiest ! MessagesBySenderCounter.Command.Reset
    questions ! ModeratedTextCollector.Command.Reset
    NoContent
  }

  def transcription(text: String): Action[Unit] = Action(parse.empty) { (_: Request[Unit]) =>
    transcriptions ! TranscriptionBroadcaster.Command.NewTranscriptionText(text)
    NoContent
  }
}
