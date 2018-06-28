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

object PingPong extends TestBase with App {

  case class Pong(replyTo: ActorRef[Ping])
  case class Ping(replyTo: ActorRef[Pong])
  case class State(var count: Int)

  val ping =
    Actor[Ping, State](State(0)) {
      case (message, self) =>
        self.state.count += 1
        println(s"Ping: ${self.state.count}")
        sleep(100.millisecond)
        message.replyTo ! Pong(self)
    }

  val pong =
    Actor[Pong, State](State(0)) {
      case (message, self) =>
        self.state.count += 1
        println(s"Pong: ${self.state.count}")
        sleep(100.millisecond)
        message.replyTo ! Ping(self)
    }

  pong ! Pong(ping)

  sleep(5.seconds)
}