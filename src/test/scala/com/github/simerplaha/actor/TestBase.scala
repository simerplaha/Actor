package com.github.simerplaha.actor

import org.scalatest.concurrent.Eventually

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

trait TestBase extends Eventually {

  def sleep(time: FiniteDuration): Unit =
    Thread.sleep(time.toMillis)

  def eventual[A](code: => A): Unit =
    eventual()(code)

  def eventual[A](after: FiniteDuration = 1.second)(code: => A): Unit =
    eventually(timeout(after))(code)

}
