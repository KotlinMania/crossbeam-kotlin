// port-lint: source crossbeam-deque/src/deque.rs
package io.github.kotlinmania.crossbeam.deque

import io.github.kotlinmania.crossbeam.epoch.atomic.Atomic
import io.github.kotlinmania.crossbeam.epoch.atomic.Owned
import io.github.kotlinmania.crossbeam.utils.backoff.Backoff
import io.github.kotlinmania.crossbeam.utils.cachepadded.CachePadded

// Ideally, we want to always use AtomicU64/AtomicI64, but since they are not available on all platforms,
// we only use them when they are available for now.
// Note: On platforms where AtomicU64/AtomicI64 is unavailable, we may want to use AtomicCell instead of
// AtomicUsize/AtomicIsize. (https://github.com/crossbeam-rs/crossbeam/issues/433)
public typealias AtomicIndex = atomic.AtomicU64

public typealias AtomicWorkerIndex = atomic.AtomicI64

public typealias Index = ULong

public typealias WorkerIndex = Long

// Minimum buffer capacity.
public val MIN_CAP: ULong = 64uL

// Maximum number of tasks that can be stolen in `steal_batch()` and `steal_batch_and_pop()`.
public val MAX_BATCH: ULong = 32uL

// If a buffer of at least this size is retired, thread-local garbage is flushed so that it gets
// deallocated as soon as possible.
public val FLUSH_THRESHOLD_BYTES: ULong = 1uL shl 10

/**
 * A buffer that holds tasks in a worker queue.
 *
 * This is just a pointer to the buffer and its length - dropping an instance of this struct will
 * *not* deallocate the buffer.
 */
public class Buffer<T>(
    /**
     * Pointer to the allocated memory.
     */
    public var ptr: Long = 0L,
    /**
     * Capacity of the buffer. Always a power of two.
     */
    public var cap: ULong = 0uL,
) {
    /**
     * Deallocates the buffer.
     */
    public fun dealloc() {
        drop(Box.from_raw(ptr.slice_from_raw_parts_mut(this.ptr, this.cap)))
    }

    /**
     * Returns a pointer to the task at the specified `index`.
     */
    public fun at(index: WorkerIndex): Long {
        // `self.cap` is always a power of two.
        // We do all the loads at `MaybeUninit` because we might realize, after loading, that we
        // don't actually have the right to access this memory.
        return (this.ptr + ((index and (this.cap - 1).toLong()).toInt()).toLong())
    }

    /**
     * Writes `task` into the specified `index`.
     *
     * This method might be concurrently called with another `read` at the same index, which is
     * technically speaking a data race and therefore UB. We should use an atomic store here, but
     * that would be more expensive and difficult to implement generically for all types `T`.
     * Hence, as a Note, we use a volatile write instead.
     */
    public fun write(index: WorkerIndex, task: T) {
        ptr.write_volatile(this.at(index), task)
    }

    /**
     * Reads a task from the specified `index`.
     *
     * This method might be concurrently called with another `write` at the same index, which is
     * technically speaking a data race and therefore UB. We should use an atomic load here, but
     * that would be more expensive and difficult to implement generically for all types `T`.
     * Hence, as a Note, we use a volatile load instead.
     */
    public fun read(index: WorkerIndex): T {
        return ptr.read_volatile(this.at(index))
    }

    public fun clone(): Buffer<T> {
        return Buffer(this.ptr, this.cap)
    }

    public companion object {
        /**
         * Allocates a new buffer with the specified capacity.
         */
        public fun <T> alloc(cap: Int): Buffer<T> {
            check(cap == cap.next_power_of_two())
            val ptr = Box.into_raw((0 until cap).map({ 0L }).collect<List<Any?>>())
            return Buffer(ptr = ptr, cap = cap)
        }
    }
}

/**
 * Internal queue data shared between the worker and stealers.
 *
 * The implementation is based on the following work:
 *
 * 1. [Chase and Lev. Dynamic circular work-stealing deque. SPAA 2005.][chase-lev]
 * 2. [Le, Pop, Cohen, and Nardelli. Correct and efficient work-stealing for weak memory models.
 *    PPoPP 2013.][weak-mem]
 * 3. [Norris and Demsky. CDSchecker: checking concurrent data structures written with C/C++
 *    atomics. OOPSLA 2013.][checker]
 *
 * [chase-lev]: https://dl.acm.org/citation.cfm?id=1073974
 * [weak-mem]: https://dl.acm.org/citation.cfm?id=2442524
 * [checker]: https://dl.acm.org/citation.cfm?id=2509514
 */
public class Inner<T>(
    /**
     * The front index.
     */
    public var front: AtomicWorkerIndex = AtomicWorkerIndex(),
    /**
     * The back index.
     */
    public var back: AtomicWorkerIndex = AtomicWorkerIndex(),
    /**
     * The underlying buffer.
     */
    public var buffer: CachePadded<Atomic<Buffer<T>>> = CachePadded<Atomic<Buffer<T>>>(),
) {
    public fun drop() {
        // Load the back index, front index, and buffer.
        val b = this.back.get_mut()
        val f = this.front.get_mut()
        run {
            val buffer = this.buffer.load(Ordering.Relaxed, io.github.kotlinmania.crossbeam.epoch.guard.unprotected())
            // Go through the buffer from front to back and drop all tasks in the queue.
            var i = f
            while (i != b) {
                buffer.deref().at(i).drop_in_place()
                i = (i + 1.toLong())
            }
            // Free the memory allocated by the buffer.
            buffer.into_owned().into_box().dealloc()
        }
    }
}

/**
 * Worker queue flavor: FIFO or LIFO.
 */
public enum class Flavor(
    public val value: UInt,
) {
    /**
     * The first-in first-out flavor.
     */
    Fifo(0u),
    /**
     * The last-in first-out flavor.
     */
    Lifo(1u),
}

/**
 * A worker queue.
 *
 * This is a FIFO or LIFO queue that is owned by a single thread, but other threads may steal
 * tasks from it. Task schedulers typically create a single worker queue per thread.
 *
 * # Examples
 *
 * A FIFO worker:
 *
 * ```kotlin
 * import crossbeamdeque.Steal
 * import crossbeamdeque.Worker
 *
 * val w = Worker.newFifo()
 * val s = w.stealer()
 * w.push(1)
 * w.push(2)
 * w.push(3)
 * check(s.steal() == Steal.Success(1))
 * check(w.pop() == 2)
 * check(w.pop() == 3)
 * ```
 *
 * A LIFO worker:
 *
 * ```kotlin
 * import crossbeamdeque.Steal
 * import crossbeamdeque.Worker
 *
 * val w = Worker.newLifo()
 * val s = w.stealer()
 * w.push(1)
 * w.push(2)
 * w.push(3)
 * check(s.steal() == Steal.Success(1))
 * check(w.pop() == 3)
 * check(w.pop() == 2)
 * ```
 */
