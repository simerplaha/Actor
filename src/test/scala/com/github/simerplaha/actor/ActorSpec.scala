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

import org.scalatest.{Matchers, WordSpec}

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class ActorSpec extends WordSpec with Matchers with TestBase {

  "Actor" should {

    "process messages in order of arrival" in {
      val messageCount = 1000

      case class State(processed: ListBuffer[Int])
      val state = State(ListBuffer.empty)

      val actor =
        Actor[Int, State](state) {
          case (int, self) =>
            self.state.processed += int
        }

      (1 to messageCount) foreach (actor ! _)

      //same thread, messages should arrive in order
      eventual {
        state.processed.size shouldBe messageCount
        state.processed should contain inOrderElementsOf (1 to messageCount)
      }
    }

    "process all messages in any order when submitted concurrently" in {
      val messageCount = 1000

      case class State(processed: ListBuffer[String])
      val state = State(ListBuffer.empty)

      val actor =
        Actor[String, State](state) {
          case (int, self) =>
            self.state.processed += int
        }

      (1 to messageCount) foreach {
        message =>
          Future(actor ! message.toString)
      }
      //concurrent sends, messages should arrive in any order but all messages should get processed
      eventual {
        state.processed.size shouldBe messageCount
        state.processed should contain allElementsOf (1 to messageCount).map(_.toString)
      }
    }

    "continue processing messages if execution of one message fails" in {
      case class State(processed: ListBuffer[Int])
      val state = State(ListBuffer.empty)

      val actor =
        Actor[Int, State](state) {
          case (int, self) =>
            if (int == 2) throw new Exception(s"Oh no! Failed at $int")
            self.state.processed += int
        }

      (1 to 3) foreach (actor ! _)
      //
      eventual {
        state.processed.size shouldBe 2
        //2nd message failed
        state.processed should contain only(1, 3)
      }
    }

    "create a stateless actor" in {
      @volatile var ran = false
      val actor = Actor[Unit] {
        (_, self) =>
          self.state shouldBe()
          ran = true
      }

      actor ! ()
      eventual(ran shouldBe true)
    }
  }

  "Actor.timer" should {

    "process all messages after a fixed interval" in {

      case class State(processed: ListBuffer[Int])
      val state = State(ListBuffer.empty)

      val actor =
        Actor.timer[Int, State](state, 1.second) {
          case (int, self) =>
            self.state.processed += int
            //delay sending message to self so that it does get processed in the same timer
            println(s"Message: $int")
            if (int < 6)
              self.schedule(int + 1, 100.millisecond)
        }

      actor ! 1
      eventual(state.processed.size shouldBe 6)
    }
  }

  "TestActor" should {
    "fetch first message on receive and then expect no message" in {
      val actor = TestActor[Int]()

      Delay.future(100.millisecond)(actor ! 1)
      actor.getMessage() shouldBe 1

      actor.expectNoMessage(1.second)
    }

    "fail is expectNoMessage received a message" in {
      val actor = TestActor[Int]()

      Delay.future(100.millisecond)(actor ! 1)
      actor.getMessage() shouldBe 1
      actor.expectNoMessage(1.second)
    }

    "expect a message of higher kind" in {
      sealed trait Domain
      object Domain {
        sealed trait User extends Domain
        case object User extends User
        case object SomeOtherThing extends Domain
      }

      val actor = TestActor[Domain]()

      Delay.future(100.millisecond)(actor ! Domain.User)
      actor.expectMessage[Domain.User]() shouldBe Domain.User
    }
  }

}
