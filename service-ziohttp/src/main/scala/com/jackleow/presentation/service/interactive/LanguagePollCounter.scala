package com.jackleow.presentation.service.interactive

import com.jackleow.presentation.service.common.SubscriberCountingHub
import com.jackleow.presentation.service.configuration.model
import com.jackleow.presentation.service.configuration.model.PresentationConfiguration
import com.jackleow.presentation.service.interactive.model.ChatMessage
import com.jackleow.presentation.tokenizing.MappedKeywordsTokenizer
import zio.*

object LanguagePollCounter:
  val live: URLayer[
    PresentationConfiguration & SubscriberCountingHub[ChatMessage, "chat"] & SubscriberCountingHub[ChatMessage, "rejected"],
    SendersByTokenCounter["language-poll"]
  ] =
    ZLayer:
      for
        config <- ZIO.service[PresentationConfiguration]
        languagePollConfig = config.languagePoll
        counter <- SendersByTokenCounter.make["language-poll"](
          MappedKeywordsTokenizer(languagePollConfig.languageByKeyword),
          languagePollConfig.maxVotesPerPerson
        )
      yield counter
