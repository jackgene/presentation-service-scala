package com.jackleow.presentation.service.interactive.graph

import akka.stream.scaladsl.Flow
import com.jackleow.akka.stream.scaladsl.Counter
import com.jackleow.presentation.config.{Configuration, ConfigurationModule}
import com.jackleow.presentation.service.SubscriptionSupport
import com.jackleow.presentation.service.interactive.model.{ChatMessage, Counts, Reset}
import com.jackleow.presentation.tokenizing.{NormalizedWordsTokenizer, Tokenizer}
import com.typesafe.scalalogging.StrictLogging

trait DefaultWordCloudModule extends WordCloudModule
  with StrictLogging
  with SubscriptionSupport:
  this: ConfigurationModule & RejectedMessageModule =>

  private val wordCloudConfig: Configuration.WordCloud =
    configuration.presentation.wordCloud
  private val extractWordCloudTokens: Tokenizer = NormalizedWordsTokenizer(
    wordCloudConfig.stopWords,
    wordCloudConfig.minWordLength,
    wordCloudConfig.maxWordLength
  )
  private val maxWordCloudWordsPerPerson: Int =
    wordCloudConfig.maxWordsPerPerson

  override val wordCloudFlow: Flow[ChatMessage | Reset.type, Counts, Counter] =
    SenderByTokenCountFlow(
      extractWordCloudTokens, maxWordCloudWordsPerPerson,
      hasActiveSubscriptionsSource("language-poll"),
      rejectedMessagesSink
    )
