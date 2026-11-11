// port-lint: source crossbeam-utils/src/sync/parker.rs
package io.github.kotlinmania.crossbeam.utils.sync.parker

import io.github.kotlinmania.crossbeam.utils.primitive.sync.Arc
import io.github.kotlinmania.crossbeam.utils.primitive.sync.Condvar
import io.github.kotlinmania.crossbeam.utils.primitive.sync.Mutex
import io.github.kotlinmania.crossbeam.utils.primitive.sync.atomic.AtomicUsize
import io.github.kotlinmania.crossbeam.utils.primitive.sync.atomic.ordering.SeqCst

/**
 * A thread parking primitive.
 *
 * Conceptually, each `Parker` has an associated token which is initially not present:
 *
 * * The [park] method blocks the current thread unless or until the token is available, at
 *   which point it automatically consumes the token.
 *
 * * The [park_timeout] and [park_deadline] methods work the same as [park], but block for
 *   a specified maximum time.
 *
 * * The [unpark] method atomically makes the token available if it wasn't already. Because the
 *   token is initially absent, [unpark] followed by [park] will result in the second call
 *   returning immediately.
 *
 * In other words, each `Parker` acts a bit like a spinlock that can be locked and unlocked using
 * [park] and [unpark].
 *
 * # Examples
 *
 * ```kotlin
 * import crossbeamutils.sync.Parker
 * import std.time.Duration
 *
 * val p = Parker.new()
 * val u = p.unparker().clone()
 * // Make the token available.
 * u.unpark()
 * // Wakes up immediately and consumes the token.
 * p.park()
 * thread.spawn({
 *     thread.sleep(Duration.fromMillis(500))
 *     u.unpark()
 * })
 * // Wakes up when `u.unpark()` provides the token.
 * p.park()
 * ```
 *
 * [park]: Parker::park
 * [park_timeout]: Parker::park_timeout
 * [park_deadline]: Parker::park_deadline
 * [unpark]: Unparker::unpark
 */
public data class Parker(public var unparker: Unparker = Unparker(), public var _marker: Unit = Unit) {
    /**
     * Blocks the current thread until the token is made available.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.sync.Parker
     *
     * val p = Parker.new()
     * val u = p.unparker().clone()
     * // Make the token available.
     * u.unpark()
     * // Wakes up immediately and consumes the token.
     * p.park()
     * ```
     */
    public fun park() {
        this.unparker.inner.park(null)
    }

    /**
     * Blocks the current thread until the token is made available, but only for a limited time.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.sync.Parker
     * import std.time.Duration
     *
     * val p = Parker.new()
     * // Waits for the token to become available, but will not wait longer than 500 ms.
     * p.parkTimeout(Duration.fromMillis(500))
     * ```
     */
    public fun park_timeout(timeout: Duration) {
        when (val matchVal = ((Instant.now()).toLong() + (timeout).toLong()).takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()) {
            null -> this.park()
            else -> {
                val deadline = matchVal
                this.park_deadline(deadline)
            }
        }
    }

    /**
     * Blocks the current thread until the token is made available, or until a certain deadline.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.sync.Parker
     * import std.time.Duration
     * import std.time.Instant
     *
     * val p = Parker.new()
     * val deadline = Instant.now() + Duration.fromMillis(500)
     * // Waits for the token to become available, but will not wait longer than 500 ms.
     * p.parkDeadline(deadline)
     * ```
     */
    public fun park_deadline(deadline: Instant) {
        this.unparker.inner.park(deadline)
    }

    public fun fmt(f: StringBuilder) {
        f.append("Parker { .. }")
    }

    public companion object {
        /**
         * Creates a new `Parker`.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamutils.sync.Parker
         *
         * val p = Parker.new()
         * ```
         */
        public fun new(): Parker {
            return Parker.default()
        }

        /**
         * Converts a `Parker` into a raw pointer.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamutils.sync.Parker
         *
         * val p = Parker.new()
         * val raw = Parker.intoRaw(p)
         * ```
         */
        public fun into_raw(thisRef: Parker): Long {
            return Unparker.into_raw(thisRef.unparker)
        }

        /**
         * Converts a raw pointer into a `Parker`.
         *
         * # Safety
         *
         * This method is safe to use only with pointers returned by [Parker.into_raw].
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamutils.sync.Parker
         *
         * val p = Parker.new()
         * val raw = Parker.intoRaw(p)
         * val p1 = Parker.fromRaw(raw)
         * ```
         */
        public fun from_raw(ptr: Long): Parker {
            return Parker(unparker = Unparker.from_raw(ptr), _marker = Unit)
        }
    }
}

/**
 * Unparks a thread parked by the associated [Parker].
 */
public data class Unparker(public var inner: Inner = Inner()) {
    /**
     * Atomically makes the token available if it is not already.
     *
     * This method will wake up the thread blocked on [park] or [park_timeout], if there is
     * any.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.sync.Parker
     * import std.time.Duration
     *
     * val p = Parker.new()
     * val u = p.unparker().clone()
     * thread.spawn({
     *     thread.sleep(Duration.fromMillis(500))
     *     u.unpark()
     * })
     * // Wakes up when `u.unpark()` provides the token.
     * p.park()
     * ```
     *
     * [park]: Parker::park
     * [park_timeout]: Parker::park_timeout
     */
    public fun unpark() {
        this.inner.unpark()
    }

