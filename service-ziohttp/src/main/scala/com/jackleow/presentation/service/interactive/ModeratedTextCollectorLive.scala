package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.*
import com.jackleow.zio.Named
import com.jackleow.zio.stream.*
import zio.*
import zio.stream.*

private final class ModeratedTextCollectorLive(
  name: String,
  chatMessages: UStream[ChatMessage],
  rejectedMessagesBroadcaster: SubscriberCountingHub[ChatMessage] Named "rejected",
  moderatedMessagesRef: SubscriptionRef[Seq[String]],
  subscribersRef: SubscriptionRef[Int]
) extends ModeratedTextCollector:
  override val moderatedMessages: UIO[UStream[ChatMessages]] =
    for
      subscribers: Int <- subscribersRef.get
      _ <-
        if subscribers == 0 then
          chatMessages
            .takeWhileActive(subscribersRef.changes.drop(1).map(_ > 0))
            .runForeach:
              case ChatMessage("", _, text) =>
                moderatedMessagesRef.update(_ :+ text)
              case rejectedMessage: ChatMessage =>
                rejectedMessagesBroadcaster.get.publish(rejectedMessage)
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
