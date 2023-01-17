package actors.counter

import org.openjdk.jmh.annotations.*

import java.util.concurrent.TimeUnit

object FifoFixedSizedSetPerf:
  private def naiveAdd[T](
    item: T, itemsReversed: List[T], fixedSize: Int
  ): (List[T], Option[(T, Option[T])]) =
    val preTruncate: List[T] = (item :: itemsReversed).distinct

    (
      preTruncate.take(fixedSize),
      if (itemsReversed.size == preTruncate.size) None
      else Some((item, preTruncate.drop(fixedSize).headOption))
    )

  private def naiveAddAll[T](
    items: List[T], itemsReversed: List[T], fixedSize: Int
  ): (List[T], Set[T], Set[T]) =
    val preTruncate: List[T] = (items ++ itemsReversed).distinct

    (
      preTruncate.take(fixedSize),
      preTruncate.toSet -- itemsReversed.toSet,
      preTruncate.drop(fixedSize).toSet
    )


/**
 * Benchmark comparing implementations using [[FifoFixedSizedSet]] against
 * a baseline of naïve implementations using only Scala collections.
 *
 * Use cases:
 * - add to empty
 * - addAll to empty
 * - add evicting
 * - addAll evicting
 * - add reordering
 * - addAll reordering
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class FifoFixedSizedSetPerf:
  import FifoFixedSizedSetPerf.*

  private val itemsReversed: List[Int] = List(3, 2, 1)
  private val emptyInstance: FifoFixedSizedSet[Int] = FifoFixedSizedSet(3)
  private val fullInstance: FifoFixedSizedSet[Int] =
    emptyInstance.addAll(itemsReversed.reverse)._1

  @Benchmark
  def add_baseline(): Seq[Int] =
    val (newItemsReversed: List[Int], update: Option[(Int,Option[Int])]) =
      naiveAdd(0, List(), 3)

    update match
      case Some(_, None) => newItemsReversed
      case _ => throw new IllegalStateException("addition expected")

  @Benchmark
  def add_implementation(): Seq[Int] =
    val (instance: FifoFixedSizedSet[Int], update: FifoFixedSizedSet.Effect[Int]) =
      emptyInstance.add(0)

    update match
      case FifoFixedSizedSet.Added() => instance.insertionOrder
      case _ => throw new IllegalStateException("addition expected")

  @Benchmark
  def addAll_baseline(): Seq[Int] =
    val (newItemsReversed: List[Int], additions: Set[Int], removals: Set[Int]) =
      naiveAddAll(List(0, 1), List(), 3)

    if (additions.nonEmpty && removals.isEmpty) newItemsReversed
    else throw new IllegalStateException("additions expected")

  @Benchmark
  def addAll_implementation(): Seq[Int] =
    val (instance: FifoFixedSizedSet[Int], _: Seq[FifoFixedSizedSet.Effect[Int]]) =
      emptyInstance.addAll(Seq(0, 1))

    // TODO
    instance.insertionOrder

  @Benchmark
  def addEvicting_baseline(): Seq[Int] =
    val (newItemsReversed: List[Int], update: Option[(Int,Option[Int])]) =
      naiveAdd(4, itemsReversed, 3)

    update match
      case Some(_, Some(_)) => newItemsReversed
      case _ => throw new IllegalStateException("eviction expected")

  @Benchmark
  def addEvicting_implementation(): Seq[Int] =
    val (instance: FifoFixedSizedSet[Int], update: FifoFixedSizedSet.Effect[Int]) =
      fullInstance.add(4)

    update match
      case FifoFixedSizedSet.AddedEvicting(_: Int) => instance.insertionOrder
      case _ => throw new IllegalStateException("eviction expected")

  @Benchmark
  def addAllEvicting_baseline(): Seq[Int] =
    val (newItemsReversed: List[Int], additions: Set[Int], removals: Set[Int]) =
      naiveAddAll(List(4, 5), itemsReversed, 3)

    if (additions.size == 2 && removals.size == 2) newItemsReversed
    else throw new IllegalStateException("evictions expected")

  @Benchmark
  def addAllEvicting_implementation(): Seq[Int] =
    val (instance: FifoFixedSizedSet[Int], _: Seq[FifoFixedSizedSet.Effect[Int]]) =
      fullInstance.addAll(Seq(4, 5))

    // TODO
    instance.insertionOrder

  @Benchmark
  def addReordering_baseline(): Seq[Int] =
    val (newItemsReversed: List[Int], update: Option[(Int,Option[Int])]) =
      naiveAdd(1, itemsReversed, 3)

    update match
      case None => newItemsReversed
      case _ => throw new IllegalStateException("no-op expected")

  @Benchmark
  def addReordering_implementation(): Seq[Int] =
    val (instance: FifoFixedSizedSet[Int], update: FifoFixedSizedSet.Effect[Int]) =
      fullInstance.add(1)

    update match
      case FifoFixedSizedSet.NotAdded() => instance.insertionOrder
      case _ => throw new IllegalStateException("no-op expected")

  @Benchmark
  def addAllReordering_baseline(): Seq[Int] =
    val (newItemsReversed: List[Int], additions: Set[Int], removals: Set[Int]) =
      naiveAddAll(List(1, 2), itemsReversed, 3)

    if (additions.isEmpty && removals.isEmpty) newItemsReversed
    else throw new IllegalStateException("no-op expected")

  @Benchmark
  def addAllReordering_implementation(): Seq[Int] =
    val (instance: FifoFixedSizedSet[Int], _: Seq[FifoFixedSizedSet.Effect[Int]]) =
      fullInstance.addAll(Seq(1, 2))

    // TODO
    instance.insertionOrder
