package com.jackleow.presentation.service.interactive.graph

import akka.stream.scaladsl.Flow
import com.jackleow.akka.stream.scaladsl.Counter
import com.jackleow.presentation.config.{Configuration, ConfigurationModule}
import com.jackleow.presentation.service.SubscriptionSupport
import com.jackleow.presentation.service.interactive.model.{ChatMessage, Counts, Reset}
import com.jackleow.presentation.tokenizing.{MappedKeywordsTokenizer, Tokenizer}
import com.typesafe.scalalogging.StrictLogging

trait DefaultLanguagePollModule extends LanguagePollModule
  with StrictLogging
  with SubscriptionSupport:
  this: ConfigurationModule & RejectedMessageModule =>

  private val languagePollConfig: Configuration.LanguagePoll =
    configuration.presentation.languagePoll
  private val extractLanguagePollTokens: Tokenizer = MappedKeywordsTokenizer(
    languagePollConfig.languageByKeyword
  )
  private val maxLanguagePollVotesPerPerson: Int =
    languagePollConfig.maxVotesPerPerson

  override val languagePollFlow: Flow[ChatMessage | Reset.type, Counts, Counter] =
    SenderByTokenCountFlow(
      extractLanguagePollTokens, maxLanguagePollVotesPerPerson,
      hasActiveSubscriptionsSource("language-poll"),
      rejectedMessagesSink
    )
