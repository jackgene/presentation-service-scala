package com.jackleow.zio

import zio.*

trait Named[A, N <: String]:
  /**
   * Wrapped item.
   */
  def get: A
