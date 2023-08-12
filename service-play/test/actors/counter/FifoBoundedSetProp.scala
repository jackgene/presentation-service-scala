package actors.counter

import common.CommonProp
import org.scalacheck.Gen

class FifoBoundedSetProp extends CommonProp {
  property("never contain more elements than fixed limit") {
    forAll(
      "fixedSize" |: Gen.posNum[Int],
      "elements"  |: Gen.listOf(Gen.posNum[Int])
    ) { (fixedSize: Int, elements: Seq[Int]) =>
      whenever(fixedSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up & Test
        val (instance, _) = FifoBoundedSet[Int](fixedSize).addAll(elements)

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
        val (instance, _) = FifoBoundedSet[Int](fixedSize).addAll(elements)

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
        val (_, actualEffects: Seq[FifoBoundedSet.Effect[Int]]) =
          FifoBoundedSet[Int](fixedSize).addAll(elements)
        val actualEvictions: Set[Int] = actualEffects.
          collect {
            case FifoBoundedSet.AddedEvicting(value: Int) => value
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
      val (instance, actualEffects: Seq[FifoBoundedSet.Effect[Int]]) =
        FifoBoundedSet[Int](elements.size).addAll(elements)

      // Verify
      val actualEvictions: Seq[FifoBoundedSet.Effect[Int]] =
        actualEffects.filter(_.isInstanceOf[FifoBoundedSet.AddedEvicting[Int]])
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
        val empty: FifoBoundedSet[Int] = FifoBoundedSet[Int](fixedSize)

        // Test
        val (instanceUsingAddAll: FifoBoundedSet[Int], _) = empty.addAll(elements)
        val instanceUsingAdd: FifoBoundedSet[Int] =
          elements.foldLeft(empty) { (accum: FifoBoundedSet[Int], elem: Int) =>
            val (accumNext: FifoBoundedSet[Int], _) = accum.add(elem)

            accumNext
          }

        // Verify
        assert(instanceUsingAddAll == instanceUsingAdd)
      }
    }
  }

  property("add and addAll produces identical effects given identical input") {
    forAll(
      "fixedSize" |: Gen.posNum[Int],
      "elements"  |: Gen.listOf(Gen.posNum[Int])
    ) { (fixedSize: Int, elements: Seq[Int]) =>
      whenever(fixedSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up
        val empty: FifoBoundedSet[Int] = FifoBoundedSet[Int](fixedSize)

        // Test
        val (_, actualEffectsAddAll: Seq[FifoBoundedSet.Effect[Int]]) = empty.addAll(elements)
        val (_, actualEffectsAdd: Seq[FifoBoundedSet.Effect[Int]]) =
          elements.foldLeft((empty, Seq[FifoBoundedSet.Effect[Int]]())) {
            case ((accum: FifoBoundedSet[Int], effects: Seq[FifoBoundedSet.Effect[Int]]), elem: Int) =>
              val (accumNext: FifoBoundedSet[Int], effect: FifoBoundedSet.Effect[Int]) = accum.add(elem)

              (accumNext, effects :+ effect)
          }

        // Verify
        assert(actualEffectsAddAll == actualEffectsAdd)
      }
    }
  }
}
