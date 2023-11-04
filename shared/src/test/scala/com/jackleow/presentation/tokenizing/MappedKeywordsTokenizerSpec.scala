package com.jackleow.presentation.tokenizing

import org.scalatest.wordspec.AnyWordSpec

class MappedKeywordsTokenizerSpec extends AnyWordSpec {
  val testText: String = "Lorem ipsum dolor sit amet!"

  "A MappedKeywordsTokenizer" when {
    "configured with lower-case keyed mapping" must {
      val instance = new MappedKeywordsTokenizer(
        Map(
          "lorem" -> "Mock-1",
          "ipsum" -> "Mock-1",
          "amet" -> "Mock-2",
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
          "AMET" -> "Mock-2",
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

    "misconfigured" must {
      "fail on an empty mapping" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new MappedKeywordsTokenizer(Map())
        }
      }

      "fail on mapping with space in raw token" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new MappedKeywordsTokenizer(Map("mock token" -> "whatever"))
        }
      }

      "fail on mapping with tab in raw token" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new MappedKeywordsTokenizer(Map("mock\ttoken" -> "whatever"))
        }
      }

      "fail on mapping with exclamation mark in raw token" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new MappedKeywordsTokenizer(Map("mock!token" -> "whatever"))
        }
      }

      "fail on mapping with quote in raw token" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new MappedKeywordsTokenizer(Map("mock\"token" -> "whatever"))
        }
      }

      "fail on mapping with ampersand in raw token" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new MappedKeywordsTokenizer(Map("mock&token" -> "whatever"))
        }
      }

      "fail on mapping with comma in raw token" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new MappedKeywordsTokenizer(Map("mock,token" -> "whatever"))
        }
      }

      "fail on mapping with period in raw token" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new MappedKeywordsTokenizer(Map("mock.token" -> "whatever"))
        }
      }

      "fail on mapping with slash in raw token" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new MappedKeywordsTokenizer(Map("mock/token" -> "whatever"))
        }
      }

      "fail on mapping with question mark in raw token" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new MappedKeywordsTokenizer(Map("mock?token" -> "whatever"))
        }
      }

      "fail on mapping with pipe in raw token" in {
        // Test
        assertThrows[IllegalArgumentException] {
          new MappedKeywordsTokenizer(Map("mock|token" -> "whatever"))
        }
      }
    }
  }
}
