package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.interactive.ChatMessageBroadcaster.ChatMessage
import com.jackleow.zio.stream.countRunning
import zio.json.{DeriveJsonEncoder, JsonEncoder, jsonField}
import zio.stream.{UStream, ZStream}
import zio.{Hub, Ref, UIO, ZIO}

object ChatMessageBroadcaster:
  object ChatMessage:
    given chatMessageEncoder: JsonEncoder[ChatMessage] =
      DeriveJsonEncoder.gen[ChatMessage]
  final case class ChatMessage(
    @jsonField("s") sender: String,
    @jsonField("r") recipient: String,
    @jsonField("t") text: String
  ):
    override def toString: String = s"$sender to $recipient: $text"

  def make(name: String): UIO[ChatMessageBroadcaster] =
    for
      hub: Hub[ChatMessage] <- Hub.dropping(1)
      subscribers: Ref[Int] <- Ref.make(0)
    yield DefaultChatMessageBroadcaster(name, hub, subscribers)

  private final class DefaultChatMessageBroadcaster (
    name: String, hub: Hub[ChatMessage], subscribersRef: Ref[Int]
  ) extends ChatMessageBroadcaster:
    override val chatMessages: UStream[ChatMessage] =
      ZStream
        .fromHub(hub)
        .countRunning(
          subscribersRef,
          (subscribers: Int) => ZIO.log(s"+1 $name subscriber (=$subscribers)"),
          (subscribers: Int) => ZIO.log(s"-1 $name subscriber (=$subscribers)"),
        )

    override def broadcast(chatMessage: ChatMessage): UIO[Boolean] =
      for
        _ <- ZIO.log(s"Received $name message: $chatMessage")
        success: Boolean <- hub.publish(chatMessage)
      yield success

trait ChatMessageBroadcaster:
  def chatMessages: UStream[ChatMessage]

  def broadcast(chatMessage: ChatMessage): UIO[Boolean]