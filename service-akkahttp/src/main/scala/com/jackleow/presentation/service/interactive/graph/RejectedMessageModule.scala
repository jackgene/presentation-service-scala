package com.jackleow.presentation.service.interactive.graph

import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source}
import com.jackleow.akka.stream.scaladsl.Counter
import com.jackleow.presentation.service.interactive.model.ChatMessage

trait RejectedMessageModule:
  def rejectedMessagesSink: Sink[ChatMessage, NotUsed]
  def rejectedMessagesSubscriptionCounter: Counter
  def rejectedMessagesSource: Source[ChatMessage, NotUsed]
