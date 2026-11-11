// port-lint: source crossbeam-epoch/src/sync/list.rs
package io.github.kotlinmania.crossbeam.epoch.sync.list

import io.github.kotlinmania.crossbeam.epoch.atomic.Atomic
import io.github.kotlinmania.crossbeam.epoch.atomic.Shared
import io.github.kotlinmania.crossbeam.epoch.guard.Guard
import io.github.kotlinmania.crossbeam.epoch.guard.unprotected

// Lock-free intrusive linked list.
//
// Ideas from Michael.  High Performance Dynamic Lock-Free Hash Tables and List-Based Sets.  SPAA
// 2002.  <http://dl.acm.org/citation.cfm?id=564870.564881>

public typealias Item<T> = Result<T>

/**
 * An entry in a linked list.
 *
 * An Entry is accessed from multiple threads, so it would be beneficial to put it in a different
 * cache-line than thread-local data in terms of performance.
 */
internal data class Entry(
    /**
     * The next entry in the linked list.
     * If the tag is 1, this entry is marked as deleted.
     */
    public var next: Atomic<Entry> = Atomic<Entry>(),
) {
    /**
     * Marks this entry as deleted, deferring the actual deallocation to a later iteration.
     *
     * # Safety
     *
     * The entry should be a member of a linked list, and it should not have been deleted.
     * It should be safe to call `C.finalize` on the entry after the `guard` is dropped, where `C`
     * is the associated helper for the linked list.
     */
    internal fun delete(guard: Guard) {
        this.next.fetch_or(1, Release, guard)
    }
}

/**
 * Implementing this trait asserts that the type `T` can be used as an element in the intrusive
 * linked list defined in this module. `T` has to contain (or otherwise be linked to) an instance
 * of `Entry`.
 *
 * # Example
 *
 * ```kotlin
 * open class A(public var entry: Entry, public var data: Int) : IsElement<A> {
 *     public open fun entryOf(a: A): Entry {
 *         val entryPtr: Entry? = null
 *         return entryPtr!!
 *     }
 *
 *     public open fun elementOf(entry: Entry): T {
 *         val elemPtr: T? = null
 *         return elemPtr!!
 *     }
 *
 *     public open fun finalize(entry: Entry, guard: Guard) {
 *         guard.deferDestroy(Shared.from(((A.elementOf(entry) as Any?) as COpaquePointer?)))
 *     }
 * }
 * ```
 *
 * This trait is implemented on a type separate from `T` (although it can be just `T`), because
 * one type might be placeable into multiple lists, in which case it would require multiple
 * implementations of `IsElement`. In such cases, each struct implementing `IsElement<T>`
 * represents a distinct `Entry` in `T`.
 *
 * For example, we can insert the following struct into two lists using `entry1` for one
 * and `entry2` for the other:
 *
 * ```kotlin
 * open class B(
 *     public var entry1: Entry,
 *     public var entry2: Entry,
 *     public var data: Int,
 * )
 * ```
 */
internal interface IsElement<T> {
    /**
     * Returns a reference to this element's `Entry`.
     */
    public fun entry_of(arg0: T): Entry

    /**
     * Given a reference to an element's entry, returns that element.
     *
     * ```kotlin
     * val elem = ListElement.new()
     * check(elem.entryOf() == ListElement.elementOf(elem.entryOf()))
     * ```
     *
     * # Safety
     *
     * The caller has to guarantee that the `Entry` is called with was retrieved from an instance
     * of the element type (`T`).
     */
    public fun element_of(arg0: Entry): T

    /**
     * The function that is called when an entry is unlinked from list.
     *
     * # Safety
     *
     * The caller has to guarantee that the `Entry` is called with was retrieved from an instance
     * of the element type (`T`).
     */
    public fun finalize(arg0: Entry, arg1: Guard)
}

/**
 * A lock-free, intrusive linked list of type `T`.
 */
