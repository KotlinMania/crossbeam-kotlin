// port-lint: source crossbeam-utils/src/atomic/seq_lock.rs
package io.github.kotlinmania.crossbeam.utils.atomic.seqlock

import io.github.kotlinmania.crossbeam.utils.backoff.Backoff
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A simple stamped lock.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class SeqLock(
    /**
     * The current state of the lock.
     *
     * All bits except the least significant one hold the current stamp. When locked, the state
     * equals 1 and doesn't contain a valid stamp.
     */
    public var state: AtomicLong = AtomicLong(0L),
) {
    /**
     * If not locked, returns the current stamp.
     *
     * This method should be called before optimistic reads.
     */
    internal fun optimistic_read(): Long? {
        val state = this.state.load()
        return if (state == 1L) {
            null
        } else {
            state
        }
    }

    /**
     * Returns `true` if the current stamp is equal to `stamp`.
     *
     * This method should be called after optimistic reads to check whether they are valid. The
     * argument `stamp` should correspond to the one returned by method `optimistic_read`.
     */
    internal fun validate_read(stamp: Long): Boolean {
        // A sequentially consistent RMW supplies the Rust acquire fence and load.
        return this.state.fetchAndAdd(0L) == stamp
    }

    /**
     * Grabs the lock for writing.
     */
    internal fun write(): SeqLockWriteGuard {
        val backoff = Backoff.new()
        while (true) {
            val previous = this.state.exchange(1L)
            if (previous != 1L) {
                // exchange is sequentially consistent, stronger than the Rust release fence.
                return SeqLockWriteGuard(lock = this, state = previous)
            }
            backoff.snooze()
        }
    }

    public companion object {
        internal fun new(): SeqLock {
            return SeqLock(state = AtomicLong(0L))
        }
    }
}

/**
 * An RAII guard that releases the lock and increments the stamp when dropped.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class SeqLockWriteGuard(
    /**
     * The parent lock.
     */
    public var lock: SeqLock = SeqLock(),
    /**
     * The stamp before locking.
     */
    public var state: Long = 0L,
) : AutoCloseable {
    private var released: Boolean = false

    /**
     * Releases the lock without incrementing the stamp.
     */
    internal fun abort() {
        check(!released)
        released = true
        this.lock.state.store(this.state)
        // We specifically don't want to call drop(), since that's
        // what increments the stamp.
        // abort consumes the Rust guard; close must not increment the stamp.
    }

    override fun close(): Unit = drop()

    public fun drop() {
        // Release the lock and increment the stamp.
        if (released) return
        released = true
        this.lock.state.store(this.state + 2L)
    }
}
