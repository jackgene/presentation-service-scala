package actors.counter

import org.scalatestplus.play.PlaySpec

class FrequenciesSpec extends PlaySpec {
  "Frequencies" must {
    val empty = Frequencies[String]()

    "be initially empty" in {
      // Set up & Test
      val instance: Frequencies[String] = empty

      // Verify
      assert(instance.countsByItem.isEmpty)
      assert(instance.itemsByCount.isEmpty)
    }

    "record correct counts" in {
      // Set up & Test
      val instance: Frequencies[String] = empty.
        // "test-1"
        decremented("test-1").  // 0
        incremented("test-1").  // 1
        incremented("test-1").  // 2
        decremented("test-1").  // 1
        incremented("test-1").  // 2
        incremented("test-1").  // 3
        incremented("test-1").  // 4
        incremented("test-1").  // 5
        decremented("test-1").  // 4
        decremented("test-1").  // 3
        decremented("test-1").  // 2
        decremented("test-1").  // 1
        // "test-2"
        incremented("test-2").  // 1
        incremented("test-2").  // 2
        incremented("test-2").  // 3
        incremented("test-2").  // 4
        incremented("test-2")   // 5

      // Verify
      assert(instance.countsByItem == Map("test-1" -> 1, "test-2" -> 5))
      assert(
        instance.itemsByCount == Map(1 -> Seq("test-1"), 5 -> Seq("test-2"))
      )
    }

    "append item to itemsByCount when incremented" in {
      // Set up & Test
      val instance: Frequencies[String] = empty.
        incremented("test-1").
        incremented("test-2")

      // Verify
      // Incremented value should be appended
      assert(instance.itemsByCount == Map(1 -> Seq("test-1", "test-2")))
    }

    "prepend item to itemsByCount when decremented" in {
      // Set up
      val instanceSetup: Frequencies[String] = empty.
        incremented("test-1").
        incremented("test-2").
        incremented("test-2")

      // Test
      val instance: Frequencies[String] = instanceSetup.
        decremented("test-2") // Decrement from 2 -> 1

      // Verify
      // Decremented value should be prepended
      assert(instance.itemsByCount == Map(1 -> Seq("test-2", "test-1")))
    }

    "never record counts less than zero" in {
      // Set up
      val instanceSetup: Frequencies[String] = empty

      // Test
      val instance: Frequencies[String] = instanceSetup.
        decremented("test")

      // Verify
      assert(instance.countsByItem.isEmpty)
      assert(instance.itemsByCount.isEmpty)
      // Instance equality is not strictly necessary, just verify optimization
      // Ok to remove if optimization no longer appropriate
      assert(instance eq instanceSetup)
    }

    "never record counts greater than the maximum" in {
      // Set up
      // Private API: manually set count to Int.MaxValue
      val instanceSetup: Frequencies[String] = new Frequencies[String](
        countsByItem = Map("test" -> Int.MaxValue),
        itemsByCount = Map(Int.MaxValue -> Seq("test"))
      )

      // Test
      val instance: Frequencies[String] = instanceSetup.
        incremented("test") // Should not overflow

      // Verify
      assert(instance.countsByItem == Map("test" -> Int.MaxValue))
      assert(
        instance.itemsByCount == Map(Int.MaxValue -> Seq("test"))
      )
      // Instance equality is not strictly necessary, just verify optimization
      // Ok to remove if optimization no longer appropriate
      assert(instance eq instanceSetup)
    }

    "omit zero counts" in {
      // Set up & Test
      val instance: Frequencies[String] = empty.
        incremented("test").
        decremented("test")

      // Verify
      assert(instance.countsByItem.isEmpty)
      assert(instance.itemsByCount.isEmpty)
    }
  }
}
