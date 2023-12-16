package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.interactive.model.*
import com.jackleow.zio.stream.countRunning
import zio.stream.{SubscriptionRef, UStream}
import zio.{Ref, UIO, ZIO}

private final class ModeratedTextCollectorLive(
  name: String,
  incomingEvents: UStream[ChatMessage | Reset.type],
  rejectedMessagesBroadcaster: SubscriberCountingHub[ChatMessage],
  moderatedMessagesRef: SubscriptionRef[Seq[String]],
  subscribersRef: SubscriptionRef[Int]
) extends ModeratedTextCollector:
  override val moderatedMessages: UIO[UStream[ChatMessages]] =
    for
      subscribers: Int <- subscribersRef.get
      _ <-
        if subscribers == 0 then
          subscribersRef.changes.map(_ > 0).drop(1)
            .mergeEither(incomingEvents)
            .collectWhile:
              case activeSubs @ Left(true) => activeSubs
              case event @ Right(_) => event
            .collect:
              case Right(event: (ChatMessage | Reset.type)) => event
            .runForeach:
              case ChatMessage("", _, text) =>
                moderatedMessagesRef.update(_ :+ text)
              case rejectedMessage: ChatMessage =>
                rejectedMessagesBroadcaster.publish(rejectedMessage)
              case Reset =>
                moderatedMessagesRef.set(Nil)
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
