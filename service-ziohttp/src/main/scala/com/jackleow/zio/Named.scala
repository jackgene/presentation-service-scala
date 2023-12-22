package com.jackleow.zio

trait Named[A, N <: Singleton & String]:
  /**
   * Wrapped item.
   */
  def get: A
