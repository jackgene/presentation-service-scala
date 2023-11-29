package com.jackleow.presentation.route

import akka.http.scaladsl.server.Route

import java.io.File

/**
 * Defines route dependencies.
 */
trait RouteModule:
  def routes(htmlFile: File): Route
