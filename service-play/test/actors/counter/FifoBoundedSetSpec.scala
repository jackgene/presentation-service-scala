package actors.counter

import org.scalatestplus.play.PlaySpec

class FifoBoundedSetSpec extends PlaySpec {
  "A FifoBoundedSet of size 2" when {
    val empty: FifoBoundedSet[String] = FifoBoundedSet[String](2)

    "no element has been added" must {
      // Set up
      val instance: FifoBoundedSet[String] = empty

      "be empty" in {
        // Test & Verify
        assert(instance.toSeq.isEmpty)
      }

      "accept 1 new element without evicting" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffect) = instance.add("test")

        // Verify
        assert(actualEffect == FifoBoundedSet.Added[String]())
        assert(actualUpdatedInstance.toSeq == Seq("test"))
      }

      "accept 2 new elements without evicting" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(Seq("test-1", "test-2"))

        // Verify
        assert(actualEffects == Seq(FifoBoundedSet.Added(), FifoBoundedSet.Added()))
        assert(actualUpdatedInstance.toSeq == Seq("test-1", "test-2"))
      }

      "accept 3 new elements evicting the first" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(Seq("test-1", "test-2", "test-3"))

        // Verify
        assert(
          actualEffects == Seq(
            FifoBoundedSet.Added(),
            FifoBoundedSet.Added(),
            FifoBoundedSet.AddedEvicting("test-1")
          )
        )
        assert(actualUpdatedInstance.toSeq == Seq("test-2", "test-3"))
      }
    }

    "1 element has been added" must {
      // Set up
      val (instance: FifoBoundedSet[String], _) = empty.add("test-1")

      "contain the element" in {
        // Test & Verify
        assert(instance.toSeq == Seq("test-1"))
      }

      "accept 1 new element without evicting" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffect) = instance.add("test-2")

        // Verify
        assert(actualEffect == FifoBoundedSet.Added[String]())
        assert(actualUpdatedInstance.toSeq == Seq("test-1", "test-2"))
      }

      "not accept the existing element again" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffect) = instance.add("test-1")

        // Verify
        assert(actualEffect == FifoBoundedSet.NotAdded[String]())
        assert(actualUpdatedInstance.toSeq == Seq("test-1"))
      }

      "accept 2 new elements evicting the existing elements" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(Seq("test-2", "test-3"))

        // Verify
        assert(
          actualEffects == Seq(
            FifoBoundedSet.Added(),
            FifoBoundedSet.AddedEvicting("test-1")
          )
        )
        assert(actualUpdatedInstance.toSeq == Seq("test-2", "test-3"))
      }
    }

    "2 elements have been added" must {
      // Set up
      val (instance: FifoBoundedSet[String], _) = empty.addAll(Seq("test-1", "test-2"))

      "contain the elements" in {
        // Test & Verify
        assert(instance.toSeq == Seq("test-1", "test-2"))
      }

      "accept 1 new element evicting the first existing element" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffect) = instance.add("test-3")

        // Verify
        assert(actualEffect == FifoBoundedSet.AddedEvicting("test-1"))
        assert(actualUpdatedInstance.toSeq == Seq("test-2", "test-3"))
      }

      "not accept an existing element again, but update its insertion order" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffect) = instance.add("test-1")

        // Verify
        assert(actualEffect == FifoBoundedSet.NotAdded[String]())
        assert(actualUpdatedInstance.toSeq == Seq("test-2", "test-1"))
      }

      "accept 2 new elements evicting all existing elements" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(Seq("test-3", "test-4"))

        // Verify
        assert(
          actualEffects == Seq(
            FifoBoundedSet.AddedEvicting("test-1"),
            FifoBoundedSet.AddedEvicting("test-2")
          )
        )
        assert(actualUpdatedInstance.toSeq == Seq("test-3", "test-4"))
      }

      "not accept 2 existing elements, but update their insertion order" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(Seq("test-2", "test-1"))

        // Verify
        assert(
          actualEffects == Seq(
            FifoBoundedSet.NotAdded(),
            FifoBoundedSet.NotAdded()
          )
        )
        assert(actualUpdatedInstance.toSeq == Seq("test-2", "test-1"))
      }

      "accept a new element, but not an existing element, updating the insertion order of the existing element" in {
        // Set up & Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(Seq("test-1", "test-3"))

        // Verify
        assert(
          actualEffects == Seq(
            FifoBoundedSet.NotAdded(),             // But test-1 should no longer be "first in"
            FifoBoundedSet.AddedEvicting("test-2") // test-2 is evicted
          )
        )
        assert(actualUpdatedInstance.toSeq == Seq("test-1", "test-3"))
      }
    }
  }
}