public class Worker<T>(
    /**
     * A reference to the inner representation of the queue.
     */
    public var inner: CachePadded<Inner<T>> = CachePadded<Inner<T>>(),
    /**
     * A copy of `inner.buffer` for quick access.
     */
    public var buffer: Cell<Buffer<T>> = Cell<Buffer<T>>(),
    /**
     * The flavor of the queue.
     */
    public var flavor: Flavor = Flavor(),
    /**
     * Indicates that the worker cannot be shared among threads.
     */
    public var _marker: Unit = Unit,
) {
    /**
     * Creates a stealer for this queue.
     *
     * The returned stealer can be shared among threads and cloned.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.Worker
     *
     * val w = Worker.newLifo()
     * val s = w.stealer()
     * ```
     */
    public fun stealer(): Stealer<T> {
        return Stealer(inner = this.inner.clone(), flavor = this.flavor)
    }

    /**
     * Resizes the internal buffer to the new capacity of `new_cap`.
     */
    public fun resize(new_cap: Int) {
        // Load the back index, front index, and buffer.
        val b = this.inner.back.load(Ordering.Relaxed)
        val f = this.inner.front.load(Ordering.Relaxed)
        val buffer = this.buffer.get()
        // Allocate a new buffer and copy data from the old buffer to the new one.
        val new = Buffer.alloc(new_cap)
        var i = f
        while (i != b) {
            ptr.copy_nonoverlapping(buffer.at(i), new.at(i), 1)
            i = (i + 1.toLong())
        }
        val guard = io.github.kotlinmania.crossbeam.epoch.default.pin()
        // Replace the old buffer with the new one.
        this.buffer.replace(new)
        val old = this.inner.buffer.swap(Owned.new(new).into_shared(guard), Ordering.Release, guard)
        // Destroy the old buffer later.
        guard.defer_unchecked({ old.into_owned().into_box().dealloc() })
        // If the buffer is very large, then flush the thread-local garbage in order to deallocate
        // it as soon as possible.
        if (mem.size_of<T>() * new_cap >= FLUSH_THRESHOLD_BYTES.toULong()) {
            guard.flush()
        }
    }

    /**
     * Reserves enough capacity so that `reserve_cap` tasks can be pushed without growing the
     * buffer.
     */
    public fun reserve(reserve_cap: Int) {
        if (reserve_cap > 0) {
            // Compute the current length.
            val b = this.inner.back.load(Ordering.Relaxed)
            val f = this.inner.front.load(Ordering.SeqCst)
            val len = (b - f.toLong()).toInt()
            // The current capacity.
            val cap = this.buffer.get().cap
            // Is there enough capacity to push `reserve_cap` tasks?
            if (cap - len < reserve_cap) {
                // Ensure capacity for reserve_cap + len, rounded up to the next power of 2
                val new_cap = (reserve_cap + len).next_power_of_two()
                // Resize the buffer.
                run {
                    this.resize(new_cap)
                }
            }
        }
    }

    /**
     * Returns `true` if the queue is empty.
     *
     * ```kotlin
     * import crossbeamdeque.Worker
     *
     * val w = Worker.newLifo()
     * check(w.isEmpty())
     * w.push(1)
     * check(!w.isEmpty())
     * ```
     */
    public fun is_empty(): Boolean {
        val b = this.inner.back.load(Ordering.Relaxed)
        val f = this.inner.front.load(Ordering.SeqCst)
        return (b - f.toLong()) <= 0
    }

    /**
     * Returns the number of tasks in the deque.
     *
     * ```kotlin
     * import crossbeamdeque.Worker
     *
     * val w = Worker.newLifo()
     * check(w.len() == 0)
     * w.push(1)
     * check(w.len() == 1)
     * w.push(1)
     * check(w.len() == 2)
     * ```
     */
    public fun len(): Int {
        val b = this.inner.back.load(Ordering.Relaxed)
        val f = this.inner.front.load(Ordering.SeqCst)
        return (b - f.toLong()).max(0).toInt()
    }

    /**
     * Pushes a task into the queue.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.Worker
     *
     * val w = Worker.newLifo()
     * w.push(1)
     * w.push(2)
     * ```
     */
    public fun push(task: T) {
        // Load the back index, front index, and buffer.
        val b = this.inner.back.load(Ordering.Relaxed)
        val f = this.inner.front.load(Ordering.Acquire)
        var buffer = this.buffer.get()
        // Calculate the length of the queue.
        val len = (b - f.toLong())
        // Is the queue full?
        if (len >= buffer.cap.toLong()) {
            // Yes. Grow the underlying buffer.
            run {
                this.resize(2 * buffer.cap)
            }
            buffer = this.buffer.get()
        }
        // Write `task` into the slot.
        run {
            buffer.write(b, MaybeUninit.new(task))
        }
        // ThreadSanitizer does not understand fences, so we omit fence and do store with Release ordering.
        atomic.fence(Ordering.Release)
        val store_order = if (false) {
            Ordering.Release
        } else {
            Ordering.Relaxed
        }
        // Increment the back index.
        this.inner.back.store((b + 1.toLong()), store_order)
    }

    /**
     * Pops a task from the queue.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.Worker
     *
     * val w = Worker.newFifo()
     * w.push(1)
     * w.push(2)
     * check(w.pop() == 1)
     * check(w.pop() == 2)
     * check(w.pop() == null)
     * ```
     */
    public fun pop(): T? {
        // Load the back and front index.
        val b = this.inner.back.load(Ordering.Relaxed)
        val f = this.inner.front.load(Ordering.Relaxed)
        // Calculate the length of the queue.
        val len = (b - f.toLong())
        // Is the queue empty?
        if (len <= 0) {
            return null
        }
        return when (this.flavor) {
            // Pop from the front of the queue.
            Flavor.Fifo -> {
                // Try incrementing the front index to pop the task.
                val f1 = this.inner.front.fetch_add(1, Ordering.SeqCst)
                val new_f = (f1 + 1.toLong())
                if ((b - new_f.toLong()) < 0) {
                    this.inner.front.store(f1, Ordering.Relaxed)
                    return null
                }
                run {
                    // Read the popped task.
                    val buffer = this.buffer.get()
                    val task = buffer.read(f1).assume_init()
                    // Shrink the buffer if `len - 1` is less than one fourth of the capacity.
                    if (buffer.cap > MIN_CAP && len <= buffer.cap.toLong() / 4L) {
                        this.resize(buffer.cap / 2)
                    }
                    task
                }
            }
            // Pop from the back of the queue.
            Flavor.Lifo -> {
                // Decrement the back index.
                val b1 = (b - 1.toLong())
                this.inner.back.store(b1, Ordering.Relaxed)
                atomic.fence(Ordering.SeqCst)
                // Load the front index.
                val f2 = this.inner.front.load(Ordering.Relaxed)
                // Compute the length after the back index was decremented.
                val len1 = (b1 - f2.toLong())
                if (len1 < 0) {
                    // The queue is empty. Restore the back index to the original task.
                    this.inner.back.store((b1 + 1.toLong()), Ordering.Relaxed)
                    null
                } else {
                    // Read the task to be popped.
                    val buffer1 = this.buffer.get()
                    var task1 = buffer1.read(b1)
                    // Are we popping the last task from the queue?
                    if (len1 == 0) {
                        // Try incrementing the front index.
                        if (this.inner.front.compare_exchange(f2, (f2 + 1.toLong()), Ordering.SeqCst, Ordering.Relaxed).is_err()) {
                            // Failed. We didn't pop anything. Reset to `None`.
                            task1.also { task1 = null }
                        }
                        // Restore the back index to the original task.
                        this.inner.back.store((b1 + 1.toLong()), Ordering.Relaxed)
                    } else {
                        // Shrink the buffer if `len` is less than one fourth of the capacity.
                        if (buffer1.cap > MIN_CAP && len1 < buffer1.cap.toLong() / 4L) {
                            run {
                                this.resize(buffer1.cap / 2)
                            }
                        }
                    }
                    task1.map({ t -> t.assume_init() })
                }
            }
        }
    }

    public fun fmt(f: StringBuilder) {
        f.append("Worker { .. }")
    }

    public companion object {
        /**
         * Creates a FIFO worker queue.
         *
         * Tasks are pushed and popped from opposite ends.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamdeque.Worker
         *
         * val w = Worker.newFifo()
         * ```
         */
        public fun <T> new_fifo(): Worker<T> {
            val buffer = Buffer.alloc(MIN_CAP)
            val inner = CachePadded.new(Inner(front = AtomicWorkerIndex.new(0), back = AtomicWorkerIndex.new(0), buffer = CachePadded.new(Atomic.new(buffer))))
            return Worker(inner = inner, buffer = Cell.new(buffer), flavor = Flavor.Fifo, _marker = Unit)
        }

        /**
         * Creates a LIFO worker queue.
         *
         * Tasks are pushed and popped from the same end.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamdeque.Worker
         *
         * val w = Worker.newLifo()
         * ```
         */
        public fun <T> new_lifo(): Worker<T> {
            val buffer = Buffer.alloc(MIN_CAP)
            val inner = CachePadded.new(Inner(front = AtomicWorkerIndex.new(0), back = AtomicWorkerIndex.new(0), buffer = CachePadded.new(Atomic.new(buffer))))
            return Worker(inner = inner, buffer = Cell.new(buffer), flavor = Flavor.Lifo, _marker = Unit)
        }
    }
}

