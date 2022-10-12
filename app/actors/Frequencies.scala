package actors

case class Frequencies(
  countsByItem: Map[String,Int] = Map(),
  itemsByCount: Map[Int,Seq[String]] = Map()
) {
  def updated(item: String, delta: Int): Frequencies = {
    if (delta == 0) this
    else {
      val oldCount: Int = countsByItem.getOrElse(item, 0)
      val newCount: Int = oldCount + delta
      val newCountItems: Seq[String] =
        itemsByCount.getOrElse(newCount, IndexedSeq())

      Frequencies(
        countsByItem.updated(item, newCount),
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
            case (count: Int, items: Seq[String]) =>
              count > 0 && items.nonEmpty
          }
      )
    }
  }
}
