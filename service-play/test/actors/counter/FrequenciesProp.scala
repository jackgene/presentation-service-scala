package actors.counter

import common.CommonProp
import org.scalacheck.Gen

class FrequenciesProp extends CommonProp {
  property("counts by items and items by counts must reciprocate") {
    forAll(
      "increments" |: Gen.nonEmptyListOf(Gen.asciiStr),
      "decrements" |: Gen.nonEmptyListOf(Gen.asciiStr)
    ) { (increments: Seq[String], decrements: Seq[String]) =>
      // Test
      val instance: Frequencies[String] = decrements.foldLeft(
        increments.foldLeft(Frequencies[String]()) {
          (accum: Frequencies[String], increment: String) => accum.incremented(increment)
        }
      ) {
        (accum: Frequencies[String], decrement: String) => accum.decremented(decrement)
      }

      // Verify
      assert(instance.countsByItem.size == instance.itemsByCount.values.flatten.size)
      assert(instance.countsByItem.values.forall { instance.itemsByCount.isDefinedAt })
    }
  }

  property("never record a negative count") {
    forAll(
      "incrementNotDecrements" |: Gen.nonEmptyListOf(Gen.oneOf(true, false))
    ) { (incrementNotDecrements: Seq[Boolean]) =>
      // Test
      val instance: Frequencies[Unit] = incrementNotDecrements.foldLeft(Frequencies[Unit]()) {
        (accum: Frequencies[Unit], incrementNotDecrements: Boolean) =>
          if (incrementNotDecrements) accum.incremented(())
          else accum.decremented(())
      }

      // Verify
      assert(instance.countsByItem.getOrElse((), 0) >= 0)
      assert(instance.itemsByCount.values.forall { _.size == 1 })
      assert(instance.itemsByCount.keySet.forall { _ >= 0 })
    }
  }

  property("never record a greater count than the number of operations") {
    forAll(
      "incrementNotDecrements" |: Gen.nonEmptyListOf(Gen.oneOf(true, false))
    ) { (incrementNotDecrements: Seq[Boolean]) =>
      // Test
      val instance: Frequencies[Unit] = incrementNotDecrements.foldLeft(Frequencies[Unit]()) {
        (accum: Frequencies[Unit], incrementNotDecrements: Boolean) =>
          if (incrementNotDecrements) accum.incremented(())
          else accum.decremented(())
      }

      // Verify
      assert(instance.countsByItem.getOrElse((), 0) <= incrementNotDecrements.size)
      assert(instance.itemsByCount.values.forall { _.size == 1 })
      assert(instance.itemsByCount.keySet.forall { _ <= incrementNotDecrements.size })
    }
  }
}
