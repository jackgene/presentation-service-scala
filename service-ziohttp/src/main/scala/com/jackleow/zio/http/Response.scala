package com.jackleow.zio.http

import zio.http.{Response, Status}

extension (response: Response.type)
  def noContent: Response = Response.status(Status.NoContent)
