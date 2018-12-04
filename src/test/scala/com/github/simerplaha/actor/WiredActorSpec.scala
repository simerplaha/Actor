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

class WiredActorSpec extends WordSpec with Matchers with TestBase {

  "Actor" should {

    "process messages in order of arrival" in {
      class MyImpl(message: ListBuffer[Int]) {
        def message(int: Int): Unit =
          message += int

        def get(): Iterable[Int] = message
      }

      val actor = Actor.wire(new MyImpl(ListBuffer.empty))

      actor.ask {
        impl =>
          (1 to 100) foreach {
            i =>
              impl.message(i)
          }
      }.await(2.seconds)

      actor.ask(_.get()).await().toList should contain theSameElementsInOrderAs (1 to 100)
    }

    "ask" in {
      object MyImpl {
        def hello(name: String, replyTo: WiredActor[MyImpl.type]): String =
          s"Hello $name"
      }

      Actor.wire(MyImpl)
        .ask {
          (impl, self) =>
            impl.hello("John", self)
        }
        .await() shouldBe "Hello John"
    }

    "askFlatMap" in {
      object MyImpl {
        def hello(name: String, replyTo: WiredActor[MyImpl.type]): Future[String] =
          Future(s"Hello $name")
      }

      Actor.wire(MyImpl)
        .askFlatMap {
          (impl, self) =>
            impl.hello("John", self)
        }
        .await() shouldBe "Hello John"
    }

    "send" in {
      class MyImpl(var name: String) {
        def hello(name: String): Future[String] =
          Future {
            this.name = name
            s"Hello $name"
          }

        def getName(): Future[String] =
          Future(name)
      }

      val actor = Actor.wire(new MyImpl(""))

      actor.send(_.hello("John"))

      eventually {
        actor
          .askFlatMap(_.getName())
          .await() shouldBe "John"
      }
    }

    "scheduleAsk" in {
      class MyImpl(var invoked: Boolean = false) {
        def invoke(): Unit =
          invoked = true

        def getInvoked(): Boolean =
          invoked
      }

      val actor = Actor.wire(new MyImpl(invoked = false))

      actor.scheduleAsk(2.second)(_.invoke())

      def assert(expected: Boolean) =
        actor
          .ask(_.getInvoked())
          .await() shouldBe expected

      assert(expected = false)
      sleep(1.second)
      assert(expected = false)

      sleep(1.second)
      eventually {
        actor
          .ask(_.getInvoked())
          .await() shouldBe true
      }
    }

    "scheduleAskFlatMap" in {
      class MyImpl(var invoked: Boolean = false) {
        def invoke(): Future[Boolean] =
          Future {
            invoked = true
            invoked
          }

        def getInvoked(): Boolean =
          invoked
      }

      val actor = Actor.wire(new MyImpl(invoked = false))

      val result = actor.scheduleAskFlatMap(2.second)(_.invoke())

      def assert(expected: Boolean) =
        actor
          .ask(_.getInvoked())
          .await() shouldBe expected

      assert(expected = false)
      sleep(1.second)
      assert(expected = false)

      sleep(1.second)
      eventually {
        actor
          .ask(_.getInvoked())
          .await() shouldBe true
      }
      result._1.await() shouldBe true
    }

    "scheduleAskWithSelf" in {
      class MyImpl(var invoked: Boolean = false) {
        def invoke(replyTo: WiredActor[MyImpl]): Future[Boolean] =
          replyTo
            .ask(_.setInvoked())
            .map {
              _ =>
                invoked
            }

        private def setInvoked() =
          invoked = true

        def getInvoked() =
          invoked
      }

      val actor = Actor.wire(new MyImpl())

      val result = actor.scheduleAskWithSelfFlatMap(2.second) {
        (impl, self) =>
          impl.invoke(self)
      }

      def assert(expected: Boolean) =
        actor
          .ask(_.getInvoked())
          .await() shouldBe expected

      assert(expected = false)
      sleep(1.second)
      assert(expected = false)

      sleep(1.second)
      eventually {
        actor
          .ask(_.getInvoked())
          .await() shouldBe true
      }
      result._1.await() shouldBe true
    }
  }
}