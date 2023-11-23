package com.jackleow.presentation.infrastructure

import akka.actor.typed.ActorSystem

trait AkkaModule {
  implicit def system: ActorSystem[Nothing]
}
