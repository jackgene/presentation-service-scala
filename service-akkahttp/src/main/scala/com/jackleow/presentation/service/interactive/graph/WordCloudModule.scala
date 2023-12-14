package com.jackleow.presentation.service.interactive.graph

import akka.stream.scaladsl.Flow
import com.jackleow.akka.stream.scaladsl.Counter
import com.jackleow.presentation.service.interactive.model.*

trait WordCloudModule:
  def wordCloudFlow: Flow[ChatMessage | Reset.type, Counts, Counter]