/**
 * A stealer handle of a worker queue.
 *
 * Stealers can be shared among threads.
 *
 * Task schedulers typically have a single worker queue per worker thread.
 *
 * # Examples
 *
 * ```kotlin
 * import crossbeamdeque.Steal
 * import crossbeamdeque.Worker
 *
 * val w = Worker.newLifo()
 * w.push(1)
 * w.push(2)
 * val s = w.stealer()
 * check(s.steal() == Steal.Success(1))
 * check(s.steal() == Steal.Success(2))
 * check(s.steal() == Steal.Empty)
 * ```
 */
public class Stealer<T>(
    /**
     * A reference to the inner representation of the queue.
     */
    public var inner: CachePadded<Inner<T>> = CachePadded<Inner<T>>(),
    /**
     * The flavor of the queue.
     */
    public var flavor: Flavor = Flavor(),
) {
    /**
     * Returns `true` if the queue is empty.
     *
     * ```kotlin
     * import crossbeamdeque.Worker
     *
     * val w = Worker.newLifo()
     * val s = w.stealer()
     * check(s.isEmpty())
     * w.push(1)
     * check(!s.isEmpty())
     * ```
     */
    public fun is_empty(): Boolean {
        val f = this.inner.front.load(Ordering.Acquire)
        atomic.fence(Ordering.SeqCst)
        val b = this.inner.back.load(Ordering.Acquire)
        return (b - f.toLong()) <= 0
    }

    /**
     * Returns the number of tasks in the deque.
     *
     * ```kotlin
     * import crossbeamdeque.Worker
     *
     * val w = Worker.newLifo()
     * val s = w.stealer()
     * check(s.len() == 0)
     * w.push(1)
     * check(s.len() == 1)
     * w.push(2)
     * check(s.len() == 2)
     * ```
     */
    public fun len(): Int {
        val f = this.inner.front.load(Ordering.Acquire)
        atomic.fence(Ordering.SeqCst)
        val b = this.inner.back.load(Ordering.Acquire)
        return (b - f.toLong()).max(0).toInt()
    }

    /**
     * Steals a task from the queue.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.Steal
     * import crossbeamdeque.Worker
     *
     * val w = Worker.newLifo()
     * w.push(1)
     * w.push(2)
     * val s = w.stealer()
     * check(s.steal() == Steal.Success(1))
     * check(s.steal() == Steal.Success(2))
     * ```
     */
    public fun steal(): Steal<T> {
        // Load the front index.
        val f = this.inner.front.load(Ordering.Acquire)
        // A SeqCst fence is needed here.
        //
        // If the current thread is already pinned (reentrantly), we must manually issue the
        // fence. Otherwise, the following pinning will issue the fence anyway, so we don't
        // have to.
        if (io.github.kotlinmania.crossbeam.epoch.default.is_pinned()) {
            atomic.fence(Ordering.SeqCst)
        }
        val guard = io.github.kotlinmania.crossbeam.epoch.default.pin()
        // Load the back index.
        val b = this.inner.back.load(Ordering.Acquire)
        // Is the queue empty?
        if ((b - f.toLong()) <= 0) {
            return Steal.Empty
        }
        // Load the buffer and read the task at the front.
        val buffer = this.inner.buffer.load(Ordering.Acquire, guard)
        val task = buffer.deref().read(f)
        // Try incrementing the front index to steal the task.
        // If the buffer has been swapped or the increment fails, we retry.
        if (this.inner.buffer.load(Ordering.Acquire, guard) != buffer || this.inner.front.compare_exchange(f, (f + 1.toLong()), Ordering.SeqCst, Ordering.Relaxed).is_err()) {
            // We didn't steal this task, forget it.
            return Steal.Retry
        }
        // Return the stolen task.
        return Steal.Success(task.assume_init())
    }

    /**
     * Steals a batch of tasks and pushes them into another worker.
     *
     * How many tasks exactly will be stolen is not specified. That said, this method will try to
     * steal around half of the tasks in the queue, but also not more than some constant limit.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.Worker
     *
     * val w1 = Worker.newFifo()
     * w1.push(1)
     * w1.push(2)
     * w1.push(3)
     * w1.push(4)
     * val s = w1.stealer()
     * val w2 = Worker.newFifo()
     * s.stealBatch(w2)
     * check(w2.pop() == 1)
     * check(w2.pop() == 2)
     * ```
     */
    public fun steal_batch(dest: Worker<T>): Steal<Unit> {
        return this.steal_batch_with_limit(dest, MAX_BATCH)
    }

    /**
     * Steals no more than `limit` of tasks and pushes them into another worker.
     *
     * How many tasks exactly will be stolen is not specified. That said, this method will try to
     * steal around half of the tasks in the queue, but also not more than the given limit.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.Worker
     *
     * val w1 = Worker.newFifo()
     * w1.push(1)
     * w1.push(2)
     * w1.push(3)
     * w1.push(4)
     * w1.push(5)
     * w1.push(6)
     * val s = w1.stealer()
     * val w2 = Worker.newFifo()
     * s.stealBatchWithLimit(w2, 2)
     * check(w2.pop() == 1)
     * check(w2.pop() == 2)
     * check(w2.pop() == null)
     * w1.push(7)
     * w1.push(8)
     * // Setting a large limit does not guarantee that all elements will be popped. In this case,
     * // half of the elements are currently popped, but the number of popped elements is considered
     * // an implementation detail that may be changed in the future.
     * s.stealBatchWithLimit(w2, Int.MAX_VALUE)
     * check(w2.len() == 3)
     * ```
     */
    public fun steal_batch_with_limit(dest: Worker<T>, limit: Int): Steal<Unit> {
        check(limit > 0)
        if (Arc.ptr_eq(this.inner, dest.inner)) {
            if (dest.is_empty()) {
                return Steal.Empty
            } else {
                return Steal.Success()
            }
        }
        // Load the front index.
        var f = this.inner.front.load(Ordering.Acquire)
        // A SeqCst fence is needed here.
        //
        // If the current thread is already pinned (reentrantly), we must manually issue the
        // fence. Otherwise, the following pinning will issue the fence anyway, so we don't
        // have to.
        if (io.github.kotlinmania.crossbeam.epoch.default.is_pinned()) {
            atomic.fence(Ordering.SeqCst)
        }
        val guard = io.github.kotlinmania.crossbeam.epoch.default.pin()
        // Load the back index.
        val b = this.inner.back.load(Ordering.Acquire)
        // Is the queue empty?
        val len = (b - f.toLong())
        if (len <= 0) {
            return Steal.Empty
        }
        // Reserve capacity for the stolen batch.
        val batch_size = minOf((len.toInt() + 1) / 2, limit)
        dest.reserve(batch_size)
        var batch_size1 = batch_size.toLong()
        // Get the destination buffer and back index.
        val dest_buffer = dest.buffer.get()
        var dest_b = dest.inner.back.load(Ordering.Relaxed)
        // Load the buffer.
        val buffer = this.inner.buffer.load(Ordering.Acquire, guard)
        when (this.flavor) {
            // Steal a batch of tasks from the front at once.
            Flavor.Fifo -> {
                // Copy the batch from the source to the destination buffer.
                when (dest.flavor) {
                    Flavor.Fifo -> {
                        for (i in 0 until batch_size1) {
                            run {
                                val task = buffer.deref().read((f + i.toLong()))
                                dest_buffer.write((dest_b + i.toLong()), task)
                            }
                        }
                    }
                    Flavor.Lifo -> {
                        for (i1 in 0 until batch_size1) {
                            run {
                                val task1 = buffer.deref().read((f + i1.toLong()))
                                dest_buffer.write((dest_b + (batch_size1 - 1L - i1)), task1)
                            }
                        }
                    }
                }
                // Try incrementing the front index to steal the batch.
                // If the buffer has been swapped or the increment fails, we retry.
                if (this.inner.buffer.load(Ordering.Acquire, guard) != buffer || this.inner.front.compare_exchange(f, (f + batch_size1), Ordering.SeqCst, Ordering.Relaxed).is_err()) {
                    return Steal.Retry
                }
                dest_b = (dest_b + batch_size1)
            }
            // Steal a batch of tasks from the front one by one.
            Flavor.Lifo -> {
                // This loop may modify the batch_size, which triggers a clippy lint warning.
                // Use a new variable to avoid the warning, and to make it clear we aren't
                // modifying the loop exit condition during iteration.
                val original_batch_size = batch_size1
                for (i2 in 0 until original_batch_size) {
                    // If this is not the first steal, check whether the queue is empty.
                    if (i2 > 0) {
                        // We've already got the current front index. Now execute the fence to
                        // synchronize with other threads.
                        atomic.fence(Ordering.SeqCst)
                        // Load the back index.
                        val b1 = this.inner.back.load(Ordering.Acquire)
                        // Is the queue empty?
                        if ((b1 - f.toLong()) <= 0) {
                            batch_size1 = i2
                            break
                        }
                    }
                    // Read the task at the front.
                    val task2 = buffer.deref().read(f)
                    // Try incrementing the front index to steal the task.
                    // If the buffer has been swapped or the increment fails, we retry.
                    if (this.inner.buffer.load(Ordering.Acquire, guard) != buffer || this.inner.front.compare_exchange(f, (f + 1.toLong()), Ordering.SeqCst, Ordering.Relaxed).is_err()) {
                        // We didn't steal this task, forget it and break from the loop.
                        batch_size1 = i2
                        break
                    }
                    // Write the stolen task into the destination buffer.
                    run {
                        dest_buffer.write(dest_b, task2)
                    }
                    // Move the source front index and the destination back index one step forward.
                    f = (f + 1.toLong())
                    dest_b = (dest_b + 1.toLong())
                }
                // If we didn't steal anything, the operation needs to be retried.
                if (batch_size1 == 0L) {
                    return Steal.Retry
                }
                // If stealing into a FIFO queue, stolen tasks need to be reversed.
                if (dest.flavor == Flavor.Fifo) {
                    for (i3 in 0 until batch_size1 / 2L) {
                        run {
                            val i1 = (dest_b - (batch_size1 - i3))
                            val i2 = (dest_b - (i3 + 1).toLong())
                            val t1 = dest_buffer.read(i1)
                            val t2 = dest_buffer.read(i2)
                            dest_buffer.write(i1, t2)
                            dest_buffer.write(i2, t1)
                        }
                    }
                }
            }
        }
        // ThreadSanitizer does not understand fences, so we omit fence and do store with Release ordering.
        atomic.fence(Ordering.Release)
        val store_order = if (false) {
            Ordering.Release
        } else {
            Ordering.Relaxed
        }
        // Update the back index in the destination queue.
        dest.inner.back.store(dest_b, store_order)
        // Return with success.
        return Steal.Success()
    }

    /**
     * Steals a batch of tasks, pushes them into another worker, and pops a task from that worker.
     *
     * How many tasks exactly will be stolen is not specified. That said, this method will try to
     * steal around half of the tasks in the queue, but also not more than some constant limit.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.Steal
     * import crossbeamdeque.Worker
     *
     * val w1 = Worker.newFifo()
     * w1.push(1)
     * w1.push(2)
     * w1.push(3)
     * w1.push(4)
     * val s = w1.stealer()
     * val w2 = Worker.newFifo()
     * check(s.stealBatchAndPop(w2) == Steal.Success(1))
     * check(w2.pop() == 2)
     * ```
     */
    public fun steal_batch_and_pop(dest: Worker<T>): Steal<T> {
        return this.steal_batch_with_limit_and_pop(dest, MAX_BATCH)
    }

    /**
     * Steals no more than `limit` of tasks, pushes them into another worker, and pops a task from
     * that worker.
     *
     * How many tasks exactly will be stolen is not specified. That said, this method will try to
     * steal around half of the tasks in the queue, but also not more than the given limit.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.Steal
     * import crossbeamdeque.Worker
     *
     * val w1 = Worker.newFifo()
     * w1.push(1)
     * w1.push(2)
     * w1.push(3)
     * w1.push(4)
     * w1.push(5)
     * w1.push(6)
     * val s = w1.stealer()
     * val w2 = Worker.newFifo()
     * check(s.stealBatchWithLimitAndPop(w2, 2) == Steal.Success(1))
     * check(w2.pop() == 2)
     * check(w2.pop() == null)
     * w1.push(7)
     * w1.push(8)
     * // Setting a large limit does not guarantee that all elements will be popped. In this case,
     * // half of the elements are currently popped, but the number of popped elements is considered
     * // an implementation detail that may be changed in the future.
     * check(s.stealBatchWithLimitAndPop(w2, Int.MAX_VALUE) == Steal.Success(3))
     * check(w2.pop() == 4)
     * check(w2.pop() == 5)
     * check(w2.pop() == null)
     * ```
     */
    public fun steal_batch_with_limit_and_pop(dest: Worker<T>, limit: Int): Steal<T> {
        check(limit > 0)
        if (Arc.ptr_eq(this.inner, dest.inner)) {
            when (val matchVal = dest.pop()) {
                null -> { return Steal.Empty }
                else -> {
                    val task = matchVal
                    return Steal.Success(task)
                }
            }
        }
        // Load the front index.
        var f = this.inner.front.load(Ordering.Acquire)
        // A SeqCst fence is needed here.
        //
        // If the current thread is already pinned (reentrantly), we must manually issue the
        // fence. Otherwise, the following pinning will issue the fence anyway, so we don't
        // have to.
        if (io.github.kotlinmania.crossbeam.epoch.default.is_pinned()) {
            atomic.fence(Ordering.SeqCst)
        }
        val guard = io.github.kotlinmania.crossbeam.epoch.default.pin()
        // Load the back index.
        val b = this.inner.back.load(Ordering.Acquire)
        // Is the queue empty?
        val len = (b - f.toLong())
        if (len <= 0) {
            return Steal.Empty
        }
        // Reserve capacity for the stolen batch.
        val batch_size = minOf((len.toInt() - 1) / 2, limit - 1)
        dest.reserve(batch_size)
        var batch_size1 = batch_size.toLong()
        // Get the destination buffer and back index.
        val dest_buffer = dest.buffer.get()
        var dest_b = dest.inner.back.load(Ordering.Relaxed)
        // Load the buffer
        val buffer = this.inner.buffer.load(Ordering.Acquire, guard)
        // Read the task at the front.
        var task1 = buffer.deref().read(f)
        when (this.flavor) {
            // Steal a batch of tasks from the front at once.
            Flavor.Fifo -> {
                // Copy the batch from the source to the destination buffer.
                when (dest.flavor) {
                    Flavor.Fifo -> {
                        for (i in 0 until batch_size1) {
                            run {
                                val task2 = buffer.deref().read((f + (i + 1).toLong()))
                                dest_buffer.write((dest_b + i.toLong()), task2)
                            }
                        }
                    }
                    Flavor.Lifo -> {
                        for (i1 in 0 until batch_size1) {
                            run {
                                val task3 = buffer.deref().read((f + (i1 + 1).toLong()))
                                dest_buffer.write((dest_b + (batch_size1 - 1L - i1)), task3)
                            }
                        }
                    }
                }
                // Try incrementing the front index to steal the task.
                // If the buffer has been swapped or the increment fails, we retry.
                if (this.inner.buffer.load(Ordering.Acquire, guard) != buffer || this.inner.front.compare_exchange(f, (f + (batch_size1 + 1L)), Ordering.SeqCst, Ordering.Relaxed).is_err()) {
                    // We didn't steal this task, forget it.
                    return Steal.Retry
                }
                dest_b = (dest_b + batch_size1)
            }
            // Steal a batch of tasks from the front one by one.
            Flavor.Lifo -> {
                // Try incrementing the front index to steal the task.
                if (this.inner.front.compare_exchange(f, (f + 1.toLong()), Ordering.SeqCst, Ordering.Relaxed).is_err()) {
                    // We didn't steal this task, forget it.
                    return Steal.Retry
                }
                // Move the front index one step forward.
                f = (f + 1.toLong())
                // Repeat the same procedure for the batch steals.
                //
                // This loop may modify the batch_size, which triggers a clippy lint warning.
                // Use a new variable to avoid the warning, and to make it clear we aren't
                // modifying the loop exit condition during iteration.
                val original_batch_size = batch_size1
                for (i2 in 0 until original_batch_size) {
                    // We've already got the current front index. Now execute the fence to
                    // synchronize with other threads.
                    atomic.fence(Ordering.SeqCst)
                    // Load the back index.
                    val b1 = this.inner.back.load(Ordering.Acquire)
                    // Is the queue empty?
                    if ((b1 - f.toLong()) <= 0) {
                        batch_size1 = i2
                        break
                    }
                    // Read the task at the front.
                    val tmp = buffer.deref().read(f)
                    // Try incrementing the front index to steal the task.
                    // If the buffer has been swapped or the increment fails, we retry.
                    if (this.inner.buffer.load(Ordering.Acquire, guard) != buffer || this.inner.front.compare_exchange(f, (f + 1.toLong()), Ordering.SeqCst, Ordering.Relaxed).is_err()) {
                        // We didn't steal this task, forget it and break from the loop.
                        batch_size1 = i2
                        break
                    }
                    // Write the previously stolen task into the destination buffer.
                    run {
                        dest_buffer.write(dest_b, mem.replace(task1, tmp))
                    }
                    // Move the source front index and the destination back index one step forward.
                    f = (f + 1.toLong())
                    dest_b = (dest_b + 1.toLong())
                }
                // If stealing into a FIFO queue, stolen tasks need to be reversed.
                if (dest.flavor == Flavor.Fifo) {
                    for (i3 in 0 until batch_size1 / 2L) {
                        run {
                            val i1 = (dest_b - (batch_size1 - i3))
                            val i2 = (dest_b - (i3 + 1).toLong())
                            val t1 = dest_buffer.read(i1)
                            val t2 = dest_buffer.read(i2)
                            dest_buffer.write(i1, t2)
                            dest_buffer.write(i2, t1)
                        }
                    }
                }
            }
        }
        // ThreadSanitizer does not understand fences, so we omit fence and do store with Release ordering.
        atomic.fence(Ordering.Release)
        val store_order = if (false) {
            Ordering.Release
        } else {
            Ordering.Relaxed
        }
        // Update the back index in the destination queue.
        dest.inner.back.store(dest_b, store_order)
        // Return with success.
        return Steal.Success(task1.assume_init())
    }

    public fun clone(): Stealer<T> {
        return Stealer(inner = this.inner.clone(), flavor = this.flavor)
    }

    public fun fmt(f: StringBuilder) {
        f.append("Stealer { .. }")
    }
}

