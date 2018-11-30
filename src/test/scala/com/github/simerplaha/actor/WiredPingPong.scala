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
import scala.concurrent.ExecutionContext.Implicits.global

object Run extends App {

  class WiredPingPong(var pingCount: Int, var pongCount: Int) {
    def ping(replyTo: WiredActor[WiredPingPong]): Unit = {
      pingCount += 1
      println(s"pingCount: $pingCount")
      replyTo.send(_.pong(replyTo))
    }

    def pong(replyTo: WiredActor[WiredPingPong]): Unit = {
      pongCount += 1
      println(s"pongCount: $pongCount")
      replyTo.send(_.ping(replyTo))
    }
  }

  Actor
    .wire(new WiredPingPong(0, 0))
    .send {
      (impl, self) =>
        impl.ping(self)
    }

  Thread.sleep(1.seconds.toMillis)
}