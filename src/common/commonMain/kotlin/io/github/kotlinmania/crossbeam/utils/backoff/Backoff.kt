// port-lint: source crossbeam-utils/src/backoff.rs
package io.github.kotlinmania.crossbeam.utils.backoff

import io.github.kotlinmania.crossbeam.utils.platform.spinLoop
import io.github.kotlinmania.crossbeam.utils.platform.yieldThread

private class DebugStruct(
    private val out: StringBuilder,
    private val name: String,
) {
    private val fields: MutableList<String> = mutableListOf()

    public fun field(name: String, value: Any?): DebugStruct =
        apply {
            fields.add("$name: $value")
        }

    public fun finish() {
        if (fields.isEmpty()) {
            out.append(name)
        } else {
            out.append("$name { ${fields.joinToString(", ")} }")
        }
    }

    public fun finishNonExhaustive() {
        fields.add("..")
        finish()
    }
}

private const val SPIN_LIMIT: UInt = 6u

private const val YIELD_LIMIT: UInt = 10u

/**
 * Performs exponential backoff in spin loops.
 *
 * Keep each instance from concurrent use, as with Rust Cell/non-Sync; Kotlin cannot enforce
 * that bound. Retained emitted examples contain unavailable std-runtime references and
 * documentation translation defects, so they do not establish runnable-Kotlin example parity.
 *
 * Backing off in spin loops reduces contention and improves overall performance.
 *
 * This primitive can execute *YIELD* and *PAUSE* instructions, yield the current thread to the OS
 * scheduler, and tell when is a good time to block the thread using a different synchronization
 * mechanism. Each step of the back off procedure takes roughly twice as long as the previous
 * step.
 *
 * # Examples
 *
 * Backing off in a lock-free loop:
 *
 * ```kotlin
 * import crossbeamutils.Backoff
 * import std.sync.atomic.AtomicUsize
 * import std.sync.atomic.ordering.SeqCst
 *
 * fun fetchMul(a: AtomicUsize, b: Int): Int {
 *     val backoff = Backoff.new()
 *     while (true) {
 *         val value = a.load(SeqCst)
 *         if (a.compareExchange(value, value.wrappingMul(b), SeqCst, SeqCst).isOk()) {
 *             return value
 *         }
 *         backoff.spin()
 *     }
 * }
 * ```
 *
 * Waiting for an [AtomicBool] to become `true`:
 *
 * ```kotlin
 * import crossbeamutils.Backoff
 * import std.sync.atomic.AtomicBool
 * import std.sync.atomic.ordering.SeqCst
 *
 * fun spinWait(ready: AtomicBool) {
 *     val backoff = Backoff.new()
 *     while (!ready.load(SeqCst)) {
 *         backoff.snooze()
 *     }
 * }
 * ```
 *
 * Waiting for an [AtomicBool] to become `true` and parking the thread after a long wait.
 * Note that whoever sets the atomic variable to `true` must notify the parked thread by calling
 * [unpark]:
 *
 * ```kotlin
 * import crossbeamutils.Backoff
 * import std.sync.atomic.AtomicBool
 * import std.sync.atomic.ordering.SeqCst
 *
 * fun blockingWait(ready: AtomicBool) {
 *     val backoff = Backoff.new()
 *     while (!ready.load(SeqCst)) {
 *         if (backoff.isCompleted()) {
 *             thread.park()
 *         } else {
 *             backoff.snooze()
 *         }
 *     }
 * }
 * ```
 *
 * [is_completed]: Backoff::is_completed
 */
public class Backoff private constructor(private var step: UInt) {
    public constructor() : this(0u)

    /**
     * Resets the `Backoff`.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.Backoff
     *
     * val backoff = Backoff.new()
     * backoff.reset()
     * ```
     */
    public fun reset() {
        this.step = 0u
    }