// Bits indicating the state of a slot:
// * If a task has been written into the slot, `WRITE` is set.
// * If a task has been read from the slot, `READ` is set.
// * If the block is being destroyed, `DESTROY` is set.
public val WRITE: ULong = 1uL

public val READ: ULong = 2uL

public val DESTROY: ULong = 4uL

// Each block covers one "lap" of indices.
public val LAP: Index = 64uL

// The maximum number of values a block can hold.
public val BLOCK_CAP: ULong = LAP - 1uL

// How many lower bits are reserved for metadata.
public val SHIFT: ULong = 1uL

// Indicates that the block is not the last one.
public val HAS_NEXT: Index = 1uL

/**
 * A slot in a block.
 */
public class Slot<T>(
    /**
     * The task.
     */
    public var task: UnsafeCell<T> = UnsafeCell<T>(),
    /**
     * The state of the slot.
     */
    public var state: AtomicUsize = AtomicUsize(),
) {
    /**
     * Waits until a task is written into the slot.
     */
    public fun wait_write() {
        val backoff = Backoff.new()
        while (this.state.load(Ordering.Acquire) and WRITE == 0) {
            backoff.snooze()
        }
    }
}

/**
 * A block in a linked list.
 *
 * Each block in the list can hold up to `BLOCK_CAP` values.
 */
