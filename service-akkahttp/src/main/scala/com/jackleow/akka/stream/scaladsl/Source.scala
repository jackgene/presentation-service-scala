package com.jackleow.akka.stream.scaladsl

import akka.stream.scaladsl.Source

extension (source: Source.type)
  def counts: Source[Int, Counter] =
    Source
      .queue[Int](1)
      .scan(0): 
        _ + _
      .mapMaterializedValue:
        Counter(_)

extension[Out, Mat] (source: Source[Out, Mat])
  def onStart(callback: => Unit): Source[Out, Mat] =
    source
      .prepend(
        Source
          .unfold(()):
            _ =>
              callback
              None
      )
    
