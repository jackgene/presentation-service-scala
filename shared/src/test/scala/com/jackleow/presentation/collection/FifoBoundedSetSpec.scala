package com.jackleow.presentation.collection

import org.scalatest.wordspec.AnyWordSpec

class FifoBoundedSetSpec extends AnyWordSpec:
  "A FifoBoundedSet of size 2" when:
    val empty: FifoBoundedSet[String] = FifoBoundedSet(2)

    "no element has been added" must:
      // Set up
      val instance: FifoBoundedSet[String] = empty

      "be empty" in:
        // Test & Verify
        assert(instance.toSeq.isEmpty)

      "accept 1 new element without evicting" in:
        // Test
        val (actualUpdatedInstance, actualEffect) = instance.add("test")

        // Verify
        assert(actualEffect.contains(FifoBoundedSet.Added[String]("test")))
        assert(actualUpdatedInstance.toSeq == Seq("test"))

      "accept 2 new elements without evicting" in:
        // Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(Seq("test-1", "test-2"))

        // Verify
        assert(
          actualEffects == Seq(
            FifoBoundedSet.Added("test-1"),
            FifoBoundedSet.Added("test-2")
          )
        )
        assert(actualUpdatedInstance.toSeq == Seq("test-1", "test-2"))

      "accept 3 new elements ignoring the first" in:
        // Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(
            Seq(
              "test-1", // test-1 is added, and evicted within this call
              "test-2",
              "test-3"
            )
          )

        // Verify
        assert(
          actualEffects == Seq(
            FifoBoundedSet.Added("test-2"),
            FifoBoundedSet.Added("test-3")
          )
        )
        assert(actualUpdatedInstance.toSeq == Seq("test-2", "test-3"))

    "1 element has been added" must:
      // Set up
      val (instance: FifoBoundedSet[String], _) = empty.add("test-1")

      "contain the element" in:
        // Test & Verify
        assert(instance.toSeq == Seq("test-1"))

      "accept 1 new element without evicting" in:
        // Test
        val (actualUpdatedInstance, actualEffect) = instance.add("test-2")

        // Verify
        assert(actualEffect.contains(FifoBoundedSet.Added[String]("test-2")))
        assert(actualUpdatedInstance.toSeq == Seq("test-1", "test-2"))

      "not accept the existing element again" in:
        // Test
        val (actualUpdatedInstance, actualEffect) = instance.add("test-1")

        // Verify
        assert(actualEffect.isEmpty)
        assert(actualUpdatedInstance.toSeq == Seq("test-1"))

      "accept 2 new elements evicting the existing element" in:
        // Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(Seq("test-2", "test-3"))

        // Verify
        assert(
          actualEffects == Seq(
            FifoBoundedSet.Added("test-2"),
            FifoBoundedSet.AddedEvicting("test-3", "test-1")
          )
        )
        assert(actualUpdatedInstance.toSeq == Seq("test-2", "test-3"))

    "2 elements have been added" must:
      // Set up
      val (instance: FifoBoundedSet[String], _) = empty.addAll(Seq("test-1", "test-2"))

      "contain the elements" in:
        // Test & Verify
        assert(instance.toSeq == Seq("test-1", "test-2"))

      "accept 1 new element evicting the first existing element" in:
        // Test
        val (actualUpdatedInstance, actualEffect) = instance.add("test-3")

        // Verify
        assert(actualEffect.contains(FifoBoundedSet.AddedEvicting("test-3", "test-1")))
        assert(actualUpdatedInstance.toSeq == Seq("test-2", "test-3"))

      "not accept an existing element again, but update its insertion order" in:
        // Test
        val (actualUpdatedInstance, actualEffect) = instance.add("test-1")

        // Verify
        assert(actualEffect.isEmpty)
        assert(actualUpdatedInstance.toSeq == Seq("test-2", "test-1"))

      "accept 2 new elements evicting all existing elements" in:
        // Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(Seq("test-3", "test-4"))

        // Verify
        assert(
          actualEffects == Seq(
            FifoBoundedSet.AddedEvicting("test-3", "test-1"),
            FifoBoundedSet.AddedEvicting("test-4", "test-2")
          )
        )
        assert(actualUpdatedInstance.toSeq == Seq("test-3", "test-4"))

      "not accept 2 existing elements, but update their insertion order" in:
        // Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(Seq("test-2", "test-1"))

        // Verify
        assert(actualEffects.isEmpty)
        assert(actualUpdatedInstance.toSeq == Seq("test-2", "test-1"))

      "accept a new element, but not an existing element, updating the insertion order of the existing element" in:
        // Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(
            Seq(
              "test-1", // test-1 not added, but is no longer first-in
              "test-3"  // test-2 is evicted instead
            )
          )

        // Verify
        assert(actualEffects == Seq(FifoBoundedSet.AddedEvicting("test-3", "test-2")))
        assert(actualUpdatedInstance.toSeq == Seq("test-1", "test-3"))

      "accept only the last two of four elements, evicting all existing elements" in:
        // Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(
            Seq(
              "test-3", // skipped - overwritten
              "test-4", // skipped - overwritten
              "test-5", // test-1 evicted
              "test-6"  // test-2 evicted
            )
          )

        // Verify
        assert(
          actualEffects == Seq(
            FifoBoundedSet.AddedEvicting("test-5", "test-1"),
            FifoBoundedSet.AddedEvicting("test-6", "test-2")
          )
        )
        assert(actualUpdatedInstance.toSeq == Seq("test-5", "test-6"))

      "not accept when the last two of four elements are identical to existing elements" in:
        // Test
        val (actualUpdatedInstance, actualEffects) =
          instance.addAll(
            Seq(
              "test-3", // skipped - overwritten
              "test-4", // skipped - overwritten
              "test-1", // skipped - identical to existing
              "test-2"  // skipped - identical to existing
            )
          )

        // Verify
        assert(actualEffects.isEmpty)
        assert(actualUpdatedInstance.toSeq == Seq("test-1", "test-2"))