public class Block<T>(
    /**
     * The next block in the linked list.
     */
    public var next: AtomicPtr<Block<T>> = AtomicPtr<Block<T>>(),
    /**
     * Slots for values.
     */
    public var slots: List<Slot<T>> = List(BLOCK_CAP.toInt()) { Slot<T>() },
) {
    /**
     * Waits until the next pointer is set.
     */
    public fun wait_next(): Long {
        val backoff = Backoff.new()
        while (true) {
            val next = this.next.load(Ordering.Acquire)
            if (!(next == 0L)) {
                return next
            }
            backoff.snooze()
        }
    }

    public companion object {
        public val LAYOUT: Layout = run {
            val layout = Layout.new<Block<T>>()
            check(layout.size() != 0) { "Block should never be zero-sized, as it has an AtomicPtr field" }
            layout
        }

        /**
         * Creates an empty block.
         */
        public fun <T> new(): Block<T> {
            // SAFETY: layout is not zero-sized
            val ptr = alloc_zeroed(Block.LAYOUT)
            // Handle allocation failure
            if ((ptr == 0L)) {
                handle_alloc_error(Block.LAYOUT)
            }
            // SAFETY: This is safe because:
            // [1] `Block.next` (AtomicPtr) may be safely zero initialized.
            // [2] `Block.slots` (Array) may be safely zero initialized because of [3, 4].
            // [3] `Slot.task` (UnsafeCell) may be safely zero initialized because it
            // holds a MaybeUninit.
            // [4] `Slot.state` (AtomicUsize) may be safely zero initialized.
            // Note: unchecked { Holder::new_zeroed().assume_init() }
            return Box.from_raw(ptr)
        }

        /**
         * Sets the `DESTROY` bit in slots starting from `start` and destroys the block.
         */
        public fun <T> destroy(thisRef: Long, count: Int) {
            // It is not necessary to set the `DESTROY` bit in the last slot because that slot has
            // begun destruction of the block.
            for (i in (0 until count).rev()) {
                val slot = thisRef.slots.get_unchecked(i)
                // Mark the `DESTROY` bit if a thread is still using the slot.
                if (slot.state.load(Ordering.Acquire) and READ == 0 && slot.state.fetch_or(DESTROY, Ordering.AcqRel) and READ == 0) {
                    // If a thread is still using the slot, it will continue destruction of the block.
                    return
                }
            }
            // No thread is using the block, now it is safe to destroy it.
            drop(Box.from_raw(thisRef))
        }
    }
}

/**
 * A position in a queue.
 */
public class Position<T>(
    /**
     * The index in the queue.
     */
    public var index: AtomicIndex = AtomicIndex(),
    /**
     * The block in the linked list.
     */
    public var block: AtomicPtr<Block<T>> = AtomicPtr<Block<T>>(),
)

/**
 * An injector queue.
 *
 * This is a FIFO queue that can be shared among multiple threads. Task schedulers typically have
 * a single injector queue, which is the entry point for new tasks.
 *
 * # Examples
 *
 * ```kotlin
 * import crossbeamdeque.Injector
 * import crossbeamdeque.Steal
 *
 * val q = Injector.new()
 * q.push(1)
 * q.push(2)
 * check(q.steal() == Steal.Success(1))
 * check(q.steal() == Steal.Success(2))
 * check(q.steal() == Steal.Empty)
 * ```
 */
