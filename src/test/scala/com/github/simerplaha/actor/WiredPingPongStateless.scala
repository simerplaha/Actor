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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object WiredPingPongStateless extends App {

  object WiredPingPong {
    def ping(pingCount: Int, pongCount: Int, replyTo: WiredActor[WiredPingPong.type]): Unit = {
      println(s"pingCount: $pingCount")
      replyTo.send(_.pong(pingCount + 1, pongCount, replyTo))
    }

    def pong(pingCount: Int, pongCount: Int, replyTo: WiredActor[WiredPingPong.type]): Unit = {
      println(s"pongCount: $pongCount")
      replyTo.send(_.ping(pingCount, pongCount + 1, replyTo))
    }
  }

  Actor
    .wire(WiredPingPong)
    .send(_.ping(0, 0, _))

  Thread.sleep(1.seconds.toMillis)
}