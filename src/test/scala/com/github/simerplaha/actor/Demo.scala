package com.github.simerplaha.actor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Stateless extends App {

  val actor =
    Actor[Int](
      (message, self) =>
        println(message)
    )

  actor ! 1
}

object Stateful extends App {

  case class MyCounter(var counter: Int)

  val actor =
    Actor[Int, MyCounter](MyCounter(0))(
      (message, self) =>
        self.state.counter += 1
    )

  actor ! 1
}

object Batch extends App {

  val actor =
    Actor.timer[Int](delays = 1.second) {
      (message, self) =>
      //do something
    }
}

object Scheduling extends App {
  val actor =
    Actor[Int](
      (message, self) => {
        println(message)
        self.schedule(message = message + 1, delay = 1.second)
      }
    )

  actor ! 1
  Thread.sleep(10.seconds.toMillis)
}

object Test extends App {

  val actor = TestActor[Int]()

  //expect a message after delay in the Actor's mailbox
  actor.expectNoMessage(after = 1.second)
  //fetch the first message in the actor's mailbox
  val got = actor.getMessage()
  //expect a message of some type
  actor.expectMessage[Int]()

}