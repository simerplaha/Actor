package com.github.simerplaha.actor

import java.util.TimerTask
import java.util.concurrent.ConcurrentLinkedQueue

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, TimeoutException}
import scala.util.{Failure, Success, Try}

case class TestActor[T](implicit ec: ExecutionContext) extends Actor[T, Unit]((), (_, _) => None, None) {

  private val queue = new ConcurrentLinkedQueue[T]

  override def schedule(message: T, delay: FiniteDuration): TimerTask =
    Delay.task(delay)(this ! message)

  override def hasMessages: Boolean =
    !queue.isEmpty

  override def messageCount: Int =
    queue.size()

  override def !(message: T): Unit =
    queue add message

  private def sleep(time: FiniteDuration): Unit =
    Thread.sleep(time.toMillis)

  private def eventually[T](timeoutDuration: FiniteDuration,
                            interval: FiniteDuration)(f: => T): T = {
    val deadline = timeoutDuration.fromNow
    var keepTrying: Boolean = true
    var result: Either[Throwable, T] = Left(new TimeoutException("Test timed-out!"))
    while (keepTrying)
      Try(f) match {
        case Failure(exception) =>
          if (deadline.isOverdue()) {
            result = Left(exception)
            keepTrying = false
          } else {
            sleep(interval)
          }
        case Success(value) =>
          result = Right(value)
          keepTrying = false
      }
    result match {
      case Right(success) =>
        success
      case Left(failure) =>
        throw failure
    }
  }

  def getMessage(timeoutDuration: FiniteDuration = 1.second,
                 interval: FiniteDuration = 100.millisecond): T =
    eventually(timeoutDuration, interval)(Option(queue.poll()).get)

  def expectMessage[A <: T](timeoutDuration: FiniteDuration = 1.second,
                            interval: FiniteDuration = 100.millisecond): A =
    eventually(timeoutDuration, interval)(Option(queue.poll()).get.asInstanceOf[A])

  def expectNoMessage(after: FiniteDuration = 100.millisecond): Unit =
    Await.result(
      awaitable =
        Delay.future(after) {
          Option(queue.poll()) match {
            case Some(item) =>
              throw new Exception(s"Has message: ${item.getClass.getSimpleName}")
            case None =>
              ()
          }
        },
      atMost = after.plus(200.millisecond)
    )
}
