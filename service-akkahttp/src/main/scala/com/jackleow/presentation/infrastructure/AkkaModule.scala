package com.jackleow.presentation.infrastructure

import akka.actor.typed.ActorSystem

trait AkkaModule:
  given system: ActorSystem[Nothing]
