package com.jackleow.presentation.collection

import com.jackleow.presentation.CommonProp
import org.scalacheck.Gen

import scala.util.Random

class MultiSetProp extends CommonProp {
  val duplicativeElements: Gen[Seq[String]] =
    Gen.nonEmptyListOf(
      for {
        element: String <- Gen.alphaLowerStr
        count: Int <- Gen.posNum[Int]
      } yield (element, count)
    ).map { (elementsAndCounts: List[(String, Int)]) =>
      Random.shuffle(
        for {
          (value: String, count: Int) <- elementsAndCounts
          _ <- 1 to count
        } yield value
      )
    }

  property("counts by element and elements by count reciprocate") {
    forAll(
      "increments" |: duplicativeElements,
      "decrements" |: duplicativeElements
    ) { (increments: Seq[String], decrements: Seq[String]) =>
      // Test
      val instance: MultiSet[String] = decrements.foldLeft(
        increments.foldLeft(MultiSet[String]()) {
          (accum: MultiSet[String], increment: String) => accum + increment
        }
      ) {
        (accum: MultiSet[String], decrement: String) => accum - decrement
      }

      // Verify
      assert(
        instance.countsByElement.forall {
          case (element: String, count: Int) => instance.elementsByCount(count).contains(element)
        }
      )
      assert(
        instance.elementsByCount.forall {
          case (count: Int, elements: Seq[String]) => elements.forall { (element: String) =>
            instance.countsByElement(element) == count
          }
        }
      )
    }
  }

  property("never record zero counts") {
    forAll(
      "increments" |: duplicativeElements,
      "decrements" |: duplicativeElements
    ) { (increments: Seq[String], decrements: Seq[String]) =>
      // Test
      val instance: MultiSet[String] = decrements.foldLeft(
        increments.foldLeft(MultiSet[String]()) {
          (accum: MultiSet[String], increment: String) => accum + increment
        }
      ) {
        (accum: MultiSet[String], decrement: String) => accum - decrement
      }

      // Verify
      assert(instance.countsByElement.values.forall { _ > 0 })
      assert(instance.elementsByCount.keys.forall { _ > 0 })
    }
  }

  property("most recently incremented element is the last of elements by count") {
    forAll("elements" |: duplicativeElements) { (elements: Seq[String]) =>
      elements.foldLeft(MultiSet[String]()) {
        (accum: MultiSet[String], element: String) =>
          val nextAccum: MultiSet[String] = accum + element
          assert(nextAccum.elementsByCount(nextAccum.countsByElement(element)).last == element)

          nextAccum
      }
    }
  }

  property("most recently decremented element is the first of elements by count") {
    forAll("elements" |: duplicativeElements) { (elements: Seq[String]) =>
      val instance: MultiSet[String] = elements.foldLeft(MultiSet[String]()) {
        (accum: MultiSet[String], element: String) => accum + element
      }

      elements.foldLeft(instance) {
        (accum: MultiSet[String], element: String) =>
          val nextAccum: MultiSet[String] = accum - element
          for {
            // Key is removed when decremented to 0
            count <- nextAccum.countsByElement.get(element)
          } assert(nextAccum.elementsByCount(count).head == element)

          nextAccum
      }
    }
  }
}
