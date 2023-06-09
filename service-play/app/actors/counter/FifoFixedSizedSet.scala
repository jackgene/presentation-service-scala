package actors.counter

import play.api.libs.json.*

object FifoFixedSizedSet {
  /**
   * The effect of adding a value to the set:
   */
  sealed trait Effect[A]
  /**
   * Indicates that an element was added to the set without evicting
   * anything from the set.
   *
   * This happens when adding a new element to a set that is not full.
   */
  case class Added[A]() extends Effect[A]
  /**
   * Indicates that an element was added to the set evicting an
   * existing element from the set.
   *
   * This happens when adding a new element to a set that is full.
   */
  case class AddedEvicting[A](value: A) extends Effect[A]
  /**
   * Indicates that an element was not added to the set.
   *
   * Note that while the contents of the set would remaing the same,
   * the insertion order may change.
   *
   * This happens when adding an existing element to a set.
   */
  case class NotAdded[A]() extends Effect[A]

  def apply[A](maxSize: Int): FifoFixedSizedSet[A] =
    new FifoFixedSizedSet[A](maxSize)

  implicit def writes[A](
    implicit elemWrites: Writes[A]
  ): Writes[FifoFixedSizedSet[A]] =
    (set: FifoFixedSizedSet[A]) => Json.arr(set.toSeq.map(elemWrites.writes))
}

/**
 * First-In, First-Out fixed sized set.
 *
 * This is basically an LRU-cache that returns evictions as elements
 * are added.
 *
 * @tparam A the element type
 */
class FifoFixedSizedSet[A] private(
  val maxSize: Int,
  val uniques: Set[A] = Set[A](),
  val insertionOrder: IndexedSeq[A] = Vector[A]()
) {
  import FifoFixedSizedSet.*

  if (maxSize < 1) {
    throw new IllegalArgumentException("maxSize must be positive")
  }

  private def copy(
    uniques: Set[A] = uniques,
    insertionOrder: IndexedSeq[A]
  ): FifoFixedSizedSet[A] = new FifoFixedSizedSet(
    maxSize, uniques, insertionOrder
  )

  /**
   * Adds a single element to this set, returning its effect.
   *
   * See [[FifoFixedSizedSet.Effect]] for details.
   *
   * @param elem element to add
   * @return updated copy of this set, and effects of the addition
   */
  def add(elem: A): (FifoFixedSizedSet[A], Effect[A]) =
    if (uniques.contains(elem))
      if (elem == insertionOrder.last) (this, NotAdded())
      else
        (
          copy(insertionOrder = insertionOrder.filterNot(_ == elem) :+ elem),
          NotAdded()
        )
    else
      if (uniques.size < maxSize)
        (
          copy(
            uniques = uniques + elem,
            insertionOrder = insertionOrder :+ elem
          ),
          Added()
        )
      else
        (
          copy(
            uniques = uniques + elem - insertionOrder.head,
            insertionOrder = insertionOrder.tail :+ elem
          ),
          AddedEvicting(insertionOrder.head)
        )

  /**
   * Adds the elements to this set, returning the effect of each addition.
   *
   * See [[FifoFixedSizedSet.Effect]] for details.
   *
   * @param elems elements to add
   * @return updated copy of this set, and effects of each addition
   */
  def addAll(elems: Seq[A]): (FifoFixedSizedSet[A], Seq[Effect[A]]) =
    elems.foldLeft((this, IndexedSeq[Effect[A]]())) {
      (accum: (FifoFixedSizedSet[A], IndexedSeq[Effect[A]]), elem: A) =>

      val (
        accumSet: FifoFixedSizedSet[A], accumUpdates: IndexedSeq[Effect[A]]
      ) = accum
      val (
        nextAccumSet: FifoFixedSizedSet[A], update: Effect[A]
      ) = accumSet.add(elem)

      (nextAccumSet, accumUpdates :+ update)
    }

  def toSeq: Seq[A] = insertionOrder

  private def canEqual(other: Any): Boolean =
    other.isInstanceOf[FifoFixedSizedSet[?]]

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
