package actors.tokenizing

import scala.util.matching.Regex

object NormalizedWordsTokenizer {
  private val ValidWordPattern: Regex = """(\p{L}+(?:-\p{L}+)*)""".r
}

class NormalizedWordsTokenizer private[tokenizing](
  stopWords: Set[String], minWordLength: Int
) extends Tokenizer {
  import NormalizedWordsTokenizer._

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
