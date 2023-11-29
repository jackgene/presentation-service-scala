package com.jackleow.presentation.service

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.jackleow.akka.stream.scaladsl.*
import com.typesafe.scalalogging.StrictLogging

trait SubscriptionSupport:
  this: StrictLogging =>

  def hasActiveSubscriptionsSource(name: String = ""): Source[Boolean, Counter] =
    Source.
      counts.
      scan(0) {
        (prevCount: Int, nextCount: Int) =>
          if nextCount != prevCount then {
            val logName: String = if name == "" then "" else s" $name"
            logger.info(f"${nextCount - prevCount}%+d$logName subscriber (=$nextCount)")
          }
          nextCount
      }.
      drop(1).
      map(_ > 0)

  def subscriptionTrackingFlow[Out](source: Source[Out, ?], counter: Counter): Flow[Any, Out, NotUsed] =
    Flow.fromSinkAndSourceCoupled(
      Sink.onComplete { _ => counter.decrement() },
      source.onStart { counter.increment() }
    )
