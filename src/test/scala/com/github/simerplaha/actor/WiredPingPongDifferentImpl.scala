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

class WiredPingActor(var pingCount: Int) {
  def ping(self: WiredActor[WiredPingActor], replyTo: WiredActor[WiredPongActor]): Unit = {
    pingCount += 1
    println(s"pingCount: $pingCount")
    replyTo.send(_.pong(replyTo, self))
  }
}

class WiredPongActor(var pongCount: Int) {

  def pong(self: WiredActor[WiredPongActor], replyTo: WiredActor[WiredPingActor]): Unit = {
    pongCount += 1
    println(s"pongCount: $pongCount")
    replyTo.send(_.ping(replyTo, self))
  }
}

object Runner extends TestBase with App {

  import scala.concurrent.ExecutionContext.Implicits.global

  val pingWire = Actor.wire(new WiredPingActor(0))
  val pongWire = Actor.wire(new WiredPongActor(0))

  pingWire.send {
    (wire, self) =>
      wire.ping(self, pongWire)
  }

  sleep(1.second)

}