public class Injector<T>(
    /**
     * The head of the queue.
     */
    public var head: CachePadded<Position<T>> = CachePadded<Position<T>>(),
    /**
     * The tail of the queue.
     */
    public var tail: CachePadded<Position<T>> = CachePadded<Position<T>>(),
    /**
     * Indicates that dropping a `Injector<T>` may drop values of type `T`.
     */
    public var _marker: Unit = Unit,
) {
    /**
     * Pushes a task into the queue.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.Injector
     *
     * val w = Injector.new()
     * w.push(1)
     * w.push(2)
     * ```
     */
    public fun push(task: T) {
        val backoff = Backoff.new()
        var tail = this.tail.index.load(Ordering.Acquire)
        var block = this.tail.block.load(Ordering.Acquire)
        var next_block = null
        while (true) {
            // Calculate the offset of the index into the block.
            val offset = ((tail shr SHIFT) % LAP).toInt()
            // If we reached the end of the block, wait until the next one is installed.
            if (offset == BLOCK_CAP) {
                backoff.snooze()
                tail = this.tail.index.load(Ordering.Acquire)
                block = this.tail.block.load(Ordering.Acquire)
                continue
            }
            // If we're going to have to install the next block, allocate it in advance in order to
            // make the wait for other threads as short as possible.
            if (offset + 1 == BLOCK_CAP && next_block == null) {
                next_block = Block.new()
            }
            val new_tail = tail + (1 shl SHIFT)
            // Try advancing the tail forward.
            run {
                val matchVal = this.tail.index.compare_exchange_weak(tail, new_tail, Ordering.SeqCst, Ordering.Acquire)
                if (matchVal.isSuccess) {
                    run {
                        // If we've reached the end of the block, install the next one.
                        if (offset + 1 == BLOCK_CAP) {
                            val next_block1 = Box.into_raw(next_block!!)
                            val next_index = (new_tail + (1 shl SHIFT).toLong())
                            this.tail.block.store(next_block1, Ordering.Release)
                            this.tail.index.store(next_index, Ordering.Release)
                            block.next.store(next_block1, Ordering.Release)
                        }
                        // Write the task into the slot.
                        val slot = block.slots.get_unchecked(offset)
                        slot.task.get().write(MaybeUninit.new(task))
                        slot.state.fetch_or(WRITE, Ordering.Release)
                        return
                    }
                } else {
                    val t = matchVal.exceptionOrNull()!!
                    tail = t
                    block = this.tail.block.load(Ordering.Acquire)
                    backoff.spin()
                }
            }
        }
    }

    /**
     * Steals a task from the queue.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.Injector
     * import crossbeamdeque.Steal
     *
     * val q = Injector.new()
     * q.push(1)
     * q.push(2)
     * check(q.steal() == Steal.Success(1))
     * check(q.steal() == Steal.Success(2))
     * check(q.steal() == Steal.Empty)
     * ```
     */
    public fun steal(): Steal<T> {
        var head: Any?
        var block: Any?
        var offset: Any?
        val backoff = Backoff.new()
        while (true) {
            head = this.head.index.load(Ordering.Acquire)
            block = this.head.block.load(Ordering.Acquire)
            // Calculate the offset of the index into the block.
            offset = ((head shr SHIFT) % LAP).toInt()
            // If we reached the end of the block, wait until the next one is installed.
            if (offset == BLOCK_CAP) {
                backoff.snooze()
            } else {
                break
            }
        }
        var new_head = head + (1 shl SHIFT)
        if (new_head and HAS_NEXT == 0uL) {
            atomic.fence(Ordering.SeqCst)
            val tail = this.tail.index.load(Ordering.Relaxed)
            // If the tail equals the head, that means the queue is empty.
            if (head shr SHIFT == tail shr SHIFT) {
                return Steal.Empty
            }
            // If head and tail are not in the same block, set `HAS_NEXT` in head.
            if ((head shr SHIFT) / LAP != (tail shr SHIFT) / LAP) {
                new_head = new_head or HAS_NEXT
            }
        }
        // Try moving the head index forward.
        if (this.head.index.compare_exchange_weak(head, new_head, Ordering.SeqCst, Ordering.Acquire).is_err()) {
            return Steal.Retry
        }
        return run {
            // If we've reached the end of the block, move to the next one.
            if (offset + 1 == BLOCK_CAP) {
                val next = block.wait_next()
                var next_index = ((new_head and HAS_NEXT.inv()) + (1 shl SHIFT).toLong())
                if (!(next.next.load(Ordering.Relaxed) == 0L)) {
                    next_index = next_index or HAS_NEXT
                }
                this.head.block.store(next, Ordering.Release)
                this.head.index.store(next_index, Ordering.Release)
            }
            // Read the task.
            val slot = block.slots.get_unchecked(offset)
            slot.wait_write()
            val task = slot.task.get().assume_init()
            // Destroy the block if we've reached the end, or if another thread wanted to destroy
            // but couldn't because we were busy reading from the slot.
            if ((offset + 1 == BLOCK_CAP) || (slot.state.fetch_or(READ, Ordering.AcqRel) and DESTROY != 0)) {
                Block.destroy(block, offset)
            }
            Steal.Success(task)
        }
    }

    /**
     * Steals a batch of tasks and pushes them into a worker.
     *
     * How many tasks exactly will be stolen is not specified. That said, this method will try to
     * steal around half of the tasks in the queue, but also not more than some constant limit.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.Injector
     * import crossbeamdeque.Worker
     *
     * val q = Injector.new()
     * q.push(1)
     * q.push(2)
     * q.push(3)
     * q.push(4)
     * val w = Worker.newFifo()
     * q.stealBatch(w)
     * check(w.pop() == 1)
     * check(w.pop() == 2)
     * ```
     */
    public fun steal_batch(dest: Worker<T>): Steal<Unit> {
        return this.steal_batch_with_limit(dest, MAX_BATCH)
    }

    /**
     * Steals no more than `limit` of tasks and pushes them into a worker.
     *
     * How many tasks exactly will be stolen is not specified. That said, this method will try to
     * steal around half of the tasks in the queue, but also not more than some constant limit.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.Injector
     * import crossbeamdeque.Worker
     *
     * val q = Injector.new()
     * q.push(1)
     * q.push(2)
     * q.push(3)
     * q.push(4)
     * q.push(5)
     * q.push(6)
     * val w = Worker.newFifo()
     * q.stealBatchWithLimit(w, 2)
     * check(w.pop() == 1)
     * check(w.pop() == 2)
     * check(w.pop() == null)
     * q.push(7)
     * q.push(8)
     * // Setting a large limit does not guarantee that all elements will be popped. In this case,
     * // half of the elements are currently popped, but the number of popped elements is considered
     * // an implementation detail that may be changed in the future.
     * q.stealBatchWithLimit(w, Int.MAX_VALUE)
     * check(w.len() == 3)
     * ```
     */
    public fun steal_batch_with_limit(dest: Worker<T>, limit: Int): Steal<Unit> {
        check(limit > 0)
        var head: Any?
        var block: Any?
        var offset: Any?
        val backoff = Backoff.new()
        while (true) {
            head = this.head.index.load(Ordering.Acquire)
            block = this.head.block.load(Ordering.Acquire)
            // Calculate the offset of the index into the block.
            offset = ((head shr SHIFT) % LAP).toInt()
            // If we reached the end of the block, wait until the next one is installed.
            if (offset == BLOCK_CAP) {
                backoff.snooze()
            } else {
                break
            }
        }
        var new_head = head
        val advance: Any?
        if (new_head and HAS_NEXT == 0uL) {
            atomic.fence(Ordering.SeqCst)
            val tail = this.tail.index.load(Ordering.Relaxed)
            // If the tail equals the head, that means the queue is empty.
            if (head shr SHIFT == tail shr SHIFT) {
                return Steal.Empty
            }
            // If head and tail are not in the same block, set `HAS_NEXT` in head. Also, calculate
            // the right batch size to steal.
            if ((head shr SHIFT) / LAP != (tail shr SHIFT) / LAP) {
                new_head = new_head or HAS_NEXT
                // We can steal all tasks till the end of the block.
                advance = (BLOCK_CAP - offset).min(limit)
            } else {
                val len = ((tail - head) shr SHIFT).toInt()
                // Steal half of the available tasks.
                advance = ((len + 1) / 2).min(limit)
            }
        } else {
            // We can steal all tasks till the end of the block.
            advance = (BLOCK_CAP - offset).min(limit)
        }
        new_head += (advance.toULong()) shl SHIFT
        val new_offset = offset + advance
        // Try moving the head index forward.
        if (this.head.index.compare_exchange_weak(head, new_head, Ordering.SeqCst, Ordering.Acquire).is_err()) {
            return Steal.Retry
        }
        // Reserve capacity for the stolen batch.
        val batch_size = new_offset - offset
        dest.reserve(batch_size)
        // Get the destination buffer and back index.
        val dest_buffer = dest.buffer.get()
        val dest_b = dest.inner.back.load(Ordering.Relaxed)
        return run {
            // If we've reached the end of the block, move to the next one.
            if (new_offset == BLOCK_CAP) {
                val next = block.wait_next()
                var next_index = ((new_head and HAS_NEXT.inv()) + (1 shl SHIFT).toLong())
                if (!(next.next.load(Ordering.Relaxed) == 0L)) {
                    next_index = next_index or HAS_NEXT
                }
                this.head.block.store(next, Ordering.Release)
                this.head.index.store(next_index, Ordering.Release)
            }
            // Copy values from the injector into the destination queue.
            when (dest.flavor) {
                Flavor.Fifo -> {
                    for (i in 0 until batch_size) {
                        // Read the task.
                        val slot = block.slots.get_unchecked(offset + i)
                        slot.wait_write()
                        val task = slot.task.get()
                        // Write it into the destination queue.
                        dest_buffer.write((dest_b + (i.toLong())), task)
                    }
                }
                Flavor.Lifo -> {
                    for (i1 in 0 until batch_size) {
                        // Read the task.
                        val slot1 = block.slots.get_unchecked(offset + i1)
                        slot1.wait_write()
                        val task1 = slot1.task.get()
                        // Write it into the destination queue.
                        dest_buffer.write((dest_b + ((batch_size - 1 - i1).toLong())), task1)
                    }
                }
            }
            // ThreadSanitizer does not understand fences, so we omit fence and do store with Release ordering.
            atomic.fence(Ordering.Release)
            val store_order = if (false) {
                Ordering.Release
            } else {
                Ordering.Relaxed
            }
            // Update the back index in the destination queue.
            dest.inner.back.store((dest_b + (batch_size.toLong())), store_order)
            // Destroy the block if we've reached the end, or if another thread wanted to destroy
            // but couldn't because we were busy reading from the slot.
            if (new_offset == BLOCK_CAP) {
                Block.destroy(block, offset)
            } else {
                for (i2 in offset until new_offset) {
                    val slot2 = block.slots.get_unchecked(i2)
                    if (slot2.state.fetch_or(READ, Ordering.AcqRel) and DESTROY != 0) {
                        Block.destroy(block, offset)
                        break
                    }
                }
            }
            Steal.Success()
        }
    }

    /**
     * Steals a batch of tasks, pushes them into a worker, and pops a task from that worker.
     *
     * How many tasks exactly will be stolen is not specified. That said, this method will try to
     * steal around half of the tasks in the queue, but also not more than some constant limit.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.Injector
     * import crossbeamdeque.Steal
     * import crossbeamdeque.Worker
     *
     * val q = Injector.new()
     * q.push(1)
     * q.push(2)
     * q.push(3)
     * q.push(4)
     * val w = Worker.newFifo()
     * check(q.stealBatchAndPop(w) == Steal.Success(1))
     * check(w.pop() == 2)
     * ```
     */
    public fun steal_batch_and_pop(dest: Worker<T>): Steal<T> {
        // Note: we use `MAX_BATCH + 1` as the hard limit for Injecter as the performance is slightly
        // better, but we may change it in the future to be compatible with the same method in Stealer.
        return this.steal_batch_with_limit_and_pop(dest, MAX_BATCH + 1)
    }

    /**
     * Steals no more than `limit` of tasks, pushes them into a worker, and pops a task from that worker.
     *
     * How many tasks exactly will be stolen is not specified. That said, this method will try to
     * steal around half of the tasks in the queue, but also not more than the given limit.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.Injector
     * import crossbeamdeque.Steal
     * import crossbeamdeque.Worker
     *
     * val q = Injector.new()
     * q.push(1)
     * q.push(2)
     * q.push(3)
     * q.push(4)
     * q.push(5)
     * q.push(6)
     * val w = Worker.newFifo()
     * check(q.stealBatchWithLimitAndPop(w, 2) == Steal.Success(1))
     * check(w.pop() == 2)
     * check(w.pop() == null)
     * q.push(7)
     * // Setting a large limit does not guarantee that all elements will be popped. In this case,
     * // half of the elements are currently popped, but the number of popped elements is considered
     * // an implementation detail that may be changed in the future.
     * check(q.stealBatchWithLimitAndPop(w, Int.MAX_VALUE) == Steal.Success(3))
     * check(w.pop() == 4)
     * check(w.pop() == 5)
     * check(w.pop() == null)
     * ```
     */
    public fun steal_batch_with_limit_and_pop(dest: Worker<T>, limit: Int): Steal<T> {
        check(limit > 0)
        var head: Any?
        var block: Any?
        var offset: Any?
        val backoff = Backoff.new()
        while (true) {
            head = this.head.index.load(Ordering.Acquire)
            block = this.head.block.load(Ordering.Acquire)
            // Calculate the offset of the index into the block.
            offset = ((head shr SHIFT) % LAP).toInt()
            // If we reached the end of the block, wait until the next one is installed.
            if (offset == BLOCK_CAP) {
                backoff.snooze()
            } else {
                break
            }
        }
        var new_head = head
        val advance: Any?
        if (new_head and HAS_NEXT == 0uL) {
            atomic.fence(Ordering.SeqCst)
            val tail = this.tail.index.load(Ordering.Relaxed)
            // If the tail equals the head, that means the queue is empty.
            if (head shr SHIFT == tail shr SHIFT) {
                return Steal.Empty
            }
            // If head and tail are not in the same block, set `HAS_NEXT` in head.
            if ((head shr SHIFT) / LAP != (tail shr SHIFT) / LAP) {
                new_head = new_head or HAS_NEXT
                // We can steal all tasks till the end of the block.
                advance = (BLOCK_CAP - offset).min(limit)
            } else {
                val len = ((tail - head) shr SHIFT).toInt()
                // Steal half of the available tasks.
                advance = ((len + 1) / 2).min(limit)
            }
        } else {
            // We can steal all tasks till the end of the block.
            advance = (BLOCK_CAP - offset).min(limit)
        }
        new_head += (advance.toULong()) shl SHIFT
        val new_offset = offset + advance
        // Try moving the head index forward.
        if (this.head.index.compare_exchange_weak(head, new_head, Ordering.SeqCst, Ordering.Acquire).is_err()) {
            return Steal.Retry
        }
        // Reserve capacity for the stolen batch.
        val batch_size = new_offset - offset - 1
        dest.reserve(batch_size)
        // Get the destination buffer and back index.
        val dest_buffer = dest.buffer.get()
        val dest_b = dest.inner.back.load(Ordering.Relaxed)
        return run {
            // If we've reached the end of the block, move to the next one.
            if (new_offset == BLOCK_CAP) {
                val next = block.wait_next()
                var next_index = ((new_head and HAS_NEXT.inv()) + (1 shl SHIFT).toLong())
                if (!(next.next.load(Ordering.Relaxed) == 0L)) {
                    next_index = next_index or HAS_NEXT
                }
                this.head.block.store(next, Ordering.Release)
                this.head.index.store(next_index, Ordering.Release)
            }
            // Read the task.
            val slot = block.slots.get_unchecked(offset)
            slot.wait_write()
            val task = slot.task.get()
            when (dest.flavor) {
                Flavor.Fifo -> {
                    // Copy values from the injector into the destination queue.
                    for (i in 0 until batch_size) {
                        // Read the task.
                        val slot1 = block.slots.get_unchecked(offset + i + 1)
                        slot1.wait_write()
                        val task1 = slot1.task.get()
                        // Write it into the destination queue.
                        dest_buffer.write((dest_b + (i.toLong())), task1)
                    }
                }
                Flavor.Lifo -> {
                    // Copy values from the injector into the destination queue.
                    for (i1 in 0 until batch_size) {
                        // Read the task.
                        val slot2 = block.slots.get_unchecked(offset + i1 + 1)
                        slot2.wait_write()
                        val task2 = slot2.task.get()
                        // Write it into the destination queue.
                        dest_buffer.write((dest_b + ((batch_size - 1 - i1).toLong())), task2)
                    }
                }
            }
            // ThreadSanitizer does not understand fences, so we omit fence and do store with Release ordering.
            atomic.fence(Ordering.Release)
            val store_order = if (false) {
                Ordering.Release
            } else {
                Ordering.Relaxed
            }
            // Update the back index in the destination queue.
            dest.inner.back.store((dest_b + (batch_size.toLong())), store_order)
            // Destroy the block if we've reached the end, or if another thread wanted to destroy
            // but couldn't because we were busy reading from the slot.
            if (new_offset == BLOCK_CAP) {
                Block.destroy(block, offset)
            } else {
                for (i2 in offset until new_offset) {
                    val slot3 = block.slots.get_unchecked(i2)
                    if (slot3.state.fetch_or(READ, Ordering.AcqRel) and DESTROY != 0) {
                        Block.destroy(block, offset)
                        break
                    }
                }
            }
            Steal.Success(task.assume_init())
        }
    }

    /**
     * Returns `true` if the queue is empty.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.Injector
     *
     * val q = Injector.new()
     * check(q.isEmpty())
     * q.push(1)
     * check(!q.isEmpty())
     * ```
     */
    public fun is_empty(): Boolean {
        val head = this.head.index.load(Ordering.SeqCst)
        val tail = this.tail.index.load(Ordering.SeqCst)
        return head shr SHIFT == tail shr SHIFT
    }

    /**
     * Returns the number of tasks in the queue.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.Injector
     *
     * val q = Injector.new()
     * check(q.len() == 0)
     * q.push(1)
     * check(q.len() == 1)
     * q.push(1)
     * check(q.len() == 2)
     * ```
     */
    public fun len(): Int {
        while (true) {
            // Load the tail index, then load the head index.
            var tail = this.tail.index.load(Ordering.SeqCst)
            var head = this.head.index.load(Ordering.SeqCst)
            // If the tail index didn't change, we've got consistent indices to work with.
            if (this.tail.index.load(Ordering.SeqCst) == tail) {
                // Erase the lower bits.
                tail = tail and ((1 shl SHIFT) - 1).inv()
                head = head and ((1 shl SHIFT) - 1).inv()
                // Fix up indices if they fall onto block ends.
                if ((tail shr SHIFT) and (LAP - 1uL) == LAP - 1uL) {
                    tail = (tail + (1 shl SHIFT).toLong())
                }
                if ((head shr SHIFT) and (LAP - 1uL) == LAP - 1uL) {
                    head = (head + (1 shl SHIFT).toLong())
                }
                // Rotate indices so that head falls into the first block.
                val lap = (head shr SHIFT) / LAP
                tail = (tail - ((lap * LAP) shl SHIFT).toLong())
                head = (head - ((lap * LAP) shl SHIFT).toLong())
                // Remove the lower bits.
                tail = tail shr SHIFT
                head = head shr SHIFT
                // Return the difference minus the number of blocks between tail and head.
                return (tail - head - tail / LAP).toInt()
            }
        }
    }

    public fun drop() {
        var head = this.head.index.get_mut()
        var tail = this.tail.index.get_mut()
        var block = this.head.block.get_mut()
        // Erase the lower bits.
        head = head and ((1 shl SHIFT) - 1).inv()
        tail = tail and ((1 shl SHIFT) - 1).inv()
        run {
            // Drop all values between `head` and `tail` and deallocate the heap-allocated blocks.
            while (head != tail) {
                val offset = ((head shr SHIFT) % LAP).toInt()
                if (offset < BLOCK_CAP) {
                    // Drop the task in the slot.
                    val slot = block.slots.get_unchecked(offset)
                    slot.task.get().assume_init_drop()
                } else {
                    // Deallocate the block and move to the next one.
                    val next = block.next.get_mut()
                    drop(Box.from_raw(block))
                    block = next
                }
                head = (head + (1 shl SHIFT).toLong())
            }
            // Deallocate the last remaining block.
            drop(Box.from_raw(block))
        }
    }

    public fun fmt(f: StringBuilder) {
        f.append("Injector { .. }")
    }

    public companion object {
        /**
         * Creates a new injector queue.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamdeque.Injector
         *
         * val q = Injector.new()
         * ```
         */
        public fun <T> new(): Injector<T> {
            return Injector.default()
        }
    }
}