internal class List<T, C : IsElement<T>>(
    /**
     * The head of the linked list.
     */
    public var head: Atomic<Entry> = Atomic<Entry>(),
    /**
     * The phantom data for using `T` and `C`.
     */
    public var _marker: Unit = Unit,
) {
    /**
     * Inserts `entry` into the head of the list.
     *
     * # Safety
     *
     * You should guarantee that:
     *
     * - `container` is not null
     * - `container` is immovable, e.g. inside an `Owned`
     * - the same `Entry` is not inserted more than once
     * - the inserted object will be removed before the list is dropped
     */
    internal fun insert(container: Shared<T>, guard: Guard, entry_ofCFn: (Any?) -> Any?) {
        // Insert right after head, i.e. at the beginning of the list.
        val to = this.head
        // Get the intrusively stored Entry of the new element to insert.
        val entry: Entry = entry_ofCFn(container.deref())
        // Make a Shared ptr to that Entry.
        val entry_ptr = Shared.from(((entry as Any?) as Unit))
        // Read the current successor of where we want to insert.
        var next = to.load(Relaxed, guard)
        while (true) {
            // Set the Entry of the to-be-inserted element to point to the previous successor of
            // `to`.
            entry.next.store(next, Relaxed)
            run {
                val matchVal = to.compare_exchange_weak(next, entry_ptr, Release, Relaxed, guard)
                if (matchVal.isSuccess) {
                    break
                } else {
                    val err = matchVal.exceptionOrNull()!!
                    next = err.current
                }
            }
        }
    }

    /**
     * Returns an iterator over all objects.
     *
     * # Caveat
     *
     * Every object that is inserted at the moment this function is called and persists at least
     * until the end of iteration will be returned. Since this iterator traverses a lock-free
     * linked list that may be concurrently modified, some additional caveats apply:
     *
     * 1. If a new object is inserted during iteration, it may or may not be returned.
     * 2. If an object is deleted during iteration, it may or may not be returned.
     * 3. The iteration may be aborted when it lost in a race condition. In this case, the winning
     *    thread will continue to iterate over the same list.
     */
    internal fun iter(guard: Guard): Iter<T, C> {
        return Iter(guard = guard, pred = this.head, curr = this.head.load(Acquire, guard), head = this.head, _marker = Unit)
    }

    public fun drop(finalizeCFn: (Any?) -> Any?) {
        run {
            val guard = unprotected()
            var curr = this.head.load(Relaxed, guard)
            while (true) {
                val matchVal = curr
                if (matchVal == null) break
                val c = matchVal
                val succ = c.next.load(Relaxed, guard)
                // Verify that all elements have been removed from the list.
                check(succ.tag() == 1)
                finalizeCFn(curr.deref(), guard)
                curr = succ
            }
        }
    }

    public companion object {
        /**
         * Returns a new, empty linked list.
         */
        internal fun <T, C : IsElement<T>> new(): List<T, C> {
            return List(head = 0L, _marker = Unit)
        }
    }
}

/**
 * An iterator used for retrieving values from the list.
 */
internal class Iter<T, C : IsElement<T>>(
    /**
     * The guard that protects the iteration.
     */
    public var guard: Guard = Guard(),
    /**
     * Pointer from the predecessor to the current entry.
     */
    public var pred: Atomic<Entry> = Atomic<Entry>(),
    /**
     * The current entry.
     */
    public var curr: Shared<Entry> = Shared<Entry>(),
    /**
     * The list head, needed for restarting iteration.
     */
    public var head: Atomic<Entry> = Atomic<Entry>(),
    /**
     * Logically, we store a borrow of an instance of `T` and
     * use the type information from `C`.
     */
    public var _marker: Unit = Unit,
) : Iterable<Result<T>> {
    override fun iterator(): kotlin.collections.Iterator<Result<T>> = object : AbstractIterator<Result<T>>() {
        override fun computeNext() {
            when (val v = this@Iter.next()) {
                null -> done()
                else -> setNext(v)
            }
        }
    }

    public fun next(finalizeCFn: (Any?) -> Any?, element_ofCFn: (Any?) -> Any?): Result<T>? {
        while (true) {
            val matchVal = this.curr
            if (matchVal == null) break
            val c = matchVal
            val succ = c.next.load(Acquire, this.guard)
            if (succ.tag() == 1) {
                // This entry was removed. Try unlinking it from the list.
                val succ1 = succ.with_tag(0)
                // The tag should always be zero, because removing a node after a logically deleted
                // node leaves the list in an invalid state.
                check(this.curr.tag() == 0)
                // Try to unlink `curr` from the list, and get the new value of `self.pred`.
                val succ2 = run {
                    val matchVal1 = this.pred.compare_exchange(this.curr, succ1, Acquire, Acquire, this.guard)
                    if (matchVal1.isSuccess) {
                        // We succeeded in unlinking `curr`, so we have to schedule
                        // deallocation. Deferred drop is okay, because `list.delete()` can only be
                        // called if `T:`.
                        run {
                            finalizeCFn(this.curr.deref(), this.guard)
                        }
                        // `succ` is the new value of `self.pred`.
                        succ1
                    } else {
                        val e = matchVal1.exceptionOrNull()!!
                        // `e.current` is the current value of `self.pred`.
                        e.current
                    }
                }
                // If the predecessor node is already marked as deleted, we need to restart from
                // `head`.
                if (succ2.tag() != 0) {
                    this.pred = this.head
                    this.curr = this.head.load(Acquire, this.guard)
                    return Result.failure(IterError.Stalled)
                }
                // Move over the removed by only advancing `curr`, not `pred`.
                this.curr = succ2
                continue
            }
            // Move one step forward.
            this.pred = c.next
            this.curr = succ
            return Result.success(element_ofCFn(c))
        }
        // We reached the end of the list.
        return null
    }
}

/**
 * An error that occurs during iteration over the list.
 */
internal enum class IterError(
    public val value: UInt,
) {
    /**
     * A concurrent thread modified the state of the list at the same place that this iterator
     * was inspecting. Subsequent iteration will restart from the beginning of the list.
     */
    Stalled(0u),
}
