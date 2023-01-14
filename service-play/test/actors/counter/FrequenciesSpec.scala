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
        updated("test-1", -1). // test-1: 0
        updated("test-1", 2).  // test-1: 2
        updated("test-1", 3).  // test-1: 5
        updated("test-1", -4). // test-1: 1
        updated("test-2", 5)   // test-2: 5

      // Verify
      assert(instance.countsByItem == Map("test-1" -> 1, "test-2" -> 5))
      assert(
        instance.itemsByCount == Map(1 -> Seq("test-1"), 5 -> Seq("test-2"))
      )
    }

    "append item to itemsByCount when incremented" in {
      // Set up & Test
      val instance: Frequencies[String] = empty.
        updated("test-1", 1).
        updated("test-2", 1) // Incrementing from 0 -> 1

      // Verify
      // Incremented value should be appended
      assert(instance.itemsByCount == Map(1 -> Seq("test-1", "test-2")))
    }

    "prepend item to itemsByCount when decremented" in {
      // Set up & Test
      val instance: Frequencies[String] = empty.
        updated("test-1", 1).
        updated("test-2", 2).
        updated("test-2", -1) // Decrement from 2 -> 1

      // Verify
      // Decremented value should be prepended
      assert(instance.itemsByCount == Map(1 -> Seq("test-2", "test-1")))
    }

    "never record counts less than zero" in {
      // Set up & Test
      val instance: Frequencies[String] = empty.
        updated("test", -1)

      // Verify
      assert(instance.countsByItem.isEmpty)
      assert(instance.itemsByCount.isEmpty)
    }

    "never record counts greater than the maximum" in {
      // Set up & Test
      val instance: Frequencies[String] = empty.
        updated("test", Int.MaxValue).
        updated("test", 1) // Should not overflow

      // Verify
      assert(instance.countsByItem == Map("test" -> Int.MaxValue))
      assert(
        instance.itemsByCount == Map(Int.MaxValue -> Seq("test"))
      )
    }

    "no-op when updated with a 0 delta" in {
      // Set up
      val instance: Frequencies[String] = empty.
        updated("test", 42)

      // Test
      val updated = instance.updated("test", 0)

      // Verify
      assert(instance == updated)
      assert(instance eq updated)
    }

    "omit zero counts" in {
      // Set up & Test
      val instance: Frequencies[String] = empty.
        updated("test", 1).
        updated("test", -1)

      // Verify
      assert(instance.countsByItem.isEmpty)
      assert(instance.itemsByCount.isEmpty)
    }
  }
}
