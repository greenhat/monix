/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
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

package monix.execution.internal

/** An abstraction over the `ForkJoinPool` implementation, meant
  * to target multiple Scala versions.
  */
package object forkJoin {
  private[monix] type ForkJoinPool =
    scala.concurrent.forkjoin.ForkJoinPool
  private[monix] type ForkJoinWorkerThreadFactory =
    scala.concurrent.forkjoin.ForkJoinPool.ForkJoinWorkerThreadFactory
  private[monix] type ForkJoinWorkerThread =
    scala.concurrent.forkjoin.ForkJoinWorkerThread
  private[monix] type ManagedBlocker =
    scala.concurrent.forkjoin.ForkJoinPool.ManagedBlocker
  private[monix] type ForkJoinTask[V] =
    scala.concurrent.forkjoin.ForkJoinTask[V]

  private[monix] object ForkJoinPool {
    def managedBlock(blocker: ManagedBlocker): Unit =
      scala.concurrent.forkjoin.ForkJoinPool.managedBlock(blocker)
  }

  private[monix] def defaultForkJoinWorkerThreadFactory: ForkJoinWorkerThreadFactory =
    scala.concurrent.forkjoin.ForkJoinPool.defaultForkJoinWorkerThreadFactory
}
