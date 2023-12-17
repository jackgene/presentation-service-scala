package com.jackleow.presentation.service.configuration.model

import com.jackleow.presentation.service.configuration.model.PresentationConfiguration.*
import zio.Config
import zio.config.*
import zio.config.magnolia.deriveConfig

object PresentationConfiguration:
  object LanguagePoll:
    given config: Config[LanguagePoll] = deriveConfig[LanguagePoll]
  case class LanguagePoll(
    maxVotesPerPerson: Int,
    languageByKeyword: Map[String, String]
  )

  object WordCloud:
    given config: Config[WordCloud] = deriveConfig[WordCloud]
  case class WordCloud(
    maxWordsPerPerson: Int,
    minWordLength: Int,
    maxWordLength: Int,
    stopWords: Seq[String]
  )

  val config: Config[PresentationConfiguration] =
    deriveConfig[PresentationConfiguration].nested("presentation")

case class PresentationConfiguration(
  languagePoll: LanguagePoll,
  wordCloud: WordCloud
)
