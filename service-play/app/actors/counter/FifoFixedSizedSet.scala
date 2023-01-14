package actors.counter

import play.api.libs.json.*

object FifoFixedSizedSet {
  /**
   * The effect of adding a value to the set:
   */
  sealed trait Effect[T]
  /**
   * Indicates that an item was added to the set without evicting
   * anything from the set.
   *
   * This happens when adding a new item to a set that is not full.
   */
  case class Added[T]() extends Effect[T]
  /**
   * Indicates that an item was added to the set evicting an existing
   * item from the set.
   *
   * This happens when adding a new item to a set that is full.
   */
  case class AddedEvicting[T](value: T) extends Effect[T]
  /**
   * Indicates that an item was not added to the set.
   *
   * Note that while the contents of the set would remaing the same,
   * the insertion order may change.
   *
   * This happens when adding an existing item to a set.
   */
  case class NotAdded[T]() extends Effect[T]

  def apply[T](maxSize: Int): FifoFixedSizedSet[T] = new FifoFixedSizedSet[T](maxSize)

  implicit def writes[T](implicit itemWrites: Writes[T]): Writes[FifoFixedSizedSet[T]] =
    (set: FifoFixedSizedSet[T]) => Json.arr(set.toSeq.map(itemWrites.writes))
}

/**
 * First-In, First-Out fixed sized set.
 *
 * This is basically an LRU-cache that returns evictions as items are
 * added.
 *
 * @tparam T the element type
 */
class FifoFixedSizedSet[T] private(
  val maxSize: Int,
  val uniques: Set[T] = Set[T](),
  val insertionOrder: IndexedSeq[T] = Vector[T]()
) {
  import FifoFixedSizedSet.*

  if (maxSize < 1) {
    throw new IllegalArgumentException("maxSize must be positive")
  }

  private def copy(
      uniques: Set[T] = uniques, insertionOrder: IndexedSeq[T]):
      FifoFixedSizedSet[T] =
    new FifoFixedSizedSet(maxSize, uniques, insertionOrder)

  /**
   * Adds a single item to this set, returning its effect.
   *
   * See [[FifoFixedSizedSet.Effect]] for details.
   *
   * @param item item to add
   * @return updated copy of this set, and effects of the addition
   */
  def add(item: T): (FifoFixedSizedSet[T], Effect[T]) =
    if (uniques.contains(item))
      if (item == insertionOrder.last) (this, NotAdded())
      else
        (
          copy(insertionOrder = insertionOrder.filterNot(_ == item) :+ item),
          NotAdded()
        )
    else
      if (uniques.size < maxSize)
        (
          copy(
            uniques = uniques + item,
            insertionOrder = insertionOrder :+ item
          ),
          Added()
        )
      else
        (
          copy(
            uniques = uniques + item - insertionOrder.head,
            insertionOrder = insertionOrder.tail :+ item
          ),
          AddedEvicting(insertionOrder.head)
        )

  /**
   * Adds the items to this set, returning the effect of each addition.
   *
   * See [[FifoFixedSizedSet.Effect]] for details.
   *
   * @param items items to add
   * @return updated copy of this set, and effects of each addition
   */
  def addAll(items: Seq[T]): (FifoFixedSizedSet[T], Seq[Effect[T]]) =
    items.foldLeft((this, IndexedSeq[Effect[T]]())) {
      (accum: (FifoFixedSizedSet[T], IndexedSeq[Effect[T]]), item: T) =>

      val (accumSet: FifoFixedSizedSet[T], accumUpdates: IndexedSeq[Effect[T]]) = accum
      val (nextAccumSet: FifoFixedSizedSet[T], update: Effect[T]) = accumSet.add(item)

      (nextAccumSet, accumUpdates :+ update)
    }

  def toSeq: Seq[T] = insertionOrder

  private def canEqual(other: Any): Boolean = other.isInstanceOf[FifoFixedSizedSet[?]]

  override def equals(other: Any): Boolean = other match {
    case that: FifoFixedSizedSet[?] =>
      (that canEqual this) &&
        maxSize == that.maxSize &&
        uniques == that.uniques &&
        insertionOrder == that.insertionOrder
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq[Any](maxSize, uniques, insertionOrder)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

  override def toString: String =
    insertionOrder.mkString("FifoFixedSizedSet(", ", ", ")")
}
