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

import java.util.concurrent.ConcurrentLinkedQueue

import org.scalatest.{Matchers, WordSpec}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TCPSpec extends WordSpec with Matchers with TestBase {

  "TCP" should {

    "start a server" in {

      val messagesReceived = new ConcurrentLinkedQueue[String]()

      val server =
        TCP.server[String, String](port = 8000, writeRequest = _.getBytes(), readRequest = new String(_), writeResponse = _.getBytes()) {
          message =>
            messagesReceived.add(message)
            s"response to message $message"
        }.get

      (1 to 10) foreach {
        i =>
          Future(server ! i.toString)
      }

      eventual(messagesReceived.asScala should contain only ((1 to 10).map(_.toString): _*))

      server.shutdown()
    }

    "start a client and server" in {

      val requestsReceived = new ConcurrentLinkedQueue[String]()

      val server =
        TCP.server[String, String](port = 8000, writeRequest = _.getBytes(), readRequest = new String(_), writeResponse = _.getBytes()) {
          message =>
            requestsReceived.add(message)
            message
        }.get

      val responsesReceived = new ConcurrentLinkedQueue[String]()

      val client = TCP.client[String](host = "localhost", port = 8000, writeRequest = _.getBytes(), readResponse = new String(_)) {
        response =>
          responsesReceived.add(response)
      }.get

      (1 to 10) foreach {
        i =>
          Future(client ! i.toString)
      }

      eventual(requestsReceived.asScala should contain only ((1 to 10).map(_.toString): _*))
      eventual(responsesReceived.asScala should contain only ((1 to 10).map(_.toString): _*))

      server.shutdown()
    }
  }
}
