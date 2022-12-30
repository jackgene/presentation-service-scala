package actors.tokenizing

class MappedKeywordsTokenizer private[tokenizing](
  keywordsByRawToken: Map[String, String]
) extends Tokenizer {
  if (keywordsByRawToken.isEmpty) {
    throw new IllegalArgumentException("keywordsByRawToken must not be empty")
  }
  private val keywordsByLowerCasedToken: Map[String, String] = keywordsByRawToken.
    map { case (keyword: String, token: String) =>
      keyword.toLowerCase -> token
    }
  private val keywordAlphabet: String = keywordsByLowerCasedToken.keySet.
    flatMap(_.toCharArray).
    mkString
  private val splitPattern: String = s"[^${keywordAlphabet}]+"

  override def apply(text: String): Seq[String] = text.trim.
    toLowerCase.
    split(splitPattern).to(LazyList).
    flatMap(keywordsByLowerCasedToken.get)
}
