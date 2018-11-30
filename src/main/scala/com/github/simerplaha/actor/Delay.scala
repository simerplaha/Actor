/*
 * Copyright (c) 2018 @simerplaha.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.simerplaha.actor

import java.util.{Timer, TimerTask}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}

object Delay {

  private val timer = new Timer(true)

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
