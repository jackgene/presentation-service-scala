package actors.tokenizing

import common.CommonProp
import org.scalacheck.Gen

class MappedKeywordsTokenizerProp extends CommonProp {
  val keywordsByRawToken: Gen[Map[String, String]] =
    for {
      keysAndValues <- Gen.nonEmptyContainerOf[Set, (String, String)](
        for {
          key: String <- Gen.nonEmptyListOf[Char](Gen.alphaLowerChar).map(_.mkString)
          value: String <- Gen.asciiStr
        } yield (key, value)
      )
    } yield keysAndValues.toMap

  property("extract all mapped tokens") {
    forAll(
      "keywordsByRawToken" |: keywordsByRawToken
    ) { (keywordsByRawToken: Map[String, String]) =>
      // Set up
      val instance = new MappedKeywordsTokenizer(keywordsByRawToken)

      // Test
      val actualTokens: Seq[String] = instance(keywordsByRawToken.keys.mkString(" "))

      // Verify
      assert(actualTokens.toSet == keywordsByRawToken.values.toSet)
    }
  }

  property("only extract mapped tokens") {
    forAll(
      "keywordsByRawToken"  |: keywordsByRawToken,
      "text"                |: Gen.asciiStr
    ) { (keywordsByRawToken: Map[String, String], text: String) =>
      // Set up
      val instance = new MappedKeywordsTokenizer(keywordsByRawToken)

      // Test
      val actualTokens: Seq[String] = instance(s"$text ${keywordsByRawToken.keys.head}")

      // Verify
      assert(actualTokens.toSet subsetOf keywordsByRawToken.values.toSet)
    }
  }
}
