package actors.tokenizing

import org.scalatestplus.play.PlaySpec

class NoOpTokenizerSpec extends PlaySpec {
  "The NoOpTokenizer" must {
    "never extract tokens" in {
      // Test
      val actualTokens = NoOpTokenizer("lorem ipsum dolor sit amet")

      // Verify
      assert(actualTokens.isEmpty)
    }
  }
}
