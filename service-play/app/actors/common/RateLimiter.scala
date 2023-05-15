package actors.common

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration.{Deadline, FiniteDuration}

/**
 * Receives messages and forwards them to the destination actor no more
 * frequently than the given period.
 *
 * Excess messages may be dropped.
 *
 * This is useful when the messages represent cumulative data, where
 * some missing messages wouldn't impact the final result.
 */
object RateLimiter {
  def apply[T](destination: ActorRef[T], limitPeriod: FiniteDuration): Behavior[T] =
    new RateLimiter(destination, limitPeriod).initial
}
private class RateLimiter[T](destination: ActorRef[T], limitPeriod: FiniteDuration) {
  private def running(
    quietPeriodEnds: Deadline, scheduled: Cancellable
  ): Behavior[T] = Behaviors.receive { (ctx: ActorContext[T], msg: T) =>
    scheduled.cancel()
    if (quietPeriodEnds.isOverdue()) {
      destination ! msg
      running(limitPeriod.fromNow, Cancellable.alreadyCancelled)
    } else {
      running(quietPeriodEnds, ctx.scheduleOnce(quietPeriodEnds.timeLeft, destination, msg))
    }
  }

  val initial: Behavior[T] = running(Deadline.now, Cancellable.alreadyCancelled)
}
