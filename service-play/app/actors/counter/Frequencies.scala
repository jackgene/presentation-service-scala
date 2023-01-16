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
class Frequencies[T] private[counter] (
  private[counter] val countsByItem: Map[T, Int],
  val itemsByCount: Map[Int, Seq[T]]
) {
  /**
   * Updated [[Frequencies]] with `item` incremented.
   *
   * @param item  the item whose count is to be updated
   * @return updated copy
   */
  def incremented(item: T): Frequencies[T] = countsByItem.getOrElse(item, 0) match {
    case Int.MaxValue => this

    case oldCount: Int =>
      val newCount: Int = oldCount + 1

      new Frequencies(
        countsByItem.updated(item, newCount),
        {
          val newCountItems: Seq[T] =
            itemsByCount.getOrElse(newCount, IndexedSeq())
          val oldCountItems: Seq[T] =
            itemsByCount.getOrElse(oldCount, IndexedSeq()).diff(Seq(item))

          (
            if (oldCountItems.isEmpty) itemsByCount.removed(oldCount)
            else itemsByCount.updated(oldCount, oldCountItems)
          ).updated(newCount, newCountItems.appended(item))
        }
      )
  }

  /**
   * Updated [[Frequencies]] with `item` decremented.
   *
   * @param item the item whose count is to be updated
   * @return updated copy
   */
  def decremented(item: T): Frequencies[T] = countsByItem.get(item) match {
    case None => this

    case Some(oldCount: Int) =>
      val newCount: Int = oldCount - 1

      new Frequencies(
        if (newCount == 0) countsByItem.removed(item)
        else countsByItem.updated(item, newCount),
        {
          val newCountItems: Seq[T] =
            itemsByCount.getOrElse(newCount, IndexedSeq())
          val oldCountItems: Seq[T] =
            itemsByCount.getOrElse(oldCount, IndexedSeq()).diff(Seq(item))
          val itemsByCountOldCountUpdated =
            if (oldCountItems.isEmpty) itemsByCount.removed(oldCount)
            else itemsByCount.updated(oldCount, oldCountItems)

          if (newCount == 0) itemsByCountOldCountUpdated
          else itemsByCountOldCountUpdated.updated(
            newCount, newCountItems.prepended(item)
          )
        }
      )
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
