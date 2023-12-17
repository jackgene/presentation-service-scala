package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.*
import com.jackleow.zio.stream.*
import zio.stream.{SubscriptionRef, UStream}
import zio.{UIO, ZIO}

private final class ModeratedTextCollectorLive[N <: String](
  name: N,
  incomingEvents: UStream[ChatMessage],
  rejectedMessagesBroadcaster: SubscriberCountingHub[ChatMessage, "rejected"],
  moderatedMessagesRef: SubscriptionRef[Seq[String]],
  subscribersRef: SubscriptionRef[Int]
) extends ModeratedTextCollector[N]:
  override val moderatedMessages: UIO[UStream[ChatMessages]] =
    for
      subscribers: Int <- subscribersRef.get
      _ <-
        if subscribers == 0 then
          incomingEvents
            .takeWhileActive(subscribersRef.changes.map(_ > 0).drop(1))
            .runForeach:
              case ChatMessage("", _, text) =>
                moderatedMessagesRef.update(_ :+ text)
              case rejectedMessage: ChatMessage =>
                rejectedMessagesBroadcaster.publish(rejectedMessage)
            .forkDaemon
        else ZIO.unit
    yield
      moderatedMessagesRef
        .changes
        .countRunning(
          subscribersRef,
          (subscribers: Int) => ZIO.log(s"+1 $name subscriber (=$subscribers)"),
          (subscribers: Int) => ZIO.log(s"-1 $name subscriber (=$subscribers)"),
        )
        .map(ChatMessages(_))

  override def reset(): UIO[Unit] = moderatedMessagesRef.set(Nil)
