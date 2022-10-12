package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import play.api.libs.json.{Json, Writes}

object TranscriptionActor {
  // Incoming messages
  case class NewTranscriptionText(text: String)
  case class Register(listener: ActorRef)

  // Outgoing messages
  case class Transcription(text: String)

  // JSON
  private implicit val TranscriptionWrites: Writes[Transcription] =
    (transcription: Transcription) => Json.obj("transcriptionText" -> transcription.text)

  def props: Props = Props(new TranscriptionActor())

  // WebSocket actor
  object WebSocketActor {
    def props(webSocketClient: ActorRef, transcriptions: ActorRef): Props =
      Props(new WebSocketActor(webSocketClient, transcriptions))
  }
  class WebSocketActor(webSocketClient: ActorRef, transcriptions: ActorRef)
      extends Actor with ActorLogging {
    transcriptions ! TranscriptionActor.Register(listener = self)

    override def receive: Receive = {
      case transcriptions: TranscriptionActor.Transcription =>
        webSocketClient ! Json.toJson(transcriptions)
    }
  }
}
private class TranscriptionActor extends Actor with ActorLogging {
  import TranscriptionActor._

  private def running(text: String, listeners: Set[ActorRef]): Receive = {
    case NewTranscriptionText(text: String) =>
      log.info(s"Received transcription text: ${text}")
      for (listener: ActorRef <- listeners) {
        listener ! Transcription(text)
      }
      context.become(running(text, listeners))

    case Register(listener: ActorRef) =>
      listener ! Transcription(text)
      context.watch(listener)
      context.become(running(text, listeners + listener))
      log.info(s"+1 transcription listener (=${listeners.size + 1})")

    case Terminated(listener: ActorRef) if listeners.contains(listener) =>
      context.become(running(text, listeners - listener))
      log.info(s"-1 transcription listener (=${listeners.size - 1})")
  }

  override def receive: Receive = running("", Set())
}
