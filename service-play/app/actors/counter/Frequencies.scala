package actors.counter

object Frequencies {
  def apply[T](): Frequencies[T] = new Frequencies[T](
    Map[T, Int](), Map[Int, Seq[T]]()
  )
}

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
class Frequencies[T] private (
  private[counter] val countsByItem: Map[T, Int],
  val itemsByCount: Map[Int, Seq[T]]
) {
  def increment(item: T): Frequencies[T] = updated(item, 1)

  def decrement(item: T): Frequencies[T] = updated(item, -1)

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

      new Frequencies(
        if (newCount <= 0) countsByItem.removed(item)
        else countsByItem.updated(item, newCount),
        itemsByCount.
          updated(
            oldCount,
            itemsByCount.getOrElse(oldCount, IndexedSeq()).diff(Seq(item))
          ).
          updated(
            newCount,
            if (delta < 0) newCountItems.prepended(item)
            else
              if (newCount == oldCount) newCountItems
              else newCountItems.appended(item)
          ).
          filter {
            case (count: Int, items: Seq[T]) =>
              count > 0 && items.nonEmpty
          }
      )
    }
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[Frequencies[?]]

  override def equals(other: Any): Boolean = other match {
    case that: Frequencies[?] =>
      (that canEqual this) &&
        countsByItem == that.countsByItem &&
        itemsByCount == that.itemsByCount
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(countsByItem, itemsByCount)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

  override def toString = s"Frequencies($itemsByCount)"
}
