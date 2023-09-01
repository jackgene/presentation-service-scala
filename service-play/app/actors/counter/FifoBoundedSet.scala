package actors.counter

import play.api.libs.json.*

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
  case class AddedEvicting[A](added: A, evicted: A) extends Effect[A]

  def apply[A](maxSize: Int): FifoBoundedSet[A] =
    new FifoBoundedSet[A](maxSize)

  implicit def writes[A](
    implicit elemWrites: Writes[A]
  ): Writes[FifoBoundedSet[A]] =
    (set: FifoBoundedSet[A]) => Json.arr(set.toSeq.map(elemWrites.writes))
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
  uniques: Set[A] = Set[A]()
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
    foldLeft((this, List[Effect[A]]())) {
      (accum: (FifoBoundedSet[A], List[Effect[A]]), elem: A) =>

      val (
        accumSet: FifoBoundedSet[A], accumUpdates: List[Effect[A]]
      ) = accum
      val (
        nextAccumSet: FifoBoundedSet[A], updateOpt: Option[Effect[A]]
      ) = accumSet.add(elem)

      (
        nextAccumSet,
        updateOpt match {
          case Some(update) => update :: accumUpdates
          case None => accumUpdates

        }
      )
    }.
    pipe {
      case (set: FifoBoundedSet[A], effects: Seq[Effect[A]]) =>
        (
          set,
          effects.take(maxSize).reverse.map {
            case AddedEvicting(added, evicted) if !uniques.contains(evicted) =>
              // Evicted value was part of elems, and effectively never added, and hence not evicted
              Added(added)
            case other => other
          }
        )
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