    public fun fmt(f: StringBuilder) {
        f.append("Unparker { .. }")
    }

    public fun clone(): Unparker {
        return Unparker(inner = this.inner.clone())
    }

    public companion object {
        /**
         * Converts an `Unparker` into a raw pointer.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamutils.sync.Parker
         * import crossbeamutils.sync.Unparker
         *
         * val p = Parker.new()
         * val u = p.unparker().clone()
         * val raw = Unparker.intoRaw(u)
         * ```
         */
        public fun into_raw(thisRef: Unparker): Long {
            return Arc.into_raw(thisRef.inner)
        }

        /**
         * Converts a raw pointer into an `Unparker`.
         *
         * # Safety
         *
         * This method is safe to use only with pointers returned by [Unparker.into_raw].
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamutils.sync.Parker
         * import crossbeamutils.sync.Unparker
         *
         * val p = Parker.new()
         * val u = p.unparker().clone()
         * val raw = Unparker.intoRaw(u)
         * val u1 = Unparker.fromRaw(raw)
         * ```
         */
        public fun from_raw(ptr: Long): Unparker {
            return Unparker(inner = Arc.from_raw(ptr))
        }
    }
}

public val EMPTY: ULong = 0uL

public val PARKED: ULong = 1uL

public val NOTIFIED: ULong = 2uL

public data class Inner(
    public var state: AtomicUsize = AtomicUsize(),
    public var lock: Mutex<Unit> = Mutex<Unit>(),
    public var cvar: Condvar = Condvar(),
) {
    public fun park(deadline: Instant?) {
        // If we were previously notified then we consume this notification and return quickly.
        if (this.state.compare_exchange(NOTIFIED, EMPTY, SeqCst, SeqCst).is_ok()) {
            return
        }
        // If the timeout is zero, then there is no need to actually block.
        if (deadline != null) {
            val deadline1 = deadline
            if (deadline1 <= Instant.now()) {
                return
            }
        }
        // Otherwise we need to coordinate going to sleep.
        var m = this.lock.lock().unwrap()
        run {
            val matchVal = this.state.compare_exchange(EMPTY, PARKED, SeqCst, SeqCst)
            when {
                matchVal is Ok -> {}
                // Consume this notification to avoid spurious wakeups in the next park.
                matchVal is Err && matchVal.value == NOTIFIED -> {
                    val (NOTIFIED) = matchVal
                    // We must read `state` here, even though we know it will be `NOTIFIED`. This is
                    // because `unpark` may have been called again since we read `NOTIFIED` in the
                    // `compare_exchange` above. We must perform an acquire operation that synchronizes
                    // with that `unpark` to observe any writes it made before the call to `unpark`. To
                    // do that we must read from the write it made to `state`.
                    val old = this.state.swap(EMPTY, SeqCst)
                    check(old == NOTIFIED)
                    return
                }
                else -> {
                    val (n) = matchVal
                    error("inconsistent park_timeout state: $n")
                }
            }
        }
        while (true) {
            // Block the current thread on the conditional variable.
            m = when (deadline) {
                null -> this.cvar.wait(m).unwrap()
                else -> {
                    val deadline2 = deadline
                    val now = Instant.now()
                    if (now < deadline2) {
                        // We could check for a timeout here, in the return value of wait_timeout,
                        // but in the case that a timeout and an unpark arrive simultaneously, we
                        // prefer to report the former.
                        this.cvar.wait_timeout(m, deadline2 - now).unwrap().first
                    } else {
                        // We've timed out; swap out the state back to empty on our way out
                        when (val matchVal1 = this.state.swap(EMPTY, SeqCst)) {
                            NOTIFIED, PARKED -> { return }
                            else -> {
                                val n1 = matchVal1
                                error("inconsistent park_timeout state: $n1")
                            }
                        }
                    }
                }
            }
            if (this.state.compare_exchange(NOTIFIED, EMPTY, SeqCst, SeqCst).is_ok()) {
                // got a notification
                return
            }
            // Spurious wakeup, go back to sleep. Alternatively, if we timed out, it will be caught
            // in the branch above, when we discover the deadline is in the past
        }
    }

    internal fun unpark() {
        // To ensure the unparked thread will observe any writes we made before this call, we must
        // perform a release operation that `park` can synchronize with. To do that we must write
        // `NOTIFIED` even if `state` is already `NOTIFIED`. That is why this must be a swap rather
        // than a compare-and-swap that returns if it reads `NOTIFIED` on failure.
        when (this.state.swap(NOTIFIED, SeqCst)) {
            EMPTY -> { return }
            // no one was waiting
            NOTIFIED -> { return }
            // already unparked
            PARKED -> {}
            // gotta go wake someone up
            else -> error("inconsistent state in unpark")
        }
        // There is a period between when the parked thread sets `state` to `PARKED` (or last
        // checked `state` in the case of a spurious wakeup) and when it actually waits on `cvar`.
        // If we were to notify during this period it would be ignored and then when the parked
        // thread went to sleep it would never wake up. Fortunately, it has `lock` locked at this
        // stage so we can acquire `lock` to wait until it is ready to receive the notification.
        //
        // Releasing `lock` before the call to `notify_one` means that when the parked thread wakes
        // it doesn't get woken only to have to wait for us to release `lock`.
        drop(this.lock.lock().unwrap())
        this.cvar.notify_one()
    }
}
