package actors

package object tokenizing {
  type Tokenizer = String => Seq[String]

  def mappedKeywordsTokenizer(keywordsByRawToken: Map[String, String]): Tokenizer =
    if (keywordsByRawToken.isEmpty) NoOpTokenizer
    else new MappedKeywordsTokenizer(keywordsByRawToken)

  def normalizedWordsTokenizer(stopWords: Set[String] = Set(), minWordLength: Int = 1): Tokenizer =
    new NormalizedWordsTokenizer(stopWords, minWordLength)
}
