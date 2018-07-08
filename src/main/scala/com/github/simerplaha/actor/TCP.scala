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

import io.netty.buffer.ByteBuf
import io.reactivex.netty.protocol.tcp.client.TcpClient
import io.reactivex.netty.protocol.tcp.server.TcpServer
import rx.Observable

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

sealed trait TCPServer[-T] extends ActorRef[T] {
  def !(message: T): Unit

  def schedule(message: T, delay: FiniteDuration): TimerTask

  def shutdown(): Unit
}

sealed trait TCPClient[-T] extends ActorRef[T] {
  def !(message: T): Unit

  def schedule(message: T, delay: FiniteDuration): TimerTask
}

/**
  * Both [[TCPServer]] & [[TCPClient]] instances extend [[ActorRef]] to make it easier to write test cases.
  */
object TCP {

  /**
    * NOTE - This implementation is a WIP and requires APIs for client termination handling etc.
    *
    * Initialises a TCP server for the given port. [[client]] can be used to connect with this port or else ! in the
    * returned [[TCPServer]] can be used to send messages to this server.
    *
    * A [[TCPServer]] instance is a subtype of [[ActorRef]], But [[TCPServer]]
    * does not have a message queue and all incoming messages are processed concurrently as managed by
    * [[io.reactivex.netty.protocol.tcp.server.TcpServer]].
    *
    * To process messages sequentially and to use a stateful Actor, send all messages to another actor in the process
    * function.
    */
  def server[T, R](port: Int)(writeRequest: T => Array[Byte],
                              readRequest: Array[Byte] => T,
                              writeResponse: R => Array[Byte])(process: T => R)(implicit ec: ExecutionContext): Try[TCPServer[T]] =
    Try {
      TcpServer.newServer(port)
        .start {
          connection =>
            connection.writeBytesAndFlushOnEach(
              connection
                .getInput
                .map {
                  byteBuff =>
                    val bytes = new Array[Byte](byteBuff.readableBytes())
                    byteBuff.readBytes(bytes)
                    writeResponse(process(readRequest(bytes)))
                }
            )
        }
    } map {
      server =>
        new TCPServer[T] {
          lazy val client =
            TcpClient
              .newClient(server.getServerAddress)
              .createConnectionRequest()

          override def !(message: T): Unit =
            client
              .flatMap {
                connection =>
                  connection.writeBytes(Observable.just(writeRequest(message)))
                    .cast(classOf[ByteBuf])
                    .concatWith(connection.getInput)
              }
              .forEach(_ => ())

          override def schedule(message: T, delay: FiniteDuration): TimerTask =
            Delay.task(delay)(this ! message)

          override def shutdown(): Unit =
            server.shutdown()

        }
    }

  /**
    * NOTE - This implementation is a WIP and requires APIs for client termination handling and reconnect etc.
    *
    * Initialises a TCP client.
    *
    * A [[TCPClient]] instance is a subtype of [[ActorRef]], But [[TCPClient]]
    * does not have a message queue and all messages are forward to the server in a reactive way
    * using [[io.reactivex.netty.protocol.tcp.client.TcpClient]].
    */
  def client[T](host: String,
                port: Int)(writeRequest: T => Array[Byte],
                           readResponse: Array[Byte] => T)(processResponse: T => Unit)(implicit ec: ExecutionContext): Try[TCPClient[T]] =
    Try {
      TcpClient
        .newClient(host, port)
        .createConnectionRequest()
    } map {
      client =>
        new TCPClient[T] {
          override def !(message: T): Unit =
            client
              .flatMap {
                connection =>
                  connection.writeBytes(Observable.just(writeRequest(message)))
                    .cast(classOf[ByteBuf])
                    .concatWith(connection.getInput)
              }
              .forEach {
                response: ByteBuf =>
                  val bytes = new Array[Byte](response.readableBytes())
                  response.readBytes(bytes)
                  processResponse(readResponse(bytes))
              }

          override def schedule(message: T, delay: FiniteDuration): TimerTask =
            Delay.task(delay)(this ! message)
        }
    }
}