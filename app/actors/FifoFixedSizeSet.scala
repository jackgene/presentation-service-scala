package actors

import play.api.libs.json._

object FifoFixedSizeSet {
  def sized[T](size: Int): FifoFixedSizeSet[T] = new FifoFixedSizeSet[T](size)

  implicit def writes[T](implicit itemWrites: Writes[T]): Writes[FifoFixedSizeSet[T]] =
    (set: FifoFixedSizeSet[T]) => Json.arr(set.toSeq.map(itemWrites.writes))
}

class FifoFixedSizeSet[T] private (
  size: Int,
  uniques: Set[T] = Set[T](),
  insertionOrder: IndexedSeq[T] = Vector[T]()
) {
  private def copy(
      uniques: Set[T] = uniques, insertionOrder: IndexedSeq[T]):
      FifoFixedSizeSet[T] =
    new FifoFixedSizeSet(size, uniques, insertionOrder)

  def add(item: T): (FifoFixedSizeSet[T], Option[Option[T]]) =
    if (uniques.contains(item))
      if (item == insertionOrder.last) (this, None)
      else
        (
          copy(insertionOrder = insertionOrder.filterNot(_ == item) :+ item),
          None
        )
    else
      if (uniques.size < size)
        (
          copy(
            uniques = uniques + item,
            insertionOrder = insertionOrder :+ item
          ),
          Some(None)
        )
      else
        (
          copy(
            uniques = uniques + item - insertionOrder.head,
            insertionOrder = insertionOrder.tail :+ item
          ),
          Some(insertionOrder.headOption)
        )

  def addAll(items: Seq[T]): (FifoFixedSizeSet[T], Seq[Option[Option[T]]]) =
    items.foldLeft((this, IndexedSeq[Option[Option[T]]]())) {
      (
        accum: (FifoFixedSizeSet[T], IndexedSeq[Option[Option[T]]]), item: T
      ) =>

      val (accumSet: FifoFixedSizeSet[T], accumUpdates: IndexedSeq[Option[Option[T]]]) = accum
      val (nextAccumSet: FifoFixedSizeSet[T], update: Option[Option[T]]) = accumSet.add(item)

      (nextAccumSet, accumUpdates :+ update)
    }

  def toSeq: Seq[T] = insertionOrder

  override def toString: String =
    insertionOrder.mkString("FifoQueueSet(", ", ", ")")
}
