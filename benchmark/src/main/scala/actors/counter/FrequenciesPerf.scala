package actors.counter

import org.openjdk.jmh.annotations.*

import java.util.concurrent.TimeUnit

/**
 * Benchmark comparing implementations using [[Frequencies]] against
 * a baseline of naïve implementations using only Scala collections.
 *
 * Naïve implementation does not correctly maintain item order.
 * 
 * Use cases:
 * - New Vote - Creating a vote entry for a new voter
 * - Vote Change - Updating a vote for an existing voter
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class FrequenciesPerf:
  private val existingVotes: Map[String, String] = Map(
    "Alice" -> "Java", "Bob" -> "Scala"
  )
  private val existingFrequencies: Frequencies[String] = Frequencies().
    incremented("Java").
    incremented("Scala")

  @Benchmark
  def newVote_baseline(): Map[Int, Iterable[String]] =
    val updatedVotes: Map[String, String] =
      existingVotes.updated("Charlie", "Scala")
    updatedVotes.
      // Counts by item
      groupMap(_._2)(_._1).map { case (key, value) => key -> value.size }.
      // Items by count - note no prepend/append rule considered
      groupMap(_._2)(_._1)

  @Benchmark
  def newVote_implementation(): Map[Int, Iterable[String]] =
    val updatedVotes: Map[String, String] =
      existingVotes.updated("Charlie", "Scala")
    val removed: Option[String] = existingVotes.get("Charlie")
    val updatedFrequencies: Frequencies[String] = removed match
      case Some(_: String) => throw new IllegalStateException("removal not expected")
      case None => existingFrequencies.incremented(updatedVotes("Charlie"))

    updatedFrequencies.itemsByCount

  @Benchmark
  def voteChange_baseline(): Map[Int, Iterable[String]] =
    val updatedVotes: Map[String, String] =
      existingVotes.updated("Alice", "Elm")
    updatedVotes.
      // Counts by item
      groupMap(_._2)(_._1).map { case (key, value) => key -> value.size }.
      // Items by count - note no prepend/append rule considered
      groupMap(_._2)(_._1)

  @Benchmark
  def voteChange_implementation(): Map[Int, Iterable[String]] =
    val updatedVotes: Map[String, String] =
      existingVotes.updated("Alice", "Elm")
    val removed: Option[String] = existingVotes.get("Alice")
    val updatedFrequencies: Frequencies[String] = removed match
      case Some(item: String) => existingFrequencies.
        incremented(updatedVotes("Alice")).
        decremented(item)
      case None => throw new IllegalStateException("removal expected")

    updatedFrequencies.itemsByCount
