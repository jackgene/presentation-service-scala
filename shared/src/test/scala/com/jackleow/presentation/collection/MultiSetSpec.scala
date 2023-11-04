package com.jackleow.presentation.collection

import org.scalatest.wordspec.AnyWordSpec

class MultiSetSpec extends AnyWordSpec {
  "A MultiSet" must {
    val empty: MultiSet[String] = MultiSet()

    "be initially empty" in {
      // Set up & Test
      val instance: MultiSet[String] = empty

      // Verify
      assert(instance.countsByElement.isEmpty)
      assert(instance.elementsByCount.isEmpty)
    }

    "record correct counts" in {
      // Set up & Test
      val instance: MultiSet[String] = empty.
        // "test-1"
        -("test-1").  // 0
        +("test-1").  // 1
        +("test-1").  // 2
        -("test-1").  // 1
        +("test-1").  // 2
        +("test-1").  // 3
        +("test-1").  // 4
        +("test-1").  // 5
        -("test-1").  // 4
        -("test-1").  // 3
        -("test-1").  // 2
        -("test-1").  // 1
        // "test-2"
        +("test-2").  // 1
        +("test-2").  // 2
        +("test-2").  // 3
        +("test-2").  // 4
        +("test-2")   // 5

      // Verify
      assert(instance.countsByElement == Map("test-1" -> 1, "test-2" -> 5))
      assert(
        instance.elementsByCount == Map(1 -> Seq("test-1"), 5 -> Seq("test-2"))
      )
    }

    "never record counts less than zero" in {
      // Set up
      val instanceSetup: MultiSet[String] = empty

      // Test
      val instance: MultiSet[String] = instanceSetup - "test"

      // Verify
      assert(instance.countsByElement.isEmpty)
      assert(instance.elementsByCount.isEmpty)
      // Instance equality is not strictly necessary, just verify optimization
      // Ok to remove if optimization no longer appropriate
      assert(instance eq instanceSetup)
    }

    "never record counts greater than the maximum" in {
      // Set up
      // Private API: manually set count to Int.MaxValue
      val instanceSetup: MultiSet[String] = new MultiSet[String](
        countsByElement = Map("test" -> Int.MaxValue),
        elementsByCount = Map(Int.MaxValue -> Seq("test"))
      )

      // Test
      val instance: MultiSet[String] = instanceSetup + "test" // Should not overflow

      // Verify
      assert(instance.countsByElement == Map("test" -> Int.MaxValue))
      assert(
        instance.elementsByCount == Map(Int.MaxValue -> Seq("test"))
      )
      // Instance equality is not strictly necessary, just verify optimization
      // Ok to remove if optimization no longer appropriate
      assert(instance eq instanceSetup)
    }

    "append element to elementsByCount when incremented" in {
      // Set up & Test
      val instance: MultiSet[String] = empty + "test-1" + "test-2"

      // Verify
      // Incremented value should be appended
      assert(instance.elementsByCount == Map(1 -> Seq("test-1", "test-2")))
    }

    "prepend element to elementsByCount when decremented" in {
      // Set up
      val instanceSetup: MultiSet[String] = empty + "test-1" + "test-2" + "test-2"

      // Test
      val instance: MultiSet[String] = instanceSetup - "test-2" // Decrement from 2 -> 1

      // Verify
      // Decremented value should be prepended
      assert(instance.elementsByCount == Map(1 -> Seq("test-2", "test-1")))
    }

    "omit zero counts" in {
      // Set up & Test
      val instance: MultiSet[String] = empty + "test" - "test"

      // Verify
      assert(instance.countsByElement.isEmpty)
      assert(instance.elementsByCount.isEmpty)
    }
  }
}
