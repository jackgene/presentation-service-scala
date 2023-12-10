package actors.common

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Terminated}
import play.api.libs.json.{JsValue, Json, Writes}

/**
 * Receives messages and forwards them to the destination as JSON,
 * converted using the provided JSON `Writes`.
 */
object JsonWriter {
  def apply[T](destination: ActorRef[JsValue])(
    using writes: Writes[T]
  ): Behavior[T] = Behaviors.setup { (ctx: ActorContext[T]) =>
    ctx.watch(destination)

    Behaviors.
      receiveMessage[T] { (message: T) =>
        destination ! Json.toJson(message)
        Behaviors.same
      }.
      receiveSignal {
        case (_, Terminated(_)) => Behaviors.stopped
      }
  }
}
