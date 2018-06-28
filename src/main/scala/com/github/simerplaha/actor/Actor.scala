/*
 * Copyright (C) 2018 Simer Plaha (@simerplaha)
 *
 * This file is a part of SwayDB.
 *
 * SwayDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SwayDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with SwayDB. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.simerplaha.actor

import java.util.TimerTask
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

sealed trait ActorRef[-T] {
  /**
    * Submits message to Actor's queue and starts message execution if not already running.
    */
  def !(message: T): Unit

  def clearMessages(): Unit

  def hasMessages(): Boolean

  def messageCount(): Int

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

  override def clearMessages(): Unit =
    queue.clear()

  override def hasMessages(): Boolean =
    queue.isEmpty

  override def messageCount(): Int =
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
            Delay.future(interval max 500.milliseconds)(receive(maxMessagesToProcessAtOnce))
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