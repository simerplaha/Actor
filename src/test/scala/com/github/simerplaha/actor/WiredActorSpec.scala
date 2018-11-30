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
import scala.concurrent.ExecutionContext.Implicits.global

class WiredActorSpec extends WordSpec with Matchers with TestBase {

  "Actor" should {

    "process messages in order of arrival" in {
      object MyImpl {
        def hello(name: String, replyTo: WiredActor[MyImpl.type]): String = {
          replyTo.send(_.hi("WiredActor"))
          s"Hello $name"
        }

        def hi(name: String): Unit =
          println(s"Hi from $name")
      }

      val actor = Actor.wire(MyImpl)

      actor
        .call {
          (impl, self) =>
            impl.hello("John", self)
        }
        .await() shouldBe "Hello John"

    }
  }
}