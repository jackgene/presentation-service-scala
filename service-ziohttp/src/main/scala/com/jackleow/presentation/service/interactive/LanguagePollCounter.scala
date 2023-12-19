package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.configuration.model
import com.jackleow.presentation.service.configuration.model.PresentationConfiguration
import com.jackleow.presentation.service.interactive.model.ChatMessage
import com.jackleow.presentation.tokenizing.MappedKeywordsTokenizer
import com.jackleow.zio.*
import zio.*

object LanguagePollCounter:
  val live: URLayer[
    PresentationConfiguration & (SubscriberCountingHub[ChatMessage] Named "chat") & (SubscriberCountingHub[ChatMessage] Named "rejected"),
    SendersByTokenCounter Named "language-poll"
  ] =
    ZLayer:
      for
        config <- ZIO.service[PresentationConfiguration]
        languagePollConfig = config.languagePoll
        counter <- SendersByTokenCounter.make(
          "language-poll",
          MappedKeywordsTokenizer(languagePollConfig.languageByKeyword),
          languagePollConfig.maxVotesPerPerson
        )
      yield counter
    .withName["language-poll"]
