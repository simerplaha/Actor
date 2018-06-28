package com.github.simerplaha.actor

import java.util.{Timer, TimerTask}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}

object Delay {

  val timer = new Timer(true)

  private def runWithDelay[T](delayFor: FiniteDuration)(block: => Future[T])(implicit ctx: ExecutionContext): Future[T] = {
    val promise = Promise[T]()
    val task =
      new TimerTask {
        def run() {
          ctx.execute(
            new Runnable {
              override def run(): Unit =
                promise.completeWith(block)
            }
          )
        }
      }
    timer.schedule(task, delayFor.toMillis)
    promise.future
  }

  private def createTask(delayFor: FiniteDuration)(block: => Unit): TimerTask = {
    val task =
      new TimerTask {
        def run() =
          block
      }
    timer.schedule(task, delayFor.toMillis)
    task
  }

  def apply[T](delayFor: FiniteDuration)(block: => Future[T])(implicit ctx: ExecutionContext): Future[T] =
    runWithDelay(delayFor)(block)

  def future[T](delayFor: FiniteDuration)(block: => T)(implicit ctx: ExecutionContext): Future[T] =
    runWithDelay(delayFor)(Future(block))

  def task(delayFor: FiniteDuration)(block: => Unit): TimerTask =
    createTask(delayFor)(block)
}
