// port-lint: source crossbeam-epoch/src/deferred.rs
@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.kotlinmania.crossbeam.epoch.deferred

/**
 * Number of words a piece of `Data` can hold.
 *
 * Three words should be enough for the majority of cases. For example, you can fit inside it the
 * function pointer together with a fat pointer representing an object that needs to be destroyed.
 */
public val DATA_WORDS: ULong = 3uL

/**
 * Some space to keep a `FnOnce()` object on the stack.
 */
public typealias Data = ULongArray

/**
 * A deferred `FnOnce()` callback.
 *
 * Kotlin closures carry their captured values directly; no Rust raw storage is exposed.
 */
internal class Deferred private constructor(private var callback: (() -> Unit)?) {
    public fun fmt(f: StringBuilder) {
        f.append("Deferred { .. }")
    }

    override fun toString(): String = "Deferred { .. }"

    /** Calls the function once, releasing the captured values after invocation. */
    internal fun call() {
        val f = checkNotNull(callback) { "Deferred has already been called" }
        callback = null
        f()
    }

    public companion object {
        internal val NO_OP: Deferred get() = Deferred { }

        /**
         * Constructs a new `Deferred` from a `FnOnce()`.
         *
         * Kotlin stores the closure directly. Its runtime chooses storage placement;
         * the Rust inline-versus-boxed optimization does not apply to Kotlin closures.
         */
        internal fun new(f: () -> Unit): Deferred = Deferred(f)
    }
}
