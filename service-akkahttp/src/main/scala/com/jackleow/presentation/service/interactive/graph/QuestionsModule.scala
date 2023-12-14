package com.jackleow.presentation.service.interactive.graph

import akka.stream.scaladsl.Flow
import com.jackleow.akka.stream.scaladsl.Counter
import com.jackleow.presentation.service.interactive.model.{ChatMessage, ChatMessages, Reset}

trait QuestionsModule:
  def questionsFlow: Flow[ChatMessage | Reset.type, ChatMessages, Counter]
