package actors.adapter

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter.*
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Terminated}
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorSource
import akka.stream.{Materializer, OverflowStrategy}
import play.api.libs.streams.ActorFlow

/**
 * Adapts Play Framework to typed Akka actors.
 */
implicit class ActorFlowOps(val ext: ActorFlow.type) extends AnyVal {
  def sourceBehavior[Out](
    behavior: ActorRef[Out] => Behavior[?],
    bufferSize: Int = 16,
    overflowStrategy: OverflowStrategy = OverflowStrategy.dropHead
  )(implicit factory: ActorSystem, mat: Materializer): Source[Out, NotUsed] = {
    val (
      sourceActor: ActorRef[Option[Out]], source: Source[Option[Out], NotUsed]
    ) = ActorSource.
      actorRef[Option[Out]](
        completionMatcher = { case None => () },
        failureMatcher = PartialFunction.empty,
        bufferSize = bufferSize,
        overflowStrategy = overflowStrategy
      ).
      preMaterialize()
    factory.spawnAnonymous(
      Behaviors.setup { (ctx: ActorContext[Out]) =>
        ctx.watch(sourceActor)
        ctx.spawnAnonymous(behavior(ctx.self))

        Behaviors.
          receiveMessage { (out: Out) =>
            sourceActor ! Some(out)
            Behaviors.same[Out]
          }.
          receiveSignal {
            case (_, Terminated(_)) => Behaviors.stopped
            case (_, PostStop) =>
              sourceActor ! None
              Behaviors.same
          }
      }
    )

    source.collect { case Some(out) => out }
  }
}
