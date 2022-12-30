package actors.tokenizing

import org.scalatestplus.play.PlaySpec

class MappedKeywordsTokenizerSpec extends PlaySpec {
  val testText = "Lorem ipsum dolor sit amet!"

  "A MappedKeywordsTokenizer" when {
    "configured with an empty mapping" must {
      "fail" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new MappedKeywordsTokenizer(Map())
        }
      }
    }

    "configured with lower-case keyed mapping" must {
      val instance = new MappedKeywordsTokenizer(
        Map(
          "lorem" -> "Mock-1",
          "ipsum" -> "Mock-1",
          "amet!" -> "Mock-2",
          "other" -> "Mock-2",
        )
      )

      "extract matching keywords" in {
        // Test
        val actualTokens = instance(testText)

        // Verify
        val expectedTokens = Seq("Mock-1", "Mock-1", "Mock-2")
        assert(actualTokens == expectedTokens)
      }
    }

    "configured with upper-case keyed mapping" must {
      val instance = new MappedKeywordsTokenizer(
        Map(
          "LOREM" -> "Mock-1",
          "IPSUM" -> "Mock-1",
          "AMET!" -> "Mock-2",
          "OTHER" -> "Mock-2",
        )
      )

      "extract matching keywords" in {
        // Test
        val actualTokens = instance(testText)

        // Verify
        val expectedTokens = Seq("Mock-1", "Mock-1", "Mock-2")
        assert(actualTokens == expectedTokens)
      }
    }
  }
}
