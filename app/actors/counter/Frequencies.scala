package actors.counter

/**
 * Immutable data structure that tracks counts of anything.
 *
 * Counts are `Int`s and will never exceed `Int.MaxValue`, and does
 * not overflow.
 *
 * @param countsByItem mapping of item -> count
 * @param itemsByCount mapping of count -> items
 * @tparam T type of item being counted
 */
case class Frequencies[T](
  countsByItem: Map[T, Int] = Map[T, Int](),
  itemsByCount: Map[Int, Seq[T]] = Map[Int, Seq[T]]()
) {
  /**
   * Updates the counts of a single item by incrementing
   * (positive-delta) or decrementing (negative-delta) by the given
   * value.
   *
   * @param item the item whose count is to be updated
   * @param delta the amount to change the count
   * @return updated copy
   */
  def updated(item: T, delta: Int): Frequencies[T] = {
    if (delta == 0) this
    else {
      val oldCount: Int = countsByItem.getOrElse(item, 0)
      val newCountUnbounded: Int = oldCount + delta
      val newCount: Int =
        if (newCountUnbounded >= 0) newCountUnbounded
        else if (delta > 0) Int.MaxValue else 0
      val newCountItems: Seq[T] =
        itemsByCount.getOrElse(newCount, IndexedSeq())

      Frequencies(
        if (newCount <= 0) countsByItem.removed(item)
        else countsByItem.updated(item, newCount),
        itemsByCount.
          updated(
            oldCount,
            itemsByCount.getOrElse(oldCount, IndexedSeq()).diff(Seq(item))
          ).
          updated(
            newCount,
            if (delta > 0) newCountItems.appended(item)
            else newCountItems.prepended(item)
          ).
          filter {
            case (count: Int, items: Seq[T]) =>
              count > 0 && items.nonEmpty
          }
      )
    }
  }
}
