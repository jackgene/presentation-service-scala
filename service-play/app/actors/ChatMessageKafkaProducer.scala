package actors

import actors.ChatMessageBroadcaster.ChatMessage
import akka.Done
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.Materializer
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
  final case class Subscribe(subscriber: ActorRef[Nothing]) extends Command
  final case class Unsubscribe(subscriber: ActorRef[Nothing]) extends Command
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
      case Subscribe(subscriber: ActorRef[Nothing]) =>
        ctx.log.info(s"+1 ${ctx.self.path.name} subscriber (=1)")
        ctx.watchWith(subscriber, Unsubscribe(subscriber))
        chatMessageBroadcaster ! ChatMessageBroadcaster.Subscribe(adapter)
        running(Set(subscriber))

      // These are not expected during paused state
      case Record(chatMessage: ChatMessage) =>
        ctx.log.warn(s"received unexpected record in paused state - $chatMessage")
        Behaviors.unhandled

      case Unsubscribe(subscriber: ActorRef[Nothing]) =>
        ctx.log.warn(s"received unexpected unsubscription in paused state - ${subscriber.path}")
        Behaviors.unhandled
    }
  }

  private def running(
    subscribers: Set[ActorRef[Nothing]]
  ): Behavior[Command] = Behaviors.receive { (ctx: ActorContext[Command], cmd: Command) =>
    cmd match {
      case Record(chatMessage: ChatMessage) =>
        Source.single(chatMessage).
          map { msg: ChatMessage =>
            new ProducerRecord[String, String](
              kafkaTopicName, msg.sender, Json.toJson(msg).toString()
            )
          }
          .runWith(kafkaProducer)
        running(subscribers)

      case Subscribe(subscriber: ActorRef[Nothing]) if !subscribers.contains(subscriber) =>
        ctx.log.info(s"+1 ${ctx.self.path.name} subscriber (=${subscribers.size + 1})")
        ctx.watchWith(subscriber, Unsubscribe(subscriber))
        running(subscribers + subscriber)

      case Subscribe(subscriber: ActorRef[Nothing]) =>
        ctx.log.warn(s"attempted to subscribe duplicate ${ctx.self.path.name} subscriber - ${subscriber.path}")
        Behaviors.unhandled

      case Unsubscribe(subscriber: ActorRef[Nothing]) if subscribers.contains(subscriber) =>
        ctx.log.info(s"-1 ${ctx.self.path.name} subscriber (=${subscribers.size - 1})")
        ctx.unwatch(subscriber)
        val remainingSubscribers: Set[ActorRef[Nothing]] = subscribers - subscriber
        if (remainingSubscribers.nonEmpty) {
          running(remainingSubscribers)
        } else {
          chatMessageBroadcaster ! ChatMessageBroadcaster.Unsubscribe(adapter)
          paused
        }

      case Unsubscribe(subscriber: ActorRef[Nothing]) =>
        ctx.log.warn(s"attempted to unsubscribe unknown ${ctx.self.path.name} subscriber - ${subscriber.path}")
        Behaviors.unhandled
    }
  }

  val initial: Behavior[Command] = paused
}
