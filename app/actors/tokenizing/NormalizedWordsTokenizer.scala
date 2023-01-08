package actors.tokenizing

import scala.util.matching.Regex

object NormalizedWordsTokenizer {
  private val ValidWordPattern: Regex = """(\p{L}+(?:-\p{L}+)*)""".r
}

class NormalizedWordsTokenizer private[tokenizing](
  stopWords: Set[String], minWordLength: Int
) extends Tokenizer {
  import NormalizedWordsTokenizer.*

  if (minWordLength < 1) {
    throw new IllegalArgumentException(s"minWordLength must be at least 1")
  }
  {
    val invalidStopWords: Set[String] = stopWords.filterNot(ValidWordPattern.matches)
    if (invalidStopWords.nonEmpty) {
      throw new IllegalArgumentException(s"some stop words are invalid: ${invalidStopWords.mkString("{", ",", "}")}")
    }
  }

  private val lowerCasedStopWords: Set[String] = stopWords.map(_.toLowerCase)

  override def apply(text: String): Seq[String] = text.trim.
    split("""[^\p{L}\-]+""").toSeq.
    map {
      _.toLowerCase
    }.
    collect {
      case ValidWordPattern(word: String)
        if word.length >= minWordLength && !lowerCasedStopWords.contains(word) =>

        word
    }
}
