package actors.counter

import org.scalacheck.Gen
import org.scalactic.anyvals.PosInt
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class FifoFixedSizedSetProp extends AnyPropSpec with ScalaCheckPropertyChecks {
  implicit override val generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(
    minSuccessful = 10_000,
    sizeRange = PosInt(1_000),
    workers = 2
  )

  property("never contain more items than fixed limit") {
    forAll(
      "fixedSize" |: Gen.posNum[Int],
      "items"     |: Gen.listOf(Gen.posNum[Int])
    ) { (fixedSize: Int, items: Seq[Int]) =>
      whenever(fixedSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up & Test
        val (instance, _) = FifoFixedSizedSet[Int](fixedSize).addAll(items)

        // Verify
        assert(instance.toSeq.size <= fixedSize)
      }
    }
  }

  property("always include the most recently added items") {
    forAll(
      "fixedSize" |: Gen.posNum[Int],
      "items"     |: Gen.nonEmptyListOf(Gen.posNum[Int])
    ) { (fixedSize: Int, items: Seq[Int]) =>
      whenever(fixedSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up & Test
        val (instance, _) = FifoFixedSizedSet[Int](fixedSize).addAll(items)

        // Verify
        assert(items.takeRight(fixedSize).toSet subsetOf instance.toSeq.toSet)
      }
    }
  }

  property("only evict the least recently added items") {
    forAll(
      "fixedSize" |: Gen.posNum[Int],
      "items"     |: Gen.nonEmptyListOf(Gen.posNum[Int])
    ) { (fixedSize: Int, items: Seq[Int]) =>
      whenever(fixedSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up & Test
        val (_, actualEffects: Seq[FifoFixedSizedSet.Effect[Int]]) =
          FifoFixedSizedSet[Int](fixedSize).addAll(items)
        val actualEvictions: Set[Int] = actualEffects.
          collect {
            case FifoFixedSizedSet.AddedEvicting(value: Int) => value
          }.
          toSet

        // Verify
        assert(actualEvictions subsetOf items.dropRight(fixedSize).toSet)
      }
    }
  }

  property("never evict when not full") {
    forAll(
      "items" |: Gen.nonEmptyListOf(Gen.posNum[Int])
    ) { (items: Seq[Int]) =>
      // Set up & Test
      val (instance, actualEffects: Seq[FifoFixedSizedSet.Effect[Int]]) =
        FifoFixedSizedSet[Int](items.size).addAll(items)

      // Verify
      val actualEvictions: Seq[FifoFixedSizedSet.Effect[Int]] =
        actualEffects.filter(_.isInstanceOf[FifoFixedSizedSet.AddedEvicting[Int]])
      assert(instance.toSeq.toSet == items.toSet)
      assert(actualEvictions.isEmpty)
    }
  }

  // add/addAll Equivalence
  property("add and addAll are equivalent given identical input") {
    forAll(
      "fixedSize" |: Gen.posNum[Int],
      "items"     |: Gen.listOf(Gen.posNum[Int])
    ) { (fixedSize: Int, items: Seq[Int]) =>
      whenever(fixedSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up
        val empty: FifoFixedSizedSet[Int] = FifoFixedSizedSet[Int](fixedSize)

        // Test
        val (instanceUsingAddAll: FifoFixedSizedSet[Int], _) = empty.addAll(items)
        val instanceUsingAdd: FifoFixedSizedSet[Int] =
          items.foldLeft(empty) { (accum: FifoFixedSizedSet[Int], item: Int) =>
            val (accumNext: FifoFixedSizedSet[Int], _) = accum.add(item)

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
      "items"     |: Gen.listOf(Gen.posNum[Int])
    ) { (fixedSize: Int, items: Seq[Int]) =>
      whenever(fixedSize > 0) { // When shrinking ScalaCheck can go beyond "posNum" range
        // Set up
        //        println(s"putting ${items.size} items in set of size ${fixedSize}")
        //        if (items.size > items.toSet.size) {
        //          println("contains dups")
        //        }
        val empty: FifoFixedSizedSet[Int] = FifoFixedSizedSet[Int](fixedSize)

        // Test
        val (_, actualEffectsAddAll: Seq[FifoFixedSizedSet.Effect[Int]]) = empty.addAll(items)
        val (_, actualEffectsAdd: Seq[FifoFixedSizedSet.Effect[Int]]) =
          items.foldLeft((empty, Seq[FifoFixedSizedSet.Effect[Int]]())) {
            case ((accum: FifoFixedSizedSet[Int], effects: Seq[FifoFixedSizedSet.Effect[Int]]), item: Int) =>
              val (accumNext: FifoFixedSizedSet[Int], effect: FifoFixedSizedSet.Effect[Int]) = accum.add(item)

              (accumNext, effects :+ effect)
          }

        // Verify
        assert(actualEffectsAddAll == actualEffectsAdd)
      }
    }
  }
}
