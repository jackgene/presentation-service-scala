package actors.tokenizing

import org.scalatestplus.play.PlaySpec

class NormalizedWordsTokenizerSpec extends PlaySpec {
  val testAsciiText = "#hashtag hyphenated-word  invalid_symbols?! YOLO Yo!"
  val testUnicodeText = "Schrödinger's little 🐱 (小猫)!"
  val testWordLengthText = "i am not your large teapot"

  "A NormalizedWordsTokenizer" when {
    "configured with no stop words or minimum word length" must {
      val instance = new NormalizedWordsTokenizer(
        stopWords = Set(), minWordLength = 1
      )

      "correctly tokenize ASCII text" in {
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
        )
        assert(actualTokens === expectedTokens)
      }

      "correctly tokenize Unicode text" in {
        // Test
        val actualTokens: Seq[String] = instance(testUnicodeText)

        // Verify
        val expectedTokens: Seq[String] = Seq(
          "schrödinger",  // ' is not valid (considered whitespace)
          "s",
          "little",       // 🐱 is not valid (considered whitespace)
          "小猫",          // () are not valid (considered whitespace)
        )
        assert(actualTokens === expectedTokens)
      }

      "extract all words regardless of length tokenizing variable word length text" in {
        // Test
        val actualTokens: Seq[String] = instance(testWordLengthText)

        // Verify
        val expectedTokens: Seq[String] = Seq("i", "am", "not", "your", "large", "teapot")
        assert(actualTokens === expectedTokens)
      }
    }

    "configured with no stop words and a minimum word length of 3" must {
      val instance = new NormalizedWordsTokenizer(
        stopWords = Set(), minWordLength = 3
      )

      "omit short words tokenizing ASCII text" in {
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
        )
        assert(actualTokens === expectedTokens)
      }

      "omit short words tokenizing Unicode text" in {
        // Test
        val actualTokens: Seq[String] = instance(testUnicodeText)

        // Verify
        val expectedTokens: Seq[String] = Seq(
          "schrödinger",
          // "s", too short
          "little",
          // "小猫" too short
        )
        assert(actualTokens === expectedTokens)
      }

      "omit short words tokenizing text" in {
        // Test
        val actualTokens: Seq[String] = instance(testWordLengthText)

        // Verify
        val expectedTokens: Seq[String] = Seq("not", "your", "large", "teapot")
        assert(actualTokens === expectedTokens)
      }
    }

    "configured with stop words and no minimum word length" must {
      val instance = new NormalizedWordsTokenizer(
        stopWords = Set("yolo", "large", "schrödinger"), minWordLength = 1
      )

      "omit stop words tokenizing ASCII text" in {
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
        )
        assert(actualTokens === expectedTokens)
      }

      "omit stop words tokenizing Unicode text" in {
        // Test
        val actualTokens: Seq[String] = instance(testUnicodeText)

        // Verify
        val expectedTokens: Seq[String] = Seq(
          //"schrödinger", stop word
          "s",
          "little",
          "小猫",
        )
        assert(actualTokens === expectedTokens)
      }

      "omit stop words regardless of length when tokenizing variable word length text" in {
        // Test
        val actualTokens: Seq[String] = instance(testWordLengthText)

        // Verify
        val expectedTokens: Seq[String] = Seq("i", "am", "not", "your", "teapot")
        assert(actualTokens === expectedTokens)
      }
    }

    "configured with stop words and a minimum word length of 5" must {
      val instance = new NormalizedWordsTokenizer(
        stopWords = Set("yolo", "large", "schrödinger"),
        minWordLength = 5
      )

      "omit short words and stop words tokenizing ASCII text" in {
        // Test
        val actualTokens: Seq[String] = instance(testAsciiText)

        // Verify
        val expectedTokens: Seq[String] = Seq(
          "hashtag",
          "hyphenated-word",
          "invalid",
          "symbols",
          // "yolo", stop word
          // "yo", too short
        )
        assert(actualTokens === expectedTokens)
      }

      "omit short words and stop words tokenizing Unicode text" in {
        // Test
        val actualTokens: Seq[String] = instance(testUnicodeText)

        // Verify
        val expectedTokens: Seq[String] = Seq(
          // "schrödinger", stop word
          // "s", too short
          "little",
          // "小猫" too short
        )
        assert(actualTokens === expectedTokens)
      }

      "omit short words and stop words tokenizing variable word length text" in {
        // Test
        val actualTokens: Seq[String] = instance(testWordLengthText)

        // Verify
        val expectedTokens: Seq[String] = Seq("teapot")
        assert(actualTokens === expectedTokens)
      }
    }

    "misconfigured" must {
      "fail on stop word of an empty string" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new NormalizedWordsTokenizer(Set(""), 1)
        }
      }

      "fail on stop word of a blank word" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new NormalizedWordsTokenizer(Set(" "), 1)
        }
      }

      "fail on stop word of a numeric string" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new NormalizedWordsTokenizer(Set("1"), 1)
        }
      }

      "fail on stop word containing non-letter symbols" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new NormalizedWordsTokenizer(Set("$_"), 1)
        }
      }

      "fail on minimum word length less than 1" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new NormalizedWordsTokenizer(Set(), 0)
        }
      }
    }
  }
}
