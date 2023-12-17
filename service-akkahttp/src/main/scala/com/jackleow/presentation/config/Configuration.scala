package com.jackleow.presentation.config

import pureconfig.*
import pureconfig.generic.derivation.default.*
import com.jackleow.presentation.config.Configuration.*

object Configuration:
  case class LanguagePoll(
    maxVotesPerPerson: Int,
    languageByKeyword: Map[String, String]
  )
  case class WordCloud(
    maxWordsPerPerson: Int,
    minWordLength: Int,
    maxWordLength: Int,
    stopWords: Set[String]
  )
  case class Presentation(
    languagePoll: LanguagePoll,
    wordCloud: WordCloud
  )

case class Configuration (
  presentation: Presentation
) derives ConfigReader
