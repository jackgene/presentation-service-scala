package com.jackleow.presentation.tokenizing

import common.CommonProp
import org.scalacheck.Gen

class NormalizedWordsTokenizerProp extends CommonProp {
  property("only extract hyphenated lower-case tokens") {
    forAll(
      "text" |: Gen.asciiStr
    ) { (text: String) =>
      // Set up
      val instance = new NormalizedWordsTokenizer()

      // Test
      val actualTokens: Seq[String] = instance(text)

      // Verify
      assert(actualTokens.forall { _.forall { (c: Char) => c == '-' || c.isLower } })
    }
  }

  property("never extract stop words") {
    forAll(
      "stopWords" |: Gen.nonEmptyContainerOf[Set, String](Gen.nonEmptyListOf[Char](Gen.alphaLowerChar).map(_.mkString)),
      "text"      |: Gen.asciiStr
    ) { (stopWords: Set[String], text: String) =>
      // Set up
      val instance = new NormalizedWordsTokenizer(stopWords)

      // Test
      val actualTokens: Seq[String] = instance(text)

      // Verify
      assert(actualTokens.forall { !stopWords.contains(_) })
    }
  }

  property("only extract words longer than minWordLength") {
    forAll(
      "minWordLength" |: Gen.posNum[Int],
      "text"          |: Gen.asciiStr
    ) { (minWordLength: Int, text: String) =>
      // Set up
      val instance = new NormalizedWordsTokenizer(minWordLength = minWordLength)

      // Test
      val actualTokens: Seq[String] = instance(text)

      // Verify
      assert(actualTokens.forall { _.length >= minWordLength })
    }
  }

  property("only extract words shorter than maxWordLength") {
    forAll(
      "maxWordLength" |: Gen.posNum[Int],
      "text"          |: Gen.asciiStr
    ) { (maxWordLength: Int, text: String) =>
      // Set up
      val instance = new NormalizedWordsTokenizer(maxWordLength = maxWordLength)

      // Test
      val actualTokens: Seq[String] = instance(text)

      // Verify
      assert(actualTokens.forall { _.length <= maxWordLength })
    }
  }
}