    /**
     * Backs off in a lock-free loop.
     *
     * This method should be used when we need to retry an operation because another thread made
     * progress.
     *
     * The processor may yield using the *YIELD* or *PAUSE* instruction.
     *
     * # Examples
     *
     * Backing off in a lock-free loop:
     *
     * ```kotlin
     * import crossbeamutils.Backoff
     * import std.sync.atomic.AtomicUsize
     * import std.sync.atomic.ordering.SeqCst
     *
     * fun fetchMul(a: AtomicUsize, b: Int): Int {
     *     val backoff = Backoff.new()
     *     while (true) {
     *         val value = a.load(SeqCst)
     *         if (a.compareExchange(value, value.wrappingMul(b), SeqCst, SeqCst).isOk()) {
     *             return value
     *         }
     *         backoff.spin()
     *     }
     * }
     * val a = AtomicUsize.new(7)
     * check(fetchMul(a, 8) == 7)
     * check(a.load(SeqCst) == 56)
     * ```
     */
    public fun spin() {
        repeat(1 shl minOf(this.step, SPIN_LIMIT).toInt()) {
            spinLoop()
        }
        if (this.step <= SPIN_LIMIT) {
            this.step += 1u
        }
    }

    /**
     * Backs off in a blocking loop.
     *
     * This method should be used when we need to wait for another thread to make progress.
     *
     * The processor may yield using the *YIELD* or *PAUSE* instruction and the current thread
     * may yield by giving up a timeslice to the OS scheduler.
     *
     * In `@no_std` environments, this method is equivalent to [spin].
     *
     * If possible, use [is_completed] to check when it is advised to stop using backoff and
     * block the current thread using a different synchronization mechanism instead.
     *
     * [spin]: Backoff::spin
     * [is_completed]: Backoff::is_completed
     *
     * # Examples
     *
     * Waiting for an [AtomicBool] to become `true`:
     *
     * ```kotlin
     * import crossbeamutils.Backoff
     * import std.sync.atomic.AtomicBool
     * import std.sync.atomic.ordering.SeqCst
     * import std.time.Duration
     *
     * fun spinWait(ready: AtomicBool) {
     *     val backoff = Backoff.new()
     *     while (!ready.load(SeqCst)) {
     *         backoff.snooze()
     *     }
     * }
     * val ready = AtomicBool.new(false)
     * val ready2 = ready.clone()
     * thread.spawn({
     *     thread.sleep(Duration.fromMillis(100))
     *     ready2.store(true, SeqCst)
     * })
     * check(ready.load(SeqCst) == false)
     * spinWait(ready)
     * check(ready.load(SeqCst) == true)
     * ```
     */
    public fun snooze() {
        if (this.step <= SPIN_LIMIT) {
            repeat(1 shl this.step.toInt()) {
                spinLoop()
            }
        } else {
            // The selected Rust std branch yields; emitted no_std fallback is incorrect.
            yieldThread()
        }
        if (this.step <= YIELD_LIMIT) {
            this.step += 1u
        }
    }

    /**
     * Returns `true` if exponential backoff has completed and blocking the thread is advised.
     *
     * # Examples
     *
     * Waiting for an [AtomicBool] to become `true` and parking the thread after a long wait:
     *
     * ```kotlin
     * import crossbeamutils.Backoff
     * import std.sync.atomic.AtomicBool
     * import std.sync.atomic.ordering.SeqCst
     * import std.time.Duration
     *
     * fun blockingWait(ready: AtomicBool) {
     *     val backoff = Backoff.new()
     *     while (!ready.load(SeqCst)) {
     *         if (backoff.isCompleted()) {
     *             thread.park()
     *         } else {
     *             backoff.snooze()
     *         }
     *     }
     * }
     * val ready = AtomicBool.new(false)
     * val ready2 = ready.clone()
     * val waiter = thread.current()
     * thread.spawn({
     *     thread.sleep(Duration.fromMillis(100))
     *     ready2.store(true, SeqCst)
     *     waiter.unpark()
     * })
     * check(ready.load(SeqCst) == false)
     * blockingWait(ready)
     * check(ready.load(SeqCst) == true)
     * ```
     */
    public fun is_completed(): Boolean {
        return this.step > YIELD_LIMIT
    }

    override fun toString(): String = StringBuilder().also { fmt(it) }.toString()

    public fun fmt(f: StringBuilder) {
        DebugStruct(f, "Backoff").field("step", "Cell { value: ${this.step} }").field("is_completed", this.is_completed()).finish()
    }

    public companion object {
        /**
         * Creates a new `Backoff`.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamutils.Backoff
         *
         * val backoff = Backoff.new()
         * ```
         */
        public fun new(): Backoff {
            return Backoff(step = 0u)
        }
        public fun default(): Backoff = Backoff.new()

    }
}
