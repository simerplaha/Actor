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
import scala.concurrent.{Await, Future}
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
  actor ! 1
  val got = actor.getMessage()
  //expect a message of some type
  actor ! 1
  actor.expectMessage[Int]()

}

object Ask extends App {
  case class CreateUser(name: String)(val replyTo: ActorRef[Boolean])

  val actor = Actor[CreateUser] {
    (message: CreateUser, _) =>
      message.replyTo ! true
  }

  val response: Future[Boolean] = (actor ? CreateUser("Tony Stark")).right.get

  Await.result(response, 1.second)
}

object WiredDemo extends App {
  //suppose this is your implementation
  object MyImpl {
    //pure function
    def hello(name: String): String =
      s"Hello $name"

    def helloFuture(name: String): Future[String] =
      Future(s"Hello $name") //some delay operation
  }
  //create a wired Actor from your implementation
  val actor = Actor.wire(MyImpl)

  //call functions on the Actor.
  val response: Future[String] = actor.call(_.hello("World"))
  response.foreach(println)

  //call functions on the Actor.
  val responseFlatMap = actor.callFlatMap(_.helloFuture("World from Future"))
  responseFlatMap.foreach(println)

  //send is fire and forget. Returns type Unit
  val responseUnit: Unit = actor.send(impl => println(impl.hello("World again!")))

  //schedule a function call on the actor. Returns Future response and TimerTask to cancel.
  val scheduleResponse: (Future[String], TimerTask) = actor.scheduleCall(delay = 1.second)(_.hello("World!!"))
  scheduleResponse._1.foreach(println)

  //Give enough time for this test to run
  Thread.sleep(2000)
}