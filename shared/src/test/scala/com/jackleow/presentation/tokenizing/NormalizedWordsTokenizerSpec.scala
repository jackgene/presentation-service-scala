package com.jackleow.presentation.tokenizing

import org.scalatest.wordspec.AnyWordSpec

class NormalizedWordsTokenizerSpec extends AnyWordSpec:
  val testAsciiText: String = "#hashtag hyphenated-word-  invalid_symbols?! YOLO Yo!fomo"
  val testUnicodeText: String = "Schrödinger's smol little 🐱 (小猫)!"
  val testWordLengthText: String = "i am not your large teapot"

  "A NormalizedWordsTokenizer" when:
    "configured with no stop words, minimum, or maximum word length" must:
      val instance = new NormalizedWordsTokenizer()

      "correctly tokenize ASCII text" in:
        // Test
        val actualTokens: Seq[String] = instance(testAsciiText)

        // Verify
        val expectedTokens: Seq[String] = Seq(
          "hashtag",          // # is not valid (considered whitespace)
          "hyphenated-word",  // - is valid
          "invalid",          // _ is not valid (considered whitespace)
          "symbols",          // ? and ! are not valid (consider whitespace)
          "yolo",             // lower-cased
          "yo",               // ! is not valid (considered whitespace)
          "fomo",             // after !
        )
        assert(actualTokens === expectedTokens)

      "correctly tokenize Unicode text" in:
        // Test
        val actualTokens: Seq[String] = instance(testUnicodeText)

        // Verify
        val expectedTokens: Seq[String] = Seq(
          "schrödinger",  // ' is not valid (considered whitespace)
          "s",
          "smol",
          "little",       // 🐱 is not valid (considered whitespace)
          "小猫",          // () are not valid (considered whitespace)
        )
        assert(actualTokens === expectedTokens)

      "extract all words regardless of length tokenizing variable word length text" in:
        // Test
        val actualTokens: Seq[String] = instance(testWordLengthText)

        // Verify
        val expectedTokens: Seq[String] = Seq("i", "am", "not", "your", "large", "teapot")
        assert(actualTokens === expectedTokens)

    "configured with no stop words, a minimum word length of 3, and no maximum word length" must:
      val instance = new NormalizedWordsTokenizer(minWordLength = 3)

      "omit short words tokenizing ASCII text" in:
        // Test
        val actualTokens: Seq[String] = instance(testAsciiText)

        // Verify
        val expectedTokens: Seq[String] = Seq(
          "hashtag",
          "hyphenated-word",
          "invalid",
          "symbols",
          "yolo",
          // "yo", too short
          "fomo",
        )
        assert(actualTokens === expectedTokens)

      "omit short words tokenizing Unicode text" in:
        // Test
        val actualTokens: Seq[String] = instance(testUnicodeText)

        // Verify
        val expectedTokens: Seq[String] = Seq(
          "schrödinger",
          // "s",   too short
          "smol",
          "little",
          // "🐱"   not a letter
          // "小猫"  too short
        )
        assert(actualTokens === expectedTokens)

      "omit short words tokenizing variable word length text" in:
        // Test
        val actualTokens: Seq[String] = instance(testWordLengthText)

        // Verify
        val expectedTokens: Seq[String] = Seq("not", "your", "large", "teapot")
        assert(actualTokens === expectedTokens)

    "configured with no stop words, no minimum word length, and a maximum word length of 4" must:
      val instance = new NormalizedWordsTokenizer(maxWordLength = 4)

      "omit long words tokenizing ASCII text" in:
        // Test
        val actualTokens: Seq[String] = instance(testAsciiText)

        // Verify
        val expectedTokens: Seq[String] = Seq(
          // "hashtag",         too long
          // "hyphenated-word", too long
          // "invalid",         too long
          // "symbols",         too long
          "yolo",
          "yo",
          "fomo",
        )
        assert(actualTokens === expectedTokens)

      "omit long words tokenizing Unicode text" in:
        // Test
        val actualTokens: Seq[String] = instance(testUnicodeText)

        // Verify
        val expectedTokens: Seq[String] = Seq(
          // "schrödinger",  too long
          "s",
          "smol",
          // "little",       too long
          // "🐱"            not a letter
           "小猫"
        )
        assert(actualTokens === expectedTokens)

      "omit long words tokenizing variable word length text" in:
        // Test
        val actualTokens: Seq[String] = instance(testWordLengthText)

        // Verify
        val expectedTokens: Seq[String] = Seq("i", "am", "not", "your")
        assert(actualTokens === expectedTokens)

    "configured with stop words and no minimum or maximum word length" must:
      val instance = new NormalizedWordsTokenizer(stopWords = Set("yolo", "large", "schrödinger"))

      "omit stop words tokenizing ASCII text" in:
        // Test
        val actualTokens: Seq[String] = instance(testAsciiText)

        // Verify
        val expectedTokens: Seq[String] = Seq(
          "hashtag",
          "hyphenated-word",
          "invalid",
          "symbols",
          // "yolo", stop word
          "yo",
          "fomo",
        )
        assert(actualTokens === expectedTokens)

      "omit stop words tokenizing Unicode text" in:
        // Test
        val actualTokens: Seq[String] = instance(testUnicodeText)

        // Verify
        val expectedTokens: Seq[String] = Seq(
          //"schrödinger",  stop word
          "s",
          "smol",
          "little",
          // "🐱"           not a letter
          "小猫",
        )
        assert(actualTokens === expectedTokens)

      "omit stop words regardless of length when tokenizing variable word length text" in:
        // Test
        val actualTokens: Seq[String] = instance(testWordLengthText)

        // Verify
        val expectedTokens: Seq[String] = Seq("i", "am", "not", "your", "teapot")
        assert(actualTokens === expectedTokens)

    "configured with stop words, a minimum word length of 3, and a maximum word length of 5" must:
      val instance = new NormalizedWordsTokenizer(
        stopWords = Set("yolo", "large", "schrödinger"),
        minWordLength = 3,
        maxWordLength = 5
      )

      "omit short, long, and stop words tokenizing ASCII text" in:
        // Test
        val actualTokens: Seq[String] = instance(testAsciiText)

        // Verify
        val expectedTokens: Seq[String] = Seq(
          // "hashtag",          too long
          // "hyphenated-word",  too long
          // "invalid",          too long
          // "symbols",          too long
          // "yolo",             stop word
          // "yo",               too short
          "fomo",
        )
        assert(actualTokens === expectedTokens)

      "omit short, long, and stop words tokenizing Unicode text" in:
        // Test
        val actualTokens: Seq[String] = instance(testUnicodeText)

        // Verify
        val expectedTokens: Seq[String] = Seq(
          // "schrödinger",  stop word
          // "s",            too short
          "smol",
          // "little",       too long
          // "🐱"            not a letter
          // "小猫"           too short
        )
        assert(actualTokens === expectedTokens)

      "omit short, long, and stop words tokenizing variable word length text" in:
        // Test
        val actualTokens: Seq[String] = instance(testWordLengthText)

        // Verify
        val expectedTokens: Seq[String] = Seq("not", "your")
        assert(actualTokens === expectedTokens)

    "misconfigured" must:
      "fail on stop word of an empty string" in:
        // Test
        assertThrows[IllegalArgumentException]:
          new NormalizedWordsTokenizer(Set(""), 1, maxWordLength = Int.MaxValue)

      "fail on stop word of a blank word" in:
        // Test
        assertThrows[IllegalArgumentException]:
          new NormalizedWordsTokenizer(Set(" "), 1, maxWordLength = Int.MaxValue)

      "fail on stop word of a numeric string" in:
        // Test
        assertThrows[IllegalArgumentException]:
          new NormalizedWordsTokenizer(Set("1"), 1, maxWordLength = Int.MaxValue)

      "fail on stop word containing non-letter symbols" in:
        // Test
        assertThrows[IllegalArgumentException]:
          new NormalizedWordsTokenizer(Set("$_"), 1, maxWordLength = Int.MaxValue)

      "fail on minimum word length less than 1" in:
        // Test
        assertThrows[IllegalArgumentException]:
          new NormalizedWordsTokenizer(Set(), 0, maxWordLength = Int.MaxValue)

      "fail on maximum word length less than minimum word length" in:
        // Test
        assertThrows[IllegalArgumentException]:
          new NormalizedWordsTokenizer(Set(), 5, 4)
