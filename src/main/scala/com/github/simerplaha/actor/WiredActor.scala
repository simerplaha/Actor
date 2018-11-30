package com.github.simerplaha.actor

import java.util.TimerTask
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

class WiredActor[+T](impl: T, delays: Option[FiniteDuration])(implicit ec: ExecutionContext) {

  private val actor =
    delays map {
      delays =>
        Actor.timer[() => Unit](delays)((function, _) => function())
    } getOrElse {
      Actor[() => Unit]((function, _) => function())
    }

  def call[R](function: T => R): Future[R] = {
    val promise = Promise[R]()
    actor ! (() => promise.tryComplete(Try(function(impl))))
    promise.future
  }

  def callFlatMap[R](function: T => Future[R]): Future[R] = {
    val promise = Promise[R]()
    actor ! (() => promise.completeWith(function(impl)))
    promise.future
  }

  def call[R](function: (T, WiredActor[T]) => R): Future[R] = {
    val promise = Promise[R]()
    actor ! (() => promise.tryComplete(Try(function(impl, this))))
    promise.future
  }

  def callFlatMap[R](function: (T, WiredActor[T]) => Future[R]): Future[R] = {
    val promise = Promise[R]()
    actor ! (() => promise.completeWith(function(impl, this)))
    promise.future
  }

  def send[R](function: T => R): Unit =
    actor ! (() => function(impl))

  def send[R](function: (T, WiredActor[T]) => R): Unit =
    actor ! (() => function(impl, this))

  def scheduleCall[R](delay: FiniteDuration)(function: T => R): (Future[R], TimerTask) = {
    val promise = Promise[R]()
    val timerTask = actor.schedule(() => promise.tryComplete(Try(function(impl))), delay)
    (promise.future, timerTask)
  }

  def scheduleCallSelf[R](delay: FiniteDuration)(function: (T, WiredActor[T]) => R): (Future[R], TimerTask) = {
    val promise = Promise[R]()
    val timerTask = actor.schedule(() => promise.tryComplete(Try(function(impl, this))), delay)
    (promise.future, timerTask)
  }

  def scheduleFlatMap[R](delay: FiniteDuration)(function: T => Future[R]): (Future[R], TimerTask) = {
    val promise = Promise[R]()
    val timerTask = actor.schedule(() => promise.completeWith(function(impl)), delay)
    (promise.future, timerTask)
  }

  def scheduleFlatMapSelf[R](delay: FiniteDuration)(function: (T, WiredActor[T]) => Future[R]): (Future[R], TimerTask) = {
    val promise = Promise[R]()
    val timerTask = actor.schedule(() => promise.completeWith(function(impl, this)), delay)
    (promise.future, timerTask)
  }

  def scheduleSend[R](delay: FiniteDuration)(function: T => R): TimerTask =
    actor.schedule(() => function(impl), delay)

  def scheduleSend[R](delay: FiniteDuration)(function: (T, WiredActor[T]) => R): TimerTask =
    actor.schedule(() => function(impl, this), delay)
}