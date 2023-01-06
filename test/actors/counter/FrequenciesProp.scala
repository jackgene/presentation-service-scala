package actors.counter

import common.CommonProp
import org.scalacheck.Gen

class FrequenciesProp extends CommonProp {
  property("never record a negative count") {
    forAll(
      "deltas" |: Gen.nonEmptyListOf(Gen.chooseNum[Int](Int.MinValue, Int.MaxValue))
    ) { (deltas: Seq[Int]) =>
      // Test
      val instance: Frequencies[Unit] = deltas.foldLeft(Frequencies[Unit]()) {
        (accum: Frequencies[Unit], delta: Int) => accum.updated((), delta)
      }

      // Verify
      assert(instance.countsByItem.getOrElse((), 0) >= 0)
    }
  }

  property("incrementing never decreases count") {
    forAll(
      "initialCount"  |: Gen.chooseNum[Int](0, Int.MaxValue),
      "delta"         |: Gen.chooseNum[Int](1, Int.MaxValue)
    ) { (initialCount: Int, delta: Int) =>
      whenever(initialCount >= 0 && delta >= 1) {
        // Set up
        val initialInstance: Frequencies[Unit] = Frequencies[Unit]().updated((), initialCount)

        // Test
        val finalInstance: Frequencies[Unit] = initialInstance.updated((), delta)

        // Verify
        assert(finalInstance.countsByItem.getOrElse((), 0) >= initialInstance.countsByItem.getOrElse((), 0))
      }
    }
  }

  property("decrementing never increases count") {
    forAll(
      "initialCount"  |: Gen.chooseNum[Int](0, Int.MaxValue),
      "delta"         |: Gen.chooseNum[Int](Int.MinValue, -1)
    ) { (initialCount: Int, delta: Int) =>
      whenever(initialCount >= 0 && delta <= -1) {
        // Set up
        val initialInstance: Frequencies[Unit] = Frequencies[Unit]().updated((), initialCount)

        // Test
        val finalInstance: Frequencies[Unit] = initialInstance.updated((), delta)

        // Verify
        assert(finalInstance.countsByItem.getOrElse((), 0) <= initialInstance.countsByItem.getOrElse((), 0))
      }
    }
  }
}
