package com.jackleow.presentation.tokenizing

import scala.collection.immutable.ArraySeq
import scala.util.matching.Regex

object MappedKeywordsTokenizer {
  private val WordSeparatorPattern: Regex = """[\s!"&,./?|]""".r

  def apply(keywordsByRawToken: Map[String, String]): Tokenizer =
    new MappedKeywordsTokenizer(keywordsByRawToken)
}

final class MappedKeywordsTokenizer private[tokenizing](
  keywordsByRawToken: Map[String, String]
) extends Tokenizer {
  import MappedKeywordsTokenizer.*

  require(keywordsByRawToken.nonEmpty, "keywordsByRawToken must not be empty")

  {
    val invalidRawTokens: Set[String] = keywordsByRawToken.keySet.
      filter {
        WordSeparatorPattern.findFirstIn(_).nonEmpty
      }
    require(
      invalidRawTokens.isEmpty,
      s"some keyword mappings have invalid raw tokens: ${invalidRawTokens.mkString("{", ",", "}")}"
    )
  }

  private val keywordsByLowerCasedToken: Map[String, String] = keywordsByRawToken.
    map { case (keyword: String, token: String) =>
      keyword.toLowerCase -> token
    }

  override def apply(text: String): Seq[String] = ArraySeq.
    unsafeWrapArray(
      WordSeparatorPattern.split(text.trim)
    ).
    map {
      _.toLowerCase
    }.
    flatMap(keywordsByLowerCasedToken.get)
}
