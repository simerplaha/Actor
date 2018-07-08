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

import java.util.TimerTask
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait ActorRef[-T] {
  /**
    * Submits message to Actor's queue and starts message execution if not already running.
    */
  def !(message: T): Unit

  def schedule(message: T, delay: FiniteDuration): TimerTask
}

object Actor {

  /**
    * Basic stateless Actor that processes all incoming messages sequentially.
    *
    * On each message send (!) the Actor is woken up if it's not already running.
    */
  def apply[T](execution: (T, Actor[T, Unit]) => Unit)(implicit ec: ExecutionContext): ActorRef[T] =
    apply[T, Unit]()(execution)

  /**
    * Basic stateful Actor that processes all incoming messages sequentially.
    *
    * On each message send (!) the Actor is woken up if it's not already running.
    */
  def apply[T, S](state: S)(execution: (T, Actor[T, S]) => Unit)(implicit ec: ExecutionContext): ActorRef[T] =
    new Actor[T, S](
      state = state,
      execution =
        (message, actor) => {
          execution(message, actor)
          None
        },
      delay = None
    )

  /**
    * Stateless [[timer]] actor
    */
  def timer[T](delays: FiniteDuration)(execution: (T, Actor[T, Unit]) => Unit)(implicit ec: ExecutionContext): ActorRef[T] =
    timer((), delays)(execution)

  /**
    * Processes messages at regular intervals.
    *
    * If there are no messages in the queue the Actor
    * is stopped and restarted only when a new message is added the queue.
    */
  def timer[T, S](state: S,
                  fixedDelay: FiniteDuration)(execution: (T, Actor[T, S]) => Unit)(implicit ec: ExecutionContext): ActorRef[T] =
    new Actor[T, S](
      state = state,
      execution =
        (message, actor) => {
          execution(message, actor)
          Some(fixedDelay)
        },
      delay = None
    )
}

class Actor[T, +S](val state: S,
                   execution: (T, Actor[T, S]) => Unit,
                   private val delay: Option[FiniteDuration])(implicit ec: ExecutionContext) extends ActorRef[T] { self =>

  private val busy = new AtomicBoolean(false)
  private val queue = new ConcurrentLinkedQueue[T]

  val maxMessagesToProcessAtOnce = 1000000
  //if initial detail is defined, trigger processMessages() to start the timer loop.
  if (delay.isDefined) processMessages()

  override def !(message: T): Unit = {
    queue offer message
    processMessages()
  }

  def clearMessages(): Unit =
    queue.clear()

  def hasMessages(): Boolean =
    queue.isEmpty

  def messageCount(): Int =
    queue.size()

  override def schedule(message: T, delay: FiniteDuration): TimerTask =
    Delay.task(delay)(this ! message)

  private def processMessages(): Unit =
    if (!queue.isEmpty && busy.compareAndSet(false, true))
      delay map {
        interval =>
          if (interval.fromNow.isOverdue())
            Future(receive(maxMessagesToProcessAtOnce))
          else
            Delay.future(interval max Duration.Zero)(receive(maxMessagesToProcessAtOnce))
      } getOrElse {
        Future(receive(maxMessagesToProcessAtOnce))
      }

  private def receive(max: Int): Unit = {
    var processed = 0
    try {
      while (processed < max) {
        val message = queue.poll
        if (message != null) {
          Try(execution(message, self))
          processed += 1
        } else {
          processed = max
        }
      }
    } finally {
      busy.set(false)
      processMessages()
    }
  }
}