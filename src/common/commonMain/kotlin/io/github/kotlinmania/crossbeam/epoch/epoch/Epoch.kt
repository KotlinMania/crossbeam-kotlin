// port-lint: source crossbeam-epoch/src/epoch.rs
package io.github.kotlinmania.crossbeam.epoch.epoch

import io.github.kotlinmania.crossbeam.epoch.crossbeamutils.src.atomic.atomiccell.atomic
import io.github.kotlinmania.crossbeam.epoch.primitive.sync.atomic.Ordering

// The global epoch
//
// The last bit in this number is unused and is always zero. Every so often the global epoch is
// incremented, i.e. we say it "advances". A pinned participant may advance the global epoch only
// if all currently pinned participants have been pinned in the current epoch.
//
// If an object became garbage in some epoch, then we can be sure that after two advancements no
// participant will hold a reference to it. That is the crux of safe memory reclamation.

// Ideally, we want to always use AtomicU64, but since it is not available on all platforms,
// we only use it when it is available for now.
// Note: On platforms where AtomicU64 is unavailable, we may want to use AtomicCell instead of
// AtomicUsize. (https://github.com/crossbeam-rs/crossbeam/issues/433)
public typealias AtomicEpochRepr = atomic.AtomicU64

public typealias EpochRepr = ULong

public typealias EpochReprSigned = Long

/**
 * An epoch that can be marked as pinned or unpinned.
 *
 * Internally, the epoch is represented as an integer that wraps around at some unspecified point
 * and a flag that represents whether it is pinned or unpinned.
 */
internal data class Epoch(
    /**
     * The least significant bit is set if pinned. The rest of the bits hold the epoch.
     */
    public var data: EpochRepr = 0uL,
) {
    /**
     * Returns the number of epochs `self` is ahead of `rhs`.
     *
     * Internally, epochs are represented as numbers in the range `(isize::MIN / 2) .. (isize::MAX
     * / 2)`, so the returned distance will be in the same interval.
     */
    internal fun wrapping_sub(rhs: Epoch): EpochReprSigned {
        // The result is the same with `(self.data & !1).wrapping_sub(rhs.data & !1) as Long>> 1`,
        // because the possible difference of LSB in `(self.data & !1).wrapping_sub(rhs.data & !1)`
        // will be ignored in the shift operation.
        return (this.data - (rhs.data and 1uL.inv())).toLong() shr 1
    }

    /**
     * Returns `true` if the epoch is marked as pinned.
     */
    internal fun is_pinned(): Boolean {
        return (this.data and 1uL) == 1uL
    }

    /**
     * Returns the same epoch, but marked as pinned.
     */
    internal fun pinned(): Epoch {
        return Epoch(data = this.data or 1uL)
    }

    /**
     * Returns the same epoch, but marked as unpinned.
     */
    internal fun unpinned(): Epoch {
        return Epoch(data = this.data and 1uL.inv())
    }

    /**
     * Returns the successor epoch.
     *
     * The returned epoch will be marked as pinned only if the previous one was as well.
     */
    internal fun successor(): Epoch {
        return Epoch(data = this.data + 2uL)
    }

    public companion object {
        /**
         * Returns the starting epoch in unpinned state.
         */
        internal fun starting(): Epoch {
            return Epoch()
        }
    }
}

/**
 * An atomic value that holds an `Epoch`.
 */
internal data class AtomicEpoch(
    /**
     * Since `Epoch` is just a wrapper around `Int`, an `AtomicEpoch` is similarly represented
     * using an `AtomicUsize`.
     */
    public var data: AtomicEpochRepr = AtomicEpochRepr(),
) {
    /**
     * Loads a value from the atomic epoch.
     */
    internal fun load(ord: Ordering): Epoch {
        return Epoch(data = this.data.load(ord))
    }

    /**
     * Stores a value into the atomic epoch.
     */
    internal fun store(epoch: Epoch, ord: Ordering) {
        this.data.store(epoch.data, ord)
    }

    /**
     * Stores a value into the atomic epoch if the current value is the same as `current`.
     *
     * The return value is a result indicating whether the new value was written and containing
     * the previous value. On success this value is guaranteed to be equal to `current`.
     *
     * This method takes two `Ordering` arguments to describe the memory
     * ordering of this operation. `success` describes the required ordering for the
     * read-modify-write operation that takes place if the comparison with `current` succeeds.
     * `failure` describes the required ordering for the load operation that takes place when
     * the comparison fails. Using `Acquire` as success ordering makes the store part
     * of this operation `Relaxed`, and using `Release` makes the successful load
     * `Relaxed`. The failure ordering can only be `SeqCst`, `Acquire` or `Relaxed`
     * and must be equivalent to or weaker than the success ordering.
     */
    internal fun compare_exchange(current: Epoch, new: Epoch, success: Ordering, failure: Ordering): Result<Epoch> {
        return run {
            val matchVal = this.data.compare_exchange(current.data, new.data, success, failure)
            if (matchVal.isSuccess) {
                val data = matchVal.getOrThrow()
                Result.success(Epoch(data = data))
            } else {
                val data1 = matchVal.exceptionOrNull()!!
                Result.failure(Epoch(data = data1))
            }
        }
    }

    public companion object {
        /**
         * Creates a new atomic epoch.
         */
        internal fun new(epoch: Epoch): AtomicEpoch {
            val data = AtomicEpochRepr.new(epoch.data)
            return AtomicEpoch(data = data)
        }
    }
}
