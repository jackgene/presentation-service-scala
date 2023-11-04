package com.jackleow.presentation.collection

import scala.util.chaining.scalaUtilChainingOps

object FifoBoundedSet {
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
  case class Added[A](added: A) extends Effect[A]
  /**
   * Indicates that an element was added to the set evicting an
   * existing element from the set.
   *
   * This happens when adding a new element to a set that is full.
   */
  case class AddedEvicting[A](added: A, evicting: A) extends Effect[A]

  def apply[A](maxSize: Int): FifoBoundedSet[A] =
    new FifoBoundedSet[A](maxSize)
}

/**
 * First-In, First-Out bounded set.
 *
 * This is basically an LRU-cache that returns evictions as elements
 * are added.
 *
 * @tparam A the element type
 */
class FifoBoundedSet[A] private(
  val maxSize: Int,
  val insertionOrder: IndexedSeq[A] = Vector[A](),
  private val uniques: Set[A] = Set[A]()
) {
  import FifoBoundedSet.*

  require(maxSize >= 1, "maxSize must be positive")

  private def copy(
    uniques: Set[A] = uniques,
    insertionOrder: IndexedSeq[A]
  ): FifoBoundedSet[A] = new FifoBoundedSet(
    maxSize, insertionOrder, uniques
  )

  /**
   * Adds a single element to this set, returning its effect.
   *
   * See [[FifoBoundedSet.Effect]] for details.
   *
   * Note that while not returning an effect means the contents of the
   * set remains the same, the insertion order may have changed.
   *
   * @param elem element to add
   * @return updated copy of this set, and effect of the addition
   */
  def add(elem: A): (FifoBoundedSet[A], Option[Effect[A]]) =
    if (uniques.contains(elem))
      if (elem == insertionOrder.last) (this, None)
      else
        (
          copy(insertionOrder = insertionOrder.filterNot(_ == elem) :+ elem),
          None
        )
    else
      if (uniques.size < maxSize)
        (
          copy(
            uniques = uniques + elem,
            insertionOrder = insertionOrder :+ elem
          ),
          Some(Added(elem))
        )
      else
        (
          copy(
            uniques = uniques + elem - insertionOrder.head,
            insertionOrder = insertionOrder.tail :+ elem
          ),
          Some(AddedEvicting(elem, insertionOrder.head))
        )

  /**
   * Adds the elements to this set, returning the effect of each addition.
   *
   * See [[FifoBoundedSet.Effect]] for details.
   *
   * @param elems elements to add
   * @return updated copy of this set, and effects of each addition
   */
  def addAll(elems: Seq[A]): (FifoBoundedSet[A], Seq[Effect[A]]) = elems.
    foldLeft((this, List[A]())) {
      (accum: (FifoBoundedSet[A], List[A]), elem: A) =>

      val (
        accumSet: FifoBoundedSet[A], accumAdds: List[A]
      ) = accum
      val (
        nextAccumSet: FifoBoundedSet[A], updateOpt: Option[Effect[A]]
      ) = accumSet.add(elem)

      (
        nextAccumSet,
        updateOpt match {
          case Some(Added(elem)) => elem :: accumAdds
          case Some(AddedEvicting(elem, _)) => elem :: accumAdds
          case None => accumAdds
        }
      )
    }.
    pipe { case (updated: FifoBoundedSet[A], additions: Seq[A]) =>
      val effectiveEvictions: Seq[A] = {
        val evictionSet: Set[A] = this.uniques -- updated.uniques
        this.insertionOrder.filter(evictionSet.contains)
      }
      val effectiveAdditions: Seq[A] = {
        val additionSet: Set[A] = updated.uniques -- this.uniques
        additions.filter(additionSet.contains).distinct.reverse
      }
      val nonEvictAdds: Int = effectiveAdditions.size - effectiveEvictions.size
      val effectiveAddeds: Seq[Added[A]] =
        effectiveAdditions.take(nonEvictAdds).map { Added(_) }
      val effectiveAddedEvictings: Seq[AddedEvicting[A]] =
        effectiveAdditions.drop(nonEvictAdds).zip(effectiveEvictions).map {
          case (added, removed) => AddedEvicting(added, removed)
        }

      (updated, effectiveAddeds ++ effectiveAddedEvictings)
    }

  val toSeq: Seq[A] = insertionOrder

  private def canEqual(other: Any): Boolean =
    other.isInstanceOf[FifoBoundedSet[?]]

  override def equals(other: Any): Boolean = other match {
    case that: FifoBoundedSet[?] =>
      (that canEqual this) &&
        maxSize == that.maxSize &&
        insertionOrder == that.insertionOrder
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq[Any](maxSize, insertionOrder)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

  override def toString: String =
    insertionOrder.mkString("FifoBoundedSet(", ", ", ")")
}
