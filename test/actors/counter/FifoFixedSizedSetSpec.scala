package actors.counter

import org.scalatestplus.play.PlaySpec

class FifoFixedSizedSetSpec extends PlaySpec {
  "A FifoFixedSizeSet of size 2" when {
    val empty: FifoFixedSizedSet[String] = FifoFixedSizedSet[String](2)

    "no items are added" must {
      // Set up
      val instance: FifoFixedSizedSet[String] = empty

      "be empty" in {
        // Test & Verify
        assert(instance.toSeq.isEmpty)
      }

      "accept 1 new item without evicting" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffect) = instance.add("test")

        // Verify
        assert(actualEffect == FifoFixedSizedSet.Added())
        assert(actualUpdatedInstance.toSeq == Seq("test"))
      }

      "accept 2 new items without evicting" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(Seq("test-1", "test-2"))

        // Verify
        assert(actualEffects == Seq(FifoFixedSizedSet.Added(), FifoFixedSizedSet.Added()))
        assert(actualUpdatedInstance.toSeq == Seq("test-1", "test-2"))
      }

      "accept 3 new items evicting the first" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(Seq("test-1", "test-2", "test-3"))

        // Verify
        assert(
          actualEffects == Seq(
            FifoFixedSizedSet.Added(),
            FifoFixedSizedSet.Added(),
            FifoFixedSizedSet.AddedEvicting("test-1")
          )
        )
        assert(actualUpdatedInstance.toSeq == Seq("test-2", "test-3"))
      }
    }

    "1 item has been added" must {
      // Set up
      val (instance: FifoFixedSizedSet[String], _) = empty.add("test-1")

      "contain the item" in {
        // Test & Verify
        assert(instance.toSeq == Seq("test-1"))
      }

      "accept 1 new item without evicting" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffect) = instance.add("test-2")

        // Verify
        assert(actualEffect == FifoFixedSizedSet.Added())
        assert(actualUpdatedInstance.toSeq == Seq("test-1", "test-2"))
      }

      "not accept the existing item again" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffect) = instance.add("test-1")

        // Verify
        assert(actualEffect == FifoFixedSizedSet.NotAdded())
        assert(actualUpdatedInstance.toSeq == Seq("test-1"))
      }

      "accept 2 new items evicting the existing item" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(Seq("test-2", "test-3"))

        // Verify
        assert(
          actualEffects == Seq(
            FifoFixedSizedSet.Added(),
            FifoFixedSizedSet.AddedEvicting("test-1")
          )
        )
        assert(actualUpdatedInstance.toSeq == Seq("test-2", "test-3"))
      }
    }

    "2 items has been added" must {
      // Set up
      val (instance: FifoFixedSizedSet[String], _) = empty.addAll(Seq("test-1", "test-2"))

      "contain the items" in {
        // Test & Verify
        assert(instance.toSeq == Seq("test-1", "test-2"))
      }

      "accept 1 new item evicting the first existing item" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffect) = instance.add("test-3")

        // Verify
        assert(actualEffect == FifoFixedSizedSet.AddedEvicting("test-1"))
        assert(actualUpdatedInstance.toSeq == Seq("test-2", "test-3"))
      }

      "not accept an existing item again, but update its insertion order" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffect) = instance.add("test-1")

        // Verify
        assert(actualEffect == FifoFixedSizedSet.NotAdded())
        assert(actualUpdatedInstance.toSeq == Seq("test-2", "test-1"))
      }

      "accept 2 new items evicting all existing items" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(Seq("test-3", "test-4"))

        // Verify
        assert(
          actualEffects == Seq(
            FifoFixedSizedSet.AddedEvicting("test-1"),
            FifoFixedSizedSet.AddedEvicting("test-2")
          )
        )
        assert(actualUpdatedInstance.toSeq == Seq("test-3", "test-4"))
      }

      "not accept 2 existing items, but update their insertion order" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(Seq("test-2", "test-1"))

        // Verify
        assert(
          actualEffects == Seq(
            FifoFixedSizedSet.NotAdded(),
            FifoFixedSizedSet.NotAdded()
          )
        )
        assert(actualUpdatedInstance.toSeq == Seq("test-2", "test-1"))
      }

      "accept a new item, but not an existing item, updating the insertion order of the existing item" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(Seq("test-1", "test-3"))

        // Verify
        assert(
          actualEffects == Seq(
            FifoFixedSizedSet.NotAdded(),             // But test-1 should no longer be "first in"
            FifoFixedSizedSet.AddedEvicting("test-2") // test-2 is evicted
          )
        )
        assert(actualUpdatedInstance.toSeq == Seq("test-1", "test-3"))
      }
    }
  }
}
