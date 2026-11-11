// port-lint: source crossbeam-epoch/src/sync/queue.rs
package io.github.kotlinmania.crossbeam.epoch.sync.queue

import io.github.kotlinmania.crossbeam.epoch.atomic.Atomic
import io.github.kotlinmania.crossbeam.epoch.atomic.Owned
import io.github.kotlinmania.crossbeam.epoch.atomic.Shared
import io.github.kotlinmania.crossbeam.epoch.guard.Guard
import io.github.kotlinmania.crossbeam.epoch.guard.unprotected
import io.github.kotlinmania.crossbeam.utils.cachepadded.CachePadded

// Michael-Scott lock-free queue.
//
// Usable with any number of producers and consumers.
//
// Michael and Scott.  Simple, Fast, and Practical Non-Blocking and Blocking Concurrent Queue
// Algorithms.  PODC 1996.  <http://dl.acm.org/citation.cfm?id=248106>
//
// Simon Doherty, Lindsay Groves, Victor Luchangco, and Mark Moir. 2004b. Formal Verification of a
// Practical Lock-Free Queue Algorithm. <https://doi.org/10.1007/978-3-540-30232-2_7>

// The representation here is a singly-linked list, with a sentinel node at the front. In general
// the `tail` pointer may lag behind the actual tail. Non-sentinel nodes are either all `Data` or
// all `Blocked` (requests for data from blocked threads).
internal class Queue<T>(public var head: CachePadded<Atomic<Node<T>>> = CachePadded<Atomic<Node<T>>>(), public var tail: CachePadded<Atomic<Node<T>>> = CachePadded<Atomic<Node<T>>>()) {
    // Any particular `T` should never be accessed concurrently, so no need for `Sync`.

    /**
     * Attempts to atomically place `n` into the `next` pointer of `onto`, and returns `true` on
     * success. The queue's `tail` pointer may be updated.
     */
    public fun push_internal(onto: Shared<Node<T>>, new: Shared<Node<T>>, guard: Guard): Boolean {
        // is `onto` the actual tail?
        val o = onto.deref()
        val next = o.next.load(Acquire, guard)
        return if (next != null) {
            // if not, try to "help" by moving the tail pointer forward
            this.tail.compare_exchange(onto, next, Release, Relaxed, guard)
            false
        } else {
            // looks like the actual tail; attempt to link in `n`
            val result = o.next.compare_exchange(0L, new, Release, Relaxed, guard).is_ok()
            if (result) {
                // try to move the tail pointer forward
                this.tail.compare_exchange(onto, new, Release, Relaxed, guard)
            }
            result
        }
    }

    /**
     * Adds `t` to the back of the queue, possibly waking up threads blocked on `pop`.
     */
    internal fun push(t: T, guard: Guard) {
        val new = Owned.new(Node(data = MaybeUninit.new(t), next = 0L))
        val new1 = Owned.into_shared(new, guard)
        while (true) {
            // We push onto the tail, so we'll start optimistically by looking there first.
            val tail = this.tail.load(Acquire, guard)
            // Attempt to push onto the `tail` snapshot; fails if `tail.next` has changed.
            if (this.push_internal(tail, new1, guard)) {
                break
            }
        }
    }

    /**
     * Attempts to pop a data node. `Ok(None)` if queue is empty; `Err(())` if lost race to pop.
     */
    public fun pop_internal(guard: Guard): Result<T?> {
        val head = this.head.load(Acquire, guard)
        val h = head.deref()
        val next = h.next.load(Acquire, guard)
        return when (next) {
            null -> Result.success(null)
            else -> {
                val n = next
                this.head.compare_exchange(head, next, Release, Relaxed, guard).map({
                    val tail = this.tail.load(Relaxed, guard)
                    // Advance the tail so that we don't retire a pointer to a reachable node.
                    if (head == tail) {
                        this.tail.compare_exchange(tail, next, Release, Relaxed, guard)
                    }
                    guard.defer_destroy(head)
                    n.data.assume_init_read()
                }).map_err({ Unit })
            }
        }
    }

    /**
     * Attempts to pop a data node, if the data satisfies the given condition. `Ok(None)` if queue
     * is empty or the data does not satisfy the condition; `Err(())` if lost race to pop.
     */
    public fun pop_if_internal(condition: (T) -> Boolean, guard: Guard): Result<T?> {
        val head = this.head.load(Acquire, guard)
        val h = head.deref()
        val next = h.next.load(Acquire, guard)
        return when {
            next != null && condition(0L) -> {
                val n = next
                this.head.compare_exchange(head, next, Release, Relaxed, guard).map({
                    val tail = this.tail.load(Relaxed, guard)
                    // Advance the tail so that we don't retire a pointer to a reachable node.
                    if (head == tail) {
                        this.tail.compare_exchange(tail, next, Release, Relaxed, guard)
                    }
                    guard.defer_destroy(head)
                    n.data.assume_init_read()
                }).map_err({ Unit })
            }
            next == null -> Result.success(null)
            else -> Result.success(null)
        }
    }

    /**
     * Attempts to dequeue from the front.
     *
     * Returns `None` if the queue is observed to be empty.
     */
    internal fun try_pop(guard: Guard): T? {
        while (true) {
            when (val matchVal = this.pop_internal(guard)) {
                is Ok -> {
                    val (head) = matchVal
                    return head
                }
                else -> {}
            }
        }
    }

    /**
     * Attempts to dequeue from the front, if the item satisfies the given condition.
     *
     * Returns `None` if the queue is observed to be empty, or the head does not satisfy the given
     * condition.
     */
    internal fun try_pop_if(condition: (T) -> Boolean, guard: Guard): T? {
        while (true) {
            when (val matchVal = this.pop_if_internal(condition, guard)) {
                is Ok -> {
                    val (head) = matchVal
                    return head
                }
                else -> {}
            }
        }
    }

    public fun drop() {
        run {
            val guard = unprotected()
            while (this.try_pop(guard) != null) {}
            // Destroy the remaining sentinel node.
            val sentinel = this.head.load(Relaxed, guard)
            drop(sentinel.into_owned())
        }
    }

    public companion object {
        /**
         * Create a new, empty queue.
         */
        internal fun <T> new(): Queue<T> {
            val q = Queue(head = CachePadded.new(0L), tail = CachePadded.new(0L))
            val sentinel = Owned.new(Node(data = Queue<T>(), next = 0L))
            return run {
                val guard = unprotected()
                val sentinel1 = sentinel.into_shared(guard)
                q.head.store(sentinel1, Relaxed)
                q.tail.store(sentinel1, Relaxed)
                q
            }
        }
    }
}

public class Node<T>(
    /**
     * The slot in which a value of type `T` can be stored.
     *
     * The type of `data` is `MaybeUninit<T>` because a `Node<T>` doesn't always contain a `T`.
     * For example, the sentinel node in a queue never contains a value: its slot is always empty.
     * Other nodes start their life with a push operation and contain a value until it gets popped
     * out. After that such empty nodes get added to the collector for destruction.
     */
    public var data: T = T(),
    public var next: Atomic<Node<T>> = Atomic<Node<T>>(),
)
