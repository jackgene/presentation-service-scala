package com.jackleow.presentation.tokenizing

import org.scalatest.wordspec.AnyWordSpec

class NoOpTokenizerSpec extends AnyWordSpec:
  "The NoOpTokenizer" must:
    "never extract tokens" in:
      // Test
      val actualTokens = NoOpTokenizer("lorem ipsum dolor sit amet")

      // Verify
      assert(actualTokens.isEmpty)