/**
 * Possible outcomes of a steal operation.
 *
 * # Examples
 *
 * There are lots of ways to chain results of steal operations together:
 *
 * ```kotlin
 * import crossbeamdeque.Steal
 * import crossbeamdeque.steal.Empty
 * import crossbeamdeque.steal.Retry
 * import crossbeamdeque.steal.Success
 *
 * val collect = { v: List<Steal<Int>> -> v.intoIter().collect<Steal<Int>>() }
 * check(collect(mutableListOf(Empty, Empty, Empty)) == Empty)
 * check(collect(mutableListOf(Empty, Retry, Empty)) == Retry)
 * check(collect(mutableListOf(Retry, Success(1), Empty)) == Success(1))
 * check(collect(mutableListOf(Empty, Empty)).orElse({ Retry }) == Retry)
 * check(collect(mutableListOf(Retry, Empty)).orElse({ Success(1) }) == Success(1))
 * ```
 */
public sealed class Steal<T> {
    /**
     * The queue was empty at the time of stealing.
     */
    public class Empty<T>() : Steal<T>()
    /**
     * At least one task was successfully stolen.
     */
    public data class Success<T>(public var value: T) : Steal<T>()
    /**
     * The steal operation needs to be retried.
     */
    public class Retry<T>() : Steal<T>()

