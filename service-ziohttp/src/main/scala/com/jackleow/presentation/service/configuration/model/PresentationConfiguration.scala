package com.jackleow.presentation.service.configuration.model

import com.jackleow.presentation.service.configuration.model.PresentationConfiguration.*
import zio.config.*
import zio.config.magnolia.descriptor

object PresentationConfiguration:
  object LanguagePoll:
    given configDescriptor: ConfigDescriptor[LanguagePoll] =
      descriptor[LanguagePoll]
  case class LanguagePoll(
    maxVotesPerPerson: Int,
    languageByKeyword: Map[String, String]
  )

  object WordCloud:
    given configDescriptor: ConfigDescriptor[WordCloud] =
      descriptor[WordCloud]
  case class WordCloud(
    maxWordsPerPerson: Int,
    minWordLength: Int,
    maxWordLength: Int,
    stopWords: List[String]
  )

  val configDescriptor: ConfigDescriptor[PresentationConfiguration] =
    descriptor[PresentationConfiguration]

case class PresentationConfiguration(
  languagePoll: LanguagePoll,
  wordCloud: WordCloud
)
