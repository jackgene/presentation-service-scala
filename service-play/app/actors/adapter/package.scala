package actors

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter.*
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Terminated}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.typed.scaladsl.ActorSource
import akka.stream.{Materializer, OverflowStrategy}
import org.reactivestreams.Publisher
import play.api.libs.streams.ActorFlow

/**
 * Adapts Play Framework to typed Akka actors.
 */
package object adapter {
  implicit class ActorFlowOps(val ext: ActorFlow.type) extends AnyVal {
    def sourceBehavior[Out](
      behavior: ActorRef[Out] => Behavior[?],
      bufferSize: Int = 16,
      overflowStrategy: OverflowStrategy = OverflowStrategy.dropHead
    )(implicit factory: ActorSystem, mat: Materializer): Source[Out, NotUsed] = {
      val (
        publisherActor: ActorRef[Option[Out]], publisher: Publisher[Option[Out]]
      ) = ActorSource.
        actorRef[Option[Out]](
          completionMatcher = { case None => () },
          failureMatcher = PartialFunction.empty,
          bufferSize = bufferSize,
          overflowStrategy = overflowStrategy
        ).
        toMat(Sink.asPublisher(false))(Keep.both).
        run()
      factory.spawnAnonymous(
        Behaviors.setup { (ctx: ActorContext[Out]) =>
          ctx.watch(publisherActor)
          ctx.spawnAnonymous(behavior(ctx.self))

          Behaviors.
            receiveMessage { (out: Out) =>
              publisherActor ! Some(out)
              Behaviors.same[Out]
            }.
            receiveSignal {
              case (_, Terminated(_)) => Behaviors.stopped
              case (_, PostStop) =>
                publisherActor ! None
                Behaviors.same
            }
        }
      )

      Source.
        fromPublisher(publisher).
        collect { case Some(out) => out }
    }
  }
}
