package com.github.simerplaha.actor

object Result {
  sealed trait Sent
  case object Sent extends Sent
  sealed trait TerminatedActor
  case object TerminatedActor extends TerminatedActor
}
