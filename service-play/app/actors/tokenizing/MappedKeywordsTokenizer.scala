package actors.tokenizing

import scala.util.matching.Regex

object MappedKeywordsTokenizer {
  private val WordSeparatorPattern: Regex = """[\s,./|?!]""".r
}

class MappedKeywordsTokenizer private[tokenizing](
  keywordsByRawToken: Map[String, String]
) extends Tokenizer {
  import MappedKeywordsTokenizer.*

  if (keywordsByRawToken.isEmpty) {
    throw new IllegalArgumentException("keywordsByRawToken must not be empty")
  }
  {
    val invalidRawTokens: Set[String] = keywordsByRawToken.keySet.
      filter {
        WordSeparatorPattern.findFirstIn(_).nonEmpty
      }
    if (invalidRawTokens.nonEmpty) {
      throw new IllegalArgumentException(
        s"some keyword mappings have invalid raw tokens: ${invalidRawTokens.mkString("{", ",", "}")}"
      )
    }
  }

  private val keywordsByLowerCasedToken: Map[String, String] = keywordsByRawToken.
    map { case (keyword: String, token: String) =>
      keyword.toLowerCase -> token
    }

  override def apply(text: String): Seq[String] = WordSeparatorPattern.
    split(text.trim.toLowerCase).to(LazyList).
    flatMap(keywordsByLowerCasedToken.get)
}
