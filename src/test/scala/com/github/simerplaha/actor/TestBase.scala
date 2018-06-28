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

import org.scalatest.concurrent.Eventually

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

trait TestBase extends Eventually {

  def sleep(time: FiniteDuration): Unit =
    Thread.sleep(time.toMillis)

  def eventual[A](code: => A): Unit =
    eventual()(code)

  def eventual[A](after: FiniteDuration = 1.second)(code: => A): Unit =
    eventually(timeout(after))(code)

}
