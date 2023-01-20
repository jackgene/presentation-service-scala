package actors.counter

import common.CommonProp
import org.scalacheck.Gen

class MultiSetProp extends CommonProp {
  property("counts by element and elements by count reciprocate") {
    forAll(
      "increments" |: Gen.nonEmptyListOf(Gen.asciiStr),
      "decrements" |: Gen.nonEmptyListOf(Gen.asciiStr)
    ) { (increments: Seq[String], decrements: Seq[String]) =>
      // Test
      val instance: MultiSet[String] = decrements.foldLeft(
        increments.foldLeft(MultiSet[String]()) {
          (accum: MultiSet[String], increment: String) => accum.incremented(increment)
        }
      ) {
        (accum: MultiSet[String], decrement: String) => accum.decremented(decrement)
      }

      // Verify
      assert(instance.countsByElement.size == instance.elementsByCount.values.flatten.size)
      assert(instance.countsByElement.values.forall { instance.elementsByCount.isDefinedAt })
    }
  }
}
