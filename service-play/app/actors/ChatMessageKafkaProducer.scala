package actors

import actors.ChatMessageBroadcaster.ChatMessage
import akka.Done
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.{BoundedSourceQueue, Materializer, QueueOfferResult}
import akka.stream.scaladsl.{Sink, Source}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import play.api.libs.json.Json

import scala.concurrent.Future

/**
 * Actor that collects chat messages and produces Kafka records from them
 */
object ChatMessageKafkaProducer {
  sealed trait Command
  final case class Activate(sender: ActorRef[Nothing]) extends Command
  private final case class Passivate(sender: ActorRef[Nothing]) extends Command
  final case class Record(chatMessage: ChatMessage) extends Command

  def apply(
    chatMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command],
    kafkaBootstrapServers: String, kafkaTopicName: String,
  )(implicit mat: Materializer): Behavior[Command] = Behaviors.setup { ctx =>
    val producerSettings: ProducerSettings[String, String] = ProducerSettings(
      ctx.system, new StringSerializer, new StringSerializer
    ).withBootstrapServers(kafkaBootstrapServers)
    val kafkaProducer: Sink[ProducerRecord[String, String], Future[Done]] =
      Producer.plainSink(producerSettings)

    new ChatMessageKafkaProducer(
      chatMessageBroadcaster, kafkaProducer, kafkaTopicName,
      ctx.messageAdapter[ChatMessageBroadcaster.Event] {
        case ChatMessageBroadcaster.New(chatMessage: ChatMessage) => Record(chatMessage)
      }
    ).initial
  }
}
private class ChatMessageKafkaProducer(
  chatMessageBroadcaster: ActorRef[ChatMessageBroadcaster.Command],
  kafkaProducer: Sink[ProducerRecord[String, String], Future[Done]], kafkaTopicName: String,
  adapter: ActorRef[ChatMessageBroadcaster.Event]
)(implicit mat: Materializer) {
  import ChatMessageKafkaProducer.*

  private val paused: Behavior[Command] = Behaviors.receive { (ctx: ActorContext[Command], cmd: Command) =>
    cmd match {
      case Activate(sender: ActorRef[Nothing]) =>
        ctx.log.info(s"+1 ${ctx.self.path.name} activation (=1)")
        ctx.watchWith(sender, Passivate(sender))
        chatMessageBroadcaster ! ChatMessageBroadcaster.Subscribe(adapter)
        running(
          Set(sender),
          kafkaProducer.runWith(
            Source.queue[ChatMessage](1).
              map { msg: ChatMessage =>
                new ProducerRecord[String, String](
                  kafkaTopicName, msg.sender, Json.toJson(msg).toString()
                )
              }
          )
        )

      // These are not expected during paused state
      case Record(chatMessage: ChatMessage) =>
        ctx.log.warn(s"received unexpected record in paused state - $chatMessage")
        Behaviors.unhandled

      case Passivate(sender: ActorRef[Nothing]) =>
        ctx.log.warn(s"received unexpected activation decrement in paused state - ${sender.path}")
        Behaviors.unhandled
    }
  }

  private def running(
    activations: Set[ActorRef[Nothing]], kafkaQueue: BoundedSourceQueue[ChatMessage]
  ): Behavior[Command] = Behaviors.receive { (ctx: ActorContext[Command], cmd: Command) =>
    cmd match {
      case Record(chatMessage: ChatMessage) =>
        val res: QueueOfferResult = kafkaQueue.offer(chatMessage)
        if (res.isEnqueued)
          ctx.log.info(s"Enqueued chat message: $chatMessage")
        else
          ctx.log.warn(s"Failed to enqueue chat message $chatMessage")
        Behaviors.same

      case Activate(sender: ActorRef[Nothing]) if !activations.contains(sender) =>
        ctx.log.info(s"+1 ${ctx.self.path.name} activation (=${activations.size + 1})")
        ctx.watchWith(sender, Passivate(sender))
        running(activations + sender, kafkaQueue)

      case Activate(sender: ActorRef[Nothing]) =>
        ctx.log.warn(s"attempted to increment activation from existing ${ctx.self.path.name} sender - ${sender.path}")
        Behaviors.unhandled

      case Passivate(sender: ActorRef[Nothing]) if activations.contains(sender) =>
        ctx.log.info(s"-1 ${ctx.self.path.name} activation (=${activations.size - 1})")
        ctx.unwatch(sender)
        val remainingActivations: Set[ActorRef[Nothing]] = activations - sender
        if (remainingActivations.nonEmpty) {
          running(remainingActivations, kafkaQueue)
        } else {
          chatMessageBroadcaster ! ChatMessageBroadcaster.Unsubscribe(adapter)
          kafkaQueue.complete()
          paused
        }

      case Passivate(sender: ActorRef[Nothing]) =>
        ctx.log.warn(s"attempted to decrement activation from unknown ${ctx.self.path.name} sender - ${sender.path}")
        Behaviors.unhandled
    }
  }

  val initial: Behavior[Command] = paused
}
