package actors.counter

import common.CommonProp
import org.scalacheck.{Arbitrary, Gen}

class FifoBoundedSetProp extends CommonProp {
  property("never contain more elements than maxSize") {
    forAll(
      "maxSize" |: Gen.posNum[Int],
      "elements" |: Arbitrary.arbitrary[Seq[Int]]
    ) { (maxSize: Int, elements: Seq[Int]) =>
      whenever(maxSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up & Test
        val (instance, _) = FifoBoundedSet[Int](maxSize).addAll(elements)

        // Verify
        assert(instance.toSeq.size <= maxSize)
      }
    }
  }

  property("always include the most recently added elements") {
    forAll(
      "maxSize" |: Gen.posNum[Int],
      "elements" |: Arbitrary.arbitrary[Seq[Int]]
    ) { (maxSize: Int, elements: Seq[Int]) =>
      whenever(maxSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up & Test
        val (instance, _) = FifoBoundedSet[Int](maxSize).addAll(elements)

        // Verify
        assert(elements.takeRight(maxSize).toSet subsetOf instance.toSeq.toSet)
      }
    }
  }

  property("only evict the least recently added elements") {
    forAll(
      "maxSize" |: Gen.posNum[Int],
      "elements" |: Arbitrary.arbitrary[Seq[Int]]
    ) { (maxSize: Int, elements: Seq[Int]) =>
      whenever(maxSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up & Test
        val (_, actualEffects: Seq[FifoBoundedSet.Effect[Int]]) =
          FifoBoundedSet[Int](maxSize).addAll(elements)
        val actualEvictions: Set[Int] = actualEffects.
          collect {
            case FifoBoundedSet.AddedEvicting(value: Int) => value
          }.
          toSet

        // Verify
        assert(actualEvictions subsetOf elements.dropRight(maxSize).toSet)
      }
    }
  }

  property("never evict when not full") {
    forAll(
      "elements" |: Gen.nonEmptyListOf(Arbitrary.arbitrary[Int])
    ) { (elements: Seq[Int]) =>
      // Set up & Test
      val (instance, actualEffects: Seq[FifoBoundedSet.Effect[Int]]) =
        FifoBoundedSet[Int](elements.size).addAll(elements)

      // Verify
      val actualEvictions: Seq[FifoBoundedSet.Effect[Int]] =
        actualEffects.filter {
          _.isInstanceOf[FifoBoundedSet.AddedEvicting[Int]]
        }
      assert(instance.toSeq.toSet == elements.toSet)
      assert(actualEvictions.isEmpty)
    }
  }

  // add/addAll Equivalence
  property("add and addAll are equal given identical input") {
    forAll(
      "maxSize" |: Gen.posNum[Int],
      "elements" |: Arbitrary.arbitrary[Seq[Int]]
    ) { (maxSize: Int, elements: Seq[Int]) =>
      whenever(maxSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up
        val empty = FifoBoundedSet[Int](maxSize)

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
      "maxSize" |: Gen.posNum[Int],
      "elements" |: Gen.listOf(Gen.posNum[Int])
    ) { (maxSize: Int, elements: Seq[Int]) =>
      whenever(maxSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up
        val empty = FifoBoundedSet[Int](maxSize)

        // Test
        val (_, actualEffectsAddAll: Seq[FifoBoundedSet.Effect[Int]]) = empty.addAll(elements)
        val (_, actualEffectsAdd: Seq[FifoBoundedSet.Effect[Int]]) =
          elements.foldLeft((empty, Seq[FifoBoundedSet.Effect[Int]]())) {
            case ((accum: FifoBoundedSet[Int], effects: Seq[FifoBoundedSet.Effect[Int]]), elem: Int) =>
              val (accumNext: FifoBoundedSet[Int], effect: Option[FifoBoundedSet.Effect[Int]]) = accum.add(elem)

              (accumNext, effects ++ effect)
          }

        // Verify
        assert(actualEffectsAddAll.size <= actualEffectsAdd.size)
        actualEffectsAddAll.zip(actualEffectsAdd.takeRight(maxSize)).collect {
          case (actualEffectAddAll: FifoBoundedSet.AddedEvicting[Int], actualEffectAdd: FifoBoundedSet.Effect[Int]) =>
            assert(actualEffectAddAll == actualEffectAdd)
        }
      }
    }
  }
}
