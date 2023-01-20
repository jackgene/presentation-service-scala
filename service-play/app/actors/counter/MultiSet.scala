package actors.counter

object MultiSet {
  def apply[A](): MultiSet[A] = new MultiSet[A](
    Map[A, Int](), Map[Int, Seq[A]]()
  )
}

/**
 * Immutable data structure that tracks counts of anything.
 *
 * Counts are `Int`s and will never exceed `Int.MaxValue`, and does
 * not overflow.
 *
 * @param countsByElement mapping of element -> count
 * @param elementsByCount mapping of count -> elements
 * @tparam A type of element being counted
 */
class MultiSet[A] private[counter] (
  private[counter] val countsByElement: Map[A, Int],
  val elementsByCount: Map[Int, Seq[A]]
) {
  /**
   * Updated [[MultiSet]] with `elem` incremented.
   *
   * @param elem the element whose count is to be updated
   * @return updated copy
   */
  def incremented(elem: A): MultiSet[A] = countsByElement.getOrElse(elem, 0) match {
    case Int.MaxValue => this

    case oldCount: Int =>
      val newCount: Int = oldCount + 1

      new MultiSet(
        countsByElement.updated(elem, newCount),
        {
          val newCountElems: Seq[A] =
            elementsByCount.getOrElse(newCount, IndexedSeq())
          val oldCountElems: Seq[A] =
            elementsByCount.getOrElse(oldCount, IndexedSeq()).diff(Seq(elem))

          (
            if (oldCountElems.isEmpty) elementsByCount.removed(oldCount)
            else elementsByCount.updated(oldCount, oldCountElems)
          ).updated(newCount, newCountElems.appended(elem))
        }
      )
  }

  /**
   * Updated [[MultiSet]] with `elem` decremented.
   *
   * @param elem the element whose count is to be updated
   * @return updated copy
   */
  def decremented(elem: A): MultiSet[A] = countsByElement.get(elem) match {
    case None => this

    case Some(oldCount: Int) =>
      val newCount: Int = oldCount - 1

      new MultiSet(
        if (newCount == 0) countsByElement.removed(elem)
        else countsByElement.updated(elem, newCount),
        {
          val newCountElems: Seq[A] =
            elementsByCount.getOrElse(newCount, IndexedSeq())
          val oldCountElems: Seq[A] =
            elementsByCount.getOrElse(oldCount, IndexedSeq()).diff(Seq(elem))
          val elemsByCountOldCountUpdated =
            if (oldCountElems.isEmpty) elementsByCount.removed(oldCount)
            else elementsByCount.updated(oldCount, oldCountElems)

          if (newCount == 0) elemsByCountOldCountUpdated
          else elemsByCountOldCountUpdated.updated(
            newCount, newCountElems.prepended(elem)
          )
        }
      )
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[MultiSet[?]]

  override def equals(other: Any): Boolean = other match {
    case that: MultiSet[?] =>
      (that canEqual this) &&
        countsByElement == that.countsByElement &&
        elementsByCount == that.elementsByCount
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(countsByElement, elementsByCount)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

  override def toString = s"Frequencies($elementsByCount)"
}
