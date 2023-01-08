package actors.tokenizing

import common.CommonProp
import org.scalacheck.Gen

class NormalizedWordsTokenizerProp extends CommonProp {
  property("only extract hyphenated lower-case tokens") {
    forAll(
      "text" |: Gen.asciiPrintableStr
    ) { (text: String) =>
      // Set up
      val instance = new NormalizedWordsTokenizer(Set(), 1)

      // Test
      val actualTokens: Seq[String] = instance(text)

      // Verify
      assert(actualTokens.forall { _.forall { c: Char => c == '-' || c.isLower } })
    }
  }

  property("never extract stop words") {
    forAll(
      "stopWords" |: Gen.nonEmptyContainerOf[Set, String](Gen.nonEmptyListOf[Char](Gen.alphaLowerChar).map(_.mkString)),
      "text"      |: Gen.asciiPrintableStr
    ) { (stopWords: Set[String], text: String) =>
      // Set up
      val instance = new NormalizedWordsTokenizer(stopWords, 1)

      // Test
      val actualTokens: Seq[String] = instance(text)

      // Verify
      assert(actualTokens.forall { !stopWords.contains(_) })
    }
  }

  property("only extract words longer than minWordLength") {
    forAll(
      "minWordLength" |: Gen.posNum[Int],
      "text"          |: Gen.asciiPrintableStr
    ) { (minWordLength: Int, text: String) =>
      // Set up
      val instance = new NormalizedWordsTokenizer(Set(), minWordLength)

      // Test
      val actualTokens: Seq[String] = instance(text)

      // Verify
      assert(actualTokens.forall { _.length >= minWordLength })
    }
  }
}
