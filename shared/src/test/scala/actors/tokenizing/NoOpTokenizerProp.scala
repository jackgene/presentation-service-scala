package actors.tokenizing

import common.CommonProp
import org.scalacheck.Gen

class NoOpTokenizerProp extends CommonProp {
  property("never extract tokens") {
    forAll(
      "text" |: Gen.asciiPrintableStr
    ) { (text: String) =>
      // Test
      val actualTokens: Seq[String] = NoOpTokenizer(text)

      // Verify
      assert(actualTokens.isEmpty)
    }
  }
}
