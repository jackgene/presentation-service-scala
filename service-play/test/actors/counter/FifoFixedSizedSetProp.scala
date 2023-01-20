package actors.counter

import common.CommonProp
import org.scalacheck.Gen

class FifoFixedSizedSetProp extends CommonProp {
  property("never contain more elements than fixed limit") {
    forAll(
      "fixedSize" |: Gen.posNum[Int],
      "elements"  |: Gen.listOf(Gen.posNum[Int])
    ) { (fixedSize: Int, elements: Seq[Int]) =>
      whenever(fixedSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up & Test
        val (instance, _) = FifoFixedSizedSet[Int](fixedSize).addAll(elements)

        // Verify
        assert(instance.toSeq.size <= fixedSize)
      }
    }
  }

  property("always include the most recently added elements") {
    forAll(
      "fixedSize" |: Gen.posNum[Int],
      "elements"  |: Gen.nonEmptyListOf(Gen.posNum[Int])
    ) { (fixedSize: Int, elements: Seq[Int]) =>
      whenever(fixedSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up & Test
        val (instance, _) = FifoFixedSizedSet[Int](fixedSize).addAll(elements)

        // Verify
        assert(elements.takeRight(fixedSize).toSet subsetOf instance.toSeq.toSet)
      }
    }
  }

  property("only evict the least recently added elements") {
    forAll(
      "fixedSize" |: Gen.posNum[Int],
      "elements"  |: Gen.nonEmptyListOf(Gen.posNum[Int])
    ) { (fixedSize: Int, elements: Seq[Int]) =>
      whenever(fixedSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up & Test
        val (_, actualEffects: Seq[FifoFixedSizedSet.Effect[Int]]) =
          FifoFixedSizedSet[Int](fixedSize).addAll(elements)
        val actualEvictions: Set[Int] = actualEffects.
          collect {
            case FifoFixedSizedSet.AddedEvicting(value: Int) => value
          }.
          toSet

        // Verify
        assert(actualEvictions subsetOf elements.dropRight(fixedSize).toSet)
      }
    }
  }

  property("never evict when not full") {
    forAll(
      "elements" |: Gen.nonEmptyListOf(Gen.posNum[Int])
    ) { (elements: Seq[Int]) =>
      // Set up & Test
      val (instance, actualEffects: Seq[FifoFixedSizedSet.Effect[Int]]) =
        FifoFixedSizedSet[Int](elements.size).addAll(elements)

      // Verify
      val actualEvictions: Seq[FifoFixedSizedSet.Effect[Int]] =
        actualEffects.filter(_.isInstanceOf[FifoFixedSizedSet.AddedEvicting[Int]])
      assert(instance.toSeq.toSet == elements.toSet)
      assert(actualEvictions.isEmpty)
    }
  }

  // add/addAll Equivalence
  property("add and addAll are equivalent given identical input") {
    forAll(
      "fixedSize" |: Gen.posNum[Int],
      "elements"  |: Gen.listOf(Gen.posNum[Int])
    ) { (fixedSize: Int, elements: Seq[Int]) =>
      whenever(fixedSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up
        val empty: FifoFixedSizedSet[Int] = FifoFixedSizedSet[Int](fixedSize)

        // Test
        val (instanceUsingAddAll: FifoFixedSizedSet[Int], _) = empty.addAll(elements)
        val instanceUsingAdd: FifoFixedSizedSet[Int] =
          elements.foldLeft(empty) { (accum: FifoFixedSizedSet[Int], elem: Int) =>
            val (accumNext: FifoFixedSizedSet[Int], _) = accum.add(elem)

            accumNext
          }

        // Verify
        assert(instanceUsingAddAll == instanceUsingAdd)
      }
    }
  }

  property("add and addAll produces identical events given identical input") {
    forAll(
      "fixedSize" |: Gen.posNum[Int],
      "elements"  |: Gen.listOf(Gen.posNum[Int])
    ) { (fixedSize: Int, elements: Seq[Int]) =>
      whenever(fixedSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up
        val empty: FifoFixedSizedSet[Int] = FifoFixedSizedSet[Int](fixedSize)

        // Test
        val (_, actualEffectsAddAll: Seq[FifoFixedSizedSet.Effect[Int]]) = empty.addAll(elements)
        val (_, actualEffectsAdd: Seq[FifoFixedSizedSet.Effect[Int]]) =
          elements.foldLeft((empty, Seq[FifoFixedSizedSet.Effect[Int]]())) {
            case ((accum: FifoFixedSizedSet[Int], effects: Seq[FifoFixedSizedSet.Effect[Int]]), elem: Int) =>
              val (accumNext: FifoFixedSizedSet[Int], effect: FifoFixedSizedSet.Effect[Int]) = accum.add(elem)

              (accumNext, effects :+ effect)
          }

        // Verify
        assert(actualEffectsAddAll == actualEffectsAdd)
      }
    }
  }
}