    /**
     * Returns `true` if the queue was empty at the time of stealing.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.steal.Empty
     * import crossbeamdeque.steal.Retry
     * import crossbeamdeque.steal.Success
     *
     * check(!Success(7).isEmpty())
     * check(!Retry.isEmpty())
     * check(Empty.isEmpty())
     * ```
     */
    public fun is_empty(): Boolean {
        return when (this) {
            Steal.Empty -> true
            else -> false
        }
    }

    /**
     * Returns `true` if at least one task was stolen.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.steal.Empty
     * import crossbeamdeque.steal.Retry
     * import crossbeamdeque.steal.Success
     *
     * check(!Empty.isSuccess())
     * check(!Retry.isSuccess())
     * check(Success(7).isSuccess())
     * ```
     */
    public fun is_success(): Boolean {
        return when (this) {
            is Steal.Success -> true
            else -> false
        }
    }

    /**
     * Returns `true` if the steal operation needs to be retried.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.steal.Empty
     * import crossbeamdeque.steal.Retry
     * import crossbeamdeque.steal.Success
     *
     * check(!Empty.isRetry())
     * check(!Success(7).isRetry())
     * check(Retry.isRetry())
     * ```
     */
    public fun is_retry(): Boolean {
        return when (this) {
            Steal.Retry -> true
            else -> false
        }
    }

    /**
     * Returns the result of the operation, if successful.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.steal.Empty
     * import crossbeamdeque.steal.Retry
     * import crossbeamdeque.steal.Success
     *
     * check(Empty.success() == null)
     * check(Retry.success() == null)
     * check(Success(7).success() == 7)
     * ```
     */
    public fun success(): T? {
        return when (this) {
            is Steal.Success -> {
                val (res) = this
                res
            }
            else -> null
        }
    }

    /**
     * If no task was stolen, attempts another steal operation.
     *
     * Returns this steal result if it is `Success`. Otherwise, closure `f` is invoked and then:
     *
     * * If the second steal resulted in `Success`, it is returned.
     * * If both steals were unsuccessful but any resulted in `Retry`, then `Retry` is returned.
     * * If both resulted in `None`, then `None` is returned.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamdeque.steal.Empty
     * import crossbeamdeque.steal.Retry
     * import crossbeamdeque.steal.Success
     *
     * check(Success(1).orElse({ Success(2) }) == Success(1))
     * check(Retry.orElse({ Success(2) }) == Success(2))
     * check(Retry.orElse({ Empty }) == Retry)
     * check(Empty.orElse({ Retry }) == Retry)
     * check(Empty.orElse({ Empty }) == Empty)
     * ```
     */
    public fun or_else(f: () -> Steal<T>): Steal<T> {
        return when (this) {
            Steal.Empty -> f()
            is Steal.Success -> this
            Steal.Retry -> {
                when (val matchVal = f()) {
                    is Steal.Success -> {
                        val (res) = matchVal
                        Steal.Success(res)
                    }
                    else -> {
                        Steal.Retry
                    }
                }
            }
        }
    }

    public fun fmt(f: StringBuilder) {
        when (this) {
            Steal.Empty -> f.append("Empty")
            is Steal.Success -> f.append("Success(..)")
            Steal.Retry -> f.append("Retry")
        }
    }

    public companion object {
        /**
         * Consumes items until a `Success` is found and returns it.
         *
         * If no `Success` was found, but there was at least one `Retry`, then returns `Retry`.
         * Otherwise, `Empty` is returned.
         */
        public fun <T, I> from_iter(iter: I): Steal<T> where I : Iterable<Steal<T>> {
            var retry = false
            for (s in iter) {
                when (s) {
                    Steal.Empty -> {}
                    is Steal.Success -> { return s }
                    Steal.Retry -> retry = true
                }
            }
            return if (retry) {
                Steal.Retry
            } else {
                Steal.Empty
            }
        }
    }
}
