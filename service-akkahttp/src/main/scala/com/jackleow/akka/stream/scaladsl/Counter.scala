package com.jackleow.akka.stream.scaladsl

import akka.stream.BoundedSourceQueue

object Counter:
  def apply(queue: BoundedSourceQueue[Int]): Counter =
    new Counter:
      override def increment(): Unit =
        queue.offer(1)
        ()

      override def decrement(): Unit =
        queue.offer(-1)
        ()

trait Counter:
  def increment(): Unit
  def decrement(): Unit
