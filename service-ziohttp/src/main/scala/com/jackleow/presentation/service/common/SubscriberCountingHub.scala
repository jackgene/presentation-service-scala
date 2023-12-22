package com.jackleow.presentation.service.common

import com.jackleow.zio.stream.countRunning
import zio.*
import zio.stream.*

object SubscriberCountingHub:
  def make[A](name: String): UIO[SubscriberCountingHub[A]] =
    for
      hub: Hub[A] <- Hub.dropping(64)
      subscribers: Ref[Int] <- Ref.make(0)
    yield SubscriberCountingHub(name, hub, subscribers)

final class SubscriberCountingHub[A](
  name: String, hub: Hub[A], subscribersRef: Ref[Int]
):
  private val logName: String = if name == "" then "" else s" $name"

  val elements: UStream[A] =
    ZStream
      .fromHub(hub)
      .countRunning(
        subscribersRef,
        (subscribers: Int) => ZIO.log(s"+1$logName subscriber (=$subscribers)"),
        (subscribers: Int) => ZIO.log(s"-1$logName subscriber (=$subscribers)"),
      )

  def publish(a: A): UIO[Boolean] = hub.publish(a)
