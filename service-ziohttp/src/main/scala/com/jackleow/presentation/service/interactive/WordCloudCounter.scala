package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.configuration.model
import com.jackleow.presentation.service.configuration.model.PresentationConfiguration
import com.jackleow.presentation.service.interactive.model.ChatMessage
import com.jackleow.presentation.tokenizing.{MappedKeywordsTokenizer, NormalizedWordsTokenizer}
import com.jackleow.zio.*
import zio.*

object WordCloudCounter:
  val live: URLayer[
    PresentationConfiguration & (SubscriberCountingHub[ChatMessage] Named "chat") & (SubscriberCountingHub[ChatMessage] Named "rejected"),
    SendersByTokenCounter Named "word-cloud"
  ] =
    ZLayer:
      for
        config <- ZIO.service[PresentationConfiguration]
        wordCloudConfig = config.wordCloud
        counter <- SendersByTokenCounter.make(
          "word-cloud",
          NormalizedWordsTokenizer(
            wordCloudConfig.stopWords.toSet,
            wordCloudConfig.minWordLength,
            wordCloudConfig.maxWordLength
          ),
          wordCloudConfig.maxWordsPerPerson
        )
      yield counter
    .withName["word-cloud"]
