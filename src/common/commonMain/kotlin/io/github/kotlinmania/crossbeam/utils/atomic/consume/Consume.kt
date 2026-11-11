// port-lint: source crossbeam-utils/src/atomic/consume.rs
package io.github.kotlinmania.crossbeam.utils.atomic.consume

import io.github.kotlinmania.crossbeam.utils.primitive.sync.atomic.compiler_fence

public typealias Val = Boolean

/**
 * Trait which allows reading from primitive atomic types with "consume" ordering.
 */
public interface AtomicConsume<Val> {
    /**
     * Loads a value from the atomic using a "consume" memory ordering.
     *
     * This is similar to the "acquire" ordering, except that an ordering is
     * only guaranteed with operations that "depend on" the result of the load.
     * However consume loads are usually much faster than acquire loads on
     * architectures with a weak memory model since they don't require memory
     * fence instructions.
     *
     * The exact definition of "depend on" is a bit vague, but it works as you
     * would expect in practice since a lot of software, especially the Linux
     * kernel, rely on this behavior.
     *
     * This is currently only implemented on ARM and AArch64, where a fence
     * can be avoided. On other architectures this will fall back to a simple
     * `load(Ordering.Acquire)`.
     */
    public fun load_consume(): Val
}

public fun AtomicBool.load_consume(): Boolean {
    val result = this.load(Ordering.Relaxed)
    compiler_fence(Ordering.Acquire)
    return result
}

public fun AtomicUsize.load_consume(): Int {
    val result = this.load(Ordering.Relaxed)
    compiler_fence(Ordering.Acquire)
    return result
}

public fun AtomicIsize.load_consume(): Int {
    val result = this.load(Ordering.Relaxed)
    compiler_fence(Ordering.Acquire)
    return result
}

public fun AtomicU8.load_consume(): UByte {
    val result = this.load(Ordering.Relaxed)
    compiler_fence(Ordering.Acquire)
    return result
}

public fun AtomicI8.load_consume(): Byte {
    val result = this.load(Ordering.Relaxed)
    compiler_fence(Ordering.Acquire)
    return result
}

public fun AtomicU16.load_consume(): UShort {
    val result = this.load(Ordering.Relaxed)
    compiler_fence(Ordering.Acquire)
    return result
}

public fun AtomicI16.load_consume(): Short {
    val result = this.load(Ordering.Relaxed)
    compiler_fence(Ordering.Acquire)
    return result
}

public fun AtomicU32.load_consume(): UInt {
    val result = this.load(Ordering.Relaxed)
    compiler_fence(Ordering.Acquire)
    return result
}

public fun AtomicI32.load_consume(): Int {
    val result = this.load(Ordering.Relaxed)
    compiler_fence(Ordering.Acquire)
    return result
}

public fun AtomicU64.load_consume(): ULong {
    val result = this.load(Ordering.Relaxed)
    compiler_fence(Ordering.Acquire)
    return result
}

public fun AtomicI64.load_consume(): Long {
    val result = this.load(Ordering.Relaxed)
    compiler_fence(Ordering.Acquire)
    return result
}

public fun <T> AtomicPtr<T>.load_consume(): Long {
    val result = this.load(Ordering.Relaxed)
    compiler_fence(Ordering.Acquire)
    return result
}
