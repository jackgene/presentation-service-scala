package actors.tokenizing

import scala.collection.immutable.ArraySeq
import scala.util.matching.Regex

object NormalizedWordsTokenizer {
  private val ValidWordPattern: Regex = """(\p{L}+(?:-\p{L}+)*)""".r
  private val WordSeparatorPattern: Regex = """[^\p{L}\-]+""".r
}

class NormalizedWordsTokenizer private[tokenizing](
  stopWords: Set[String], minWordLength: Int, maxWordLength: Int
) extends Tokenizer {
  import NormalizedWordsTokenizer.*

  require(minWordLength >= 1, "minWordLength must be at least 1")
  require(maxWordLength >= minWordLength, "maxWordLength must be at least minWordLength")

  {
    val invalidStopWords: Set[String] = stopWords.filterNot(ValidWordPattern.matches)
    require(
      invalidStopWords.isEmpty,
      s"some stop words are invalid: ${invalidStopWords.mkString("{", ",", "}")}"
    )
  }

  private val lowerCasedStopWords: Set[String] = stopWords.map(_.toLowerCase)

  override def apply(text: String): Seq[String] = ArraySeq.
    unsafeWrapArray(
      WordSeparatorPattern.split(text.trim)
    ).
    map {
      _.toLowerCase
    }.
    collect {
      case ValidWordPattern(word: String) if
        minWordLength  <= word.length && word.length <= maxWordLength &&
          !lowerCasedStopWords.contains(word)
      => word
    }
}
