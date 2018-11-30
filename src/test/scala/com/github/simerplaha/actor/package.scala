package com.github.simerplaha

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

package object actor {

  implicit class Awaiter[F](f: Future[F]) {
    def await(timeout: FiniteDuration = 1.second): F =
      Await.result(f, timeout)
  }

}
