/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
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

package monix.execution.cancelables

import monix.execution.Cancelable
import monix.execution.atomic.{AtomicAny, PaddingStrategy}
import scala.annotation.tailrec

/** Represents a composite of cancelables that are stacked,
  * so you can push a new reference, or pop an existing one and
  * when it gets canceled, then the whole stack gets canceled.
  *
  * Similar in spirit with [[CompositeCancelable]], except that
  * you can only pull out references in a FIFO fashion.
  *
  * Used in the implementation of `monix.eval.Task`.
  */
sealed abstract class StackedCancelable extends BooleanCancelable {
  /** Pops the head of the stack and pushes a list as an
    * atomic operation.
    *
    * This operation is the atomic equivalent of doing:
    * {{{
    *   sc.pop()
    *   sc.pushList(list)
    * }}}
    *
    * @param list is the list to prepend to the cancelable stack
    * @return the cancelable reference that was popped from the stack
    */
  def popAndPushList(list: List[Cancelable]): Cancelable

  /** Pops the head of the stack and pushes a list as
    * an atomic operation.
    *
    * This operation is the atomic equivalent of doing:
    * {{{
    *   sc.pop()
    *   sc.push(value)
    * }}}
    *
    * @param value is the cancelable reference to push on the stack
    * @return the cancelable reference that was popped from the stack
    */
  def popAndPush(value: Cancelable): Cancelable

  /** Pushes a whole list of cancelable references on the stack.
    *
    * This operation is the atomic equivalent of doing:
    * {{{
    *   for (c <- list.reverse) sc.push(c)
    * }}}
    *
    * @param list is the list to prepend to the cancelable stack
    */
  def pushList(list: List[Cancelable]): Unit

  /** Pushes a cancelable reference on the stack, to be
    * popped or cancelled later in FIFO order.
    */
  def push(value: Cancelable): Unit

  /** Removes a cancelable reference from the stack in FIFO order.
    *
    * @return the cancelable reference that was removed.
    */
  def pop(): Cancelable
}

object StackedCancelable {
  /** Builds an empty [[StackedCancelable]] reference. */
  def apply(): StackedCancelable =
    new Impl(Nil)

  /** Builds a [[StackedCancelable]] with an initial
    * cancelable reference in it.
    */
  def apply(initial: Cancelable): StackedCancelable =
    new Impl(List(initial))

  /** Builds a [[StackedCancelable]] already initialized with
    * a list of cancelable references, to be popped or canceled
    * later in FIFO order.
    */
  def apply(initial: List[Cancelable]): StackedCancelable =
    new Impl(initial)

  /** Reusable [[StackedCancelable]] reference that is already
    * cancelled.
    */
  final val alreadyCanceled: StackedCancelable =
    new AlreadyCanceled

  /** Implementation for [[StackedCancelable]] backed by a
    * cache-line padded atomic reference for synchronization.
    */
  private final class Impl(initial: List[Cancelable])
    extends StackedCancelable {

    private[this] val state = {
      val ref = if (initial != null) initial else Nil
      AtomicAny.withPadding(ref, PaddingStrategy.LeftRight128)
    }

    override def isCanceled: Boolean =
      state.get == null

    override def cancel(): Unit = {
      // Using getAndSet, which on Java 8 should be faster than
      // a compare-and-set.
      val oldState = state.getAndSet(null)
      if (oldState ne null) oldState.foreach(_.cancel())
    }

    @tailrec def popAndPushList(list: List[Cancelable]): Cancelable = {
      state.get match {
        case null =>
          list.foreach(_.cancel())
          Cancelable.empty
        case Nil =>
          if (!state.compareAndSet(Nil, list))
            popAndPushList(list)
          else
            Cancelable.empty
        case ref @ (head :: tail) =>
          if (!state.compareAndSet(ref, list ::: tail))
            popAndPushList(list)
          else
            head
      }
    }

    @tailrec def popAndPush(value: Cancelable): Cancelable = {
      state.get match {
        case null =>
          value.cancel()
          Cancelable.empty
        case Nil =>
          if (!state.compareAndSet(Nil, value :: Nil))
            popAndPush(value)
          else
            Cancelable.empty
        case ref @ (head :: tail) =>
          if (!state.compareAndSet(ref, value :: tail))
            popAndPush(value) // retry
          else
            head
      }
    }

    @tailrec def pushList(list: List[Cancelable]): Unit = {
      state.get match {
        case null =>
          list.foreach(_.cancel())
        case stack =>
          if (!state.compareAndSet(stack, list ::: stack))
            pushList(list) // retry
      }
    }

    @tailrec def push(value: Cancelable): Unit = {
      state.get match {
        case null =>
          value.cancel()
        case stack =>
          if (!state.compareAndSet(stack, value :: stack))
            push(value) // retry
      }
    }

    @tailrec def pop(): Cancelable = {
      state.get match {
        case null => Cancelable.empty
        case Nil => Cancelable.empty
        case ref @ (head :: tail) =>
          if (!state.compareAndSet(ref, tail))
            pop()
          else
            head
      }
    }
  }

  /** [[StackedCancelable]] implementation that is already cancelled. */
  private final class AlreadyCanceled extends StackedCancelable {
    override def cancel(): Unit = ()
    override def isCanceled: Boolean = true
    override def pop(): Cancelable = Cancelable.empty

    override def push(value: Cancelable): Unit =
      value.cancel()

    override def popAndPushList(list: List[Cancelable]): Cancelable = {
      Cancelable.cancelAll(list)
      Cancelable.empty
    }

    override def popAndPush(value: Cancelable): Cancelable = {
      value.cancel()
      Cancelable.empty
    }

    override def pushList(list: List[Cancelable]): Unit =
      Cancelable.cancelAll(list)
  }
}