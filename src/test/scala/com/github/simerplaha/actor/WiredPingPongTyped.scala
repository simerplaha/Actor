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

import scala.concurrent.duration._

sealed trait WiredPing {
  def ping(replyTo: WiredActor[WiredPong], self: WiredActor[WiredPing]): Unit
}

sealed trait WiredPong {
  def pong(replyTo: WiredActor[WiredPing], self: WiredActor[WiredPong]): Unit
}

class TypedWiredPingPong(var pingCount: Int, var pongCount: Int) extends WiredPing with WiredPong {
  override def ping(replyTo: WiredActor[WiredPong], self: WiredActor[WiredPing]): Unit = {
    pingCount += 1
    println(s"pingCount: $pingCount")
    replyTo.send(_.pong(self, replyTo))
  }

  override def pong(replyTo: WiredActor[WiredPing], self: WiredActor[WiredPong]): Unit = {
    pongCount += 1
    println(s"pongCount: $pongCount")
    replyTo.send(_.ping(self, replyTo))
  }
}

object RunTyped extends TestBase with App {

  import scala.concurrent.ExecutionContext.Implicits.global

  Actor
    .wire(new TypedWiredPingPong(0, 0))
    .send {
      (impl, self) =>
        impl.ping(self, self)
    }

  sleep(1.second)

}