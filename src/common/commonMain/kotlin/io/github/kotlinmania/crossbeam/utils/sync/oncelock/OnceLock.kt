// port-lint: source crossbeam-utils/src/sync/once_lock.rs
package io.github.kotlinmania.crossbeam.utils.sync.oncelock

internal class OnceLock<T>(public var once: Once = Once(), public var value: UnsafeCell<T> = UnsafeCell<T>()) {
    /**
     * Gets the contents of the cell, initializing it with `f` if the cell
     * was empty.
     *
     * Many threads may call `get_or_init` concurrently with different
     * initializing functions, but it is guaranteed that only one function
     * will be executed.
     *
     * # Panics
     *
     * If `f` panics, the panic is propagated to the caller, and the cell
     * remains uninitialized.
     *
     * It is an error to reentrantly initialize the cell from `f`. The
     * exact outcome is unspecified. Current implementation deadlocks, but
     * this may be changed to a panic in the future.
     */
    internal fun get_or_init(f: () -> T): T {
        // Fast path check
        if (this.once.is_completed()) {
            // SAFETY: The inner value has been initialized
            return this.get_unchecked()
        }
        this.initialize(f)
        // SAFETY: The inner value has been initialized
        return this.get_unchecked()
    }

    public fun initialize(f: () -> T) {
        val slot = this.value.get()
        this.once.call_once({
            val value = f()
            slot.write(MaybeUninit.new(value))
        })
    }

    /**
     * # Safety
     *
     * The value must be initialized
     */
    public fun get_unchecked(): T {
        check(this.once.is_completed())
        return this.value.get()!!
    }

    public fun drop() {
        if (this.once.is_completed()) {
            // SAFETY: The inner value has been initialized
            this.value.get().assume_init_drop()
        }
    }

    public companion object {
        /**
         * Creates a new empty cell.
         */
        internal fun <T> new(): OnceLock<T> {
            return OnceLock(once = Once.new(), value = UnsafeCell.new(OnceLock<T>()))
        }
    }
}
