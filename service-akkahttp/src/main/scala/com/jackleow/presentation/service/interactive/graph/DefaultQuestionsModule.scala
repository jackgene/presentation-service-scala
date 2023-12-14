package com.jackleow.presentation.service.interactive.graph

import akka.stream.scaladsl.*
import com.jackleow.akka.stream.scaladsl.*
import com.jackleow.presentation.service.SubscriptionSupport
import com.jackleow.presentation.service.interactive.model.*
import com.typesafe.scalalogging.StrictLogging

trait DefaultQuestionsModule extends QuestionsModule
  with StrictLogging
  with SubscriptionSupport:
  this: RejectedMessageModule =>

  override val questionsFlow: Flow[ChatMessage | Reset.type, ChatMessages, Counter] =
    Flow[ChatMessage | Reset.type]
      .viaMat(
        Flow.activeFilter[ChatMessage | Reset.type, Counter](
          hasActiveSubscriptionsSource("question"),
          _ != Reset
        )
      )(Keep.right)
      .divertTo(
        Flow[ChatMessage | Reset.type]
          .collect:
            case chatMessage: ChatMessage => chatMessage
          .to(rejectedMessagesSink),
        {
          case ChatMessage(sender: String, _, _) => sender != ""
          case Reset => false
        }
      )
      .scan[List[String]](Nil):
        case (texts: Seq[String], ChatMessage(_, _, text: String)) =>
          texts :+ text
        case (_, Reset) => Nil
      .map(ChatMessages(_))
