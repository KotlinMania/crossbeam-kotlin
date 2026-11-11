// port-lint: source crossbeam-epoch/src/atomic.rs
package io.github.kotlinmania.crossbeam.epoch.atomic

import io.github.kotlinmania.crossbeam.epoch.guard.Guard
import io.github.kotlinmania.crossbeam.epoch.primitive.sync.atomic.AtomicUsize
import io.github.kotlinmania.crossbeam.epoch.primitive.sync.atomic.Ordering
import io.github.kotlinmania.crossbeam.utils.atomic.consume.AtomicConsume

public class DebugStruct(
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

/**
 * Given ordering for the success case in a compare-exchange operation, returns the strongest
 * appropriate ordering for the failure case.
 */
public fun strongest_failure_ordering(ord: Ordering): Ordering {
    return when (ord) {
        Relaxed, Release -> Relaxed
        Acquire, AcqRel -> Acquire
        else -> SeqCst
    }
}

// Note: remove in the next major version.

/**
 * The error returned on failed compare-and-set operation.
 */
public typealias CompareAndSetError<T, P> = CompareExchangeError<T, P>

/**
 * The error returned on failed compare-and-swap operation.
 */
public class CompareExchangeError<T : Pointable<T>, P : Pointer<T>>(
    /**
     * The value in the atomic pointer at the time of the failed operation.
     */
    public var current: Shared<T> = Shared<T>(),
    /**
     * The new value, which the operation failed to store.
     */
    public var new: P = P(),
) {
    public fun fmt(f: StringBuilder) {
        DebugStruct(f, "CompareExchangeError").field("current", this.current).field("new", this.new).finish()
    }
}

// Note: remove in the next major version.

/**
 * Memory orderings for compare-and-set operations.
 *
 * A compare-and-set operation can have different memory orderings depending on whether it
 * succeeds or fails. This trait generalizes different ways of specifying memory orderings.
 *
 * The two ways of specifying orderings for compare-and-set are:
 *
 * 1. Just one `Ordering` for the success case. In case of failure, the strongest appropriate
 *    ordering is chosen.
 * 2. A pair of `Ordering`s. The first one is for the success case, while the second one is
 *    for the failure case.
 */
public interface CompareAndSetOrdering {
    /**
     * The ordering of the operation when it succeeds.
     */
    public fun success(): Ordering

    /**
     * The ordering of the operation when it fails.
     *
     * The failure ordering can't be `Release` or `AcqRel` and must be equivalent or weaker than
     * the success ordering.
     */
    public fun failure(): Ordering
}

public fun Ordering.success(): Ordering {
    return this
}

public fun Ordering.failure(): Ordering {
    return strongest_failure_ordering(this)
}

public fun Pair<Ordering, Ordering>.success(): Ordering {
    return this.value
}

public fun Pair<Ordering, Ordering>.failure(): Ordering {
    return this.field1
}

/**
 * Returns a bitmask containing the unused least significant bits of an aligned pointer to `T`.
 */
public fun <Init, T : Pointable<T>> low_bits(): Int {
    return (1 shl T.ALIGN.trailing_zeros().toInt()) - 1
}

/**
 * Panics if the pointer is not properly unaligned.
 */
public fun <Init, T : Pointable<T>> ensure_aligned(raw: Int) {
    check(raw and low_bits<T>().toInt() == 0)
}

/**
 * Given a tagged pointer `data`, returns the same pointer, but tagged with `tag`.
 *
 * `tag` is truncated to fit into the unused bits of the pointer to `T`.
 */
public fun <Init, T : Pointable<T>> compose_tag(data: Int, tag: Int): Int {
    return (data and low_bits<T>().inv()) or (tag and low_bits<T>().toInt())
}

/**
 * Decomposes a tagged pointer `data` into the pointer and the tag.
 */
public fun <Init, T : Pointable<T>> decompose_tag(data: Int): Pair<Int, Int> {
    return Pair(data and low_bits<T>().inv(), data and low_bits<T>().toInt())
}

/**
 * Types that are pointed to by a single word.
 *
 * In concurrent programming, it is necessary to represent an object within a word because atomic
 * operations (e.g., reads, writes, read-modify-writes) support only single words.  This trait
 * qualifies such types that are pointed to by a single word.
 *
 * The trait generalizes `Holder<T>` for a sized type `T`.  In a box, an object of type `T` is
 * allocated in heap and it is owned by a single-word pointer.  This trait is also implemented for
 * `[MaybeUninit<T>]` by storing its size along with its elements and pointing to the pair of array
 * size and elements.
 *
 * Pointers to `Pointable` types can be stored in [Atomic], [Owned], and [Shared].  In
 * particular, Crossbeam supports dynamically sized slices as follows.
 *
 * ```kotlin
 * import crossbeamepoch.Owned
 * import std.mem.MaybeUninit
 *
 * val o = Owned.init(10)
 * // allocating [i32; 10]
 * ```
 */
public interface Pointable<T, Init> {
    /**
     * The alignment of pointer.
     */
    public val ALIGN: ULong

    /**
     * Initializes a with the given initializer.
     *
     * # Safety
     *
     * The result should be a multiple of `ALIGN`.
     */
    public fun init(init: Init): ULong

    /**
     * Dereferences the given pointer.
     *
     * # Safety
     *
     * - The given `ptr` should have been initialized with [Pointable.init].
     * - `ptr` should not have yet been dropped by [Pointable.drop].
     * - `ptr` should not be mutably dereferenced by [Pointable.deref_mut] concurrently.
     */
    public fun deref(ptr: ULong): T

    /**
     * Mutably dereferences the given pointer.
     *
     * # Safety
     *
     * - The given `ptr` should have been initialized with [Pointable.init].
     * - `ptr` should not have yet been dropped by [Pointable.drop].
     * - `ptr` should not be dereferenced by [Pointable.deref] or [Pointable.deref_mut]
     *   concurrently.
     */
    public fun deref_mut(ptr: ULong): T

    /**
     * Drops the object pointed to by the given pointer.
     *
     * # Safety
     *
     * - The given `ptr` should have been initialized with [Pointable.init].
     * - `ptr` should not have yet been dropped by [Pointable.drop].
     * - `ptr` should not be dereferenced by [Pointable.deref] or [Pointable.deref_mut]
     *   concurrently.
     */
    public fun drop(ptr: ULong)
}

public val ALIGN: ULong = mem.align_of<T>()

public fun <T> pointableT(init: T): ULong {
    return (Box.into_raw(init).toInt()).toULong()
}

public fun <T> pointableT(ptr: Int): T {
    return (ptr.toLong())
}

public fun <T> pointableT(ptr: ULong) {
    drop(Box.from_raw(ptr.toLong()))
}

/**
 * Array with size.
 *
 * # Memory layout
 *
 * An array consisting of size and elements:
 *
 * ```text
 *          elements
 *          |
 *          |
 * ------------------------------------
 * | size | 0 | 1 | 2 | 3 | 4 | 5 | 6 |
 * ------------------------------------
 * ```
 *
 * Its memory layout is different from that of `Holder<[T]>` in that size is in the allocation (not
 * along with pointer as in `Holder<[T]>`).
 *
 * Elements are not present in the type, but they will be in the allocation.
 */
public class Array<T>(
    /**
     * The number of elements (not the number of bytes).
     */
    public var len: ULong = 0uL,
    public var elements: List<T> = List(0) { T() },
) {
    public companion object {
        public fun <T> layout(len: Int): Layout {
            return Layout.new<Array<T>>().extend(Layout.array<T>(len).unwrap()).unwrap().first.pad_to_align()
        }
    }
}

public val ALIGN1: ULong = mem.align_of<Array<T>>()

public fun <T> pointableMaybeUninitTSlice(len: Int): Int {
    val layout = Array.layout(len)
    val ptr = alloc(layout)
    if ((ptr == 0L)) {
        handle_alloc_error(layout)
    }
    ptr!!.len.write(len)
    return ptr.toInt()
}

/**
 * An atomic pointer that can be safely shared between threads.
 *
 * The pointer must be properly aligned. Since it is aligned, a tag can be stored into the unused
 * least significant bits of the address. For example, the tag for a pointer to a sized type `T`
 * should be less than `(1<<mem.align_of<T>().trailing_zeros())`.
 *
 * Any method that loads the pointer must be passed a reference to a [Guard].
 *
 * Crossbeam supports dynamically sized types.  See [Pointable] for details.
 */
public class Atomic<T : Pointable<T>>(public var data: AtomicUsize = AtomicUsize(), public var _marker: Unit = Unit) : fmt.Pointer<Atomic<T>>, From<Owned<T>> {
    /**
     * Loads a `Shared` from the atomic pointer.
     *
     * This method takes an [Ordering] argument which describes the memory ordering of this
     * operation.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.new(1234)
     * val guard = epoch.pin()
     * val p = a.load(SeqCst, guard)
     * ```
     */
    public fun load(ord: Ordering, arg1: Guard): Shared<T> {
        return Shared.from_usize(this.data.load(ord))
    }

    /**
     * Loads a `Shared` from the atomic pointer using a "consume" memory ordering.
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
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     *
     * val a = Atomic.new(1234)
     * val guard = epoch.pin()
     * val p = a.loadConsume(guard)
     * ```
     */
    public fun load_consume(arg0: Guard): Shared<T> {
        return Shared.from_usize(this.data.load_consume())
    }

    /**
     * Stores a `Shared` or `Owned` pointer into the atomic pointer.
     *
     * This method takes an [Ordering] argument which describes the memory ordering of this
     * operation.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import crossbeamepoch.Owned
     * import crossbeamepoch.Shared
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.new(1234)
     * a.store(null, SeqCst)
     * a.store(Owned.new(1234), SeqCst)
     * ```
     */
    public fun <P : Pointer<T>> store(new: P, ord: Ordering) {
        this.data.store(new.into_usize(), ord)
    }

    /**
     * Stores a `Shared` or `Owned` pointer into the atomic pointer, returning the previous
     * `Shared`.
     *
     * This method takes an [Ordering] argument which describes the memory ordering of this
     * operation.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import crossbeamepoch.Shared
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.new(1234)
     * val guard = epoch.pin()
     * val p = a.swap(null, SeqCst, guard)
     * ```
     */
    public fun <P : Pointer<T>> swap(new: P, ord: Ordering, arg2: Guard): Shared<T> {
        return Shared.from_usize(this.data.swap(new.into_usize(), ord))
    }

    /**
     * Stores the pointer `new` (either `Shared` or `Owned`) into the atomic pointer if the current
     * value is the same as `current`. The tag is also taken into account, so two pointers to the
     * same object, but with different tags, will not be considered equal.
     *
     * The return value is a result indicating whether the new pointer was written. On success the
     * pointer that was written is returned. On failure the actual current value and `new` are
     * returned.
     *
     * This method takes two `Ordering` arguments to describe the memory
     * ordering of this operation. `success` describes the required ordering for the
     * read-modify-write operation that takes place if the comparison with `current` succeeds.
     * `failure` describes the required ordering for the load operation that takes place when
     * the comparison fails. Using `Acquire` as success ordering makes the store part
     * of this operation `Relaxed`, and using `Release` makes the successful load
     * `Relaxed`. The failure ordering can only be `SeqCst`, `Acquire` or `Relaxed`
     * and must be equivalent to or weaker than the success ordering.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import crossbeamepoch.Owned
     * import crossbeamepoch.Shared
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.new(1234)
     * val guard = epoch.pin()
     * val curr = a.load(SeqCst, guard)
     * val res1 = a.compareExchange(curr, null, SeqCst, SeqCst, guard)
     * val res2 = a.compareExchange(curr, Owned.new(5678), SeqCst, SeqCst, guard)
     * ```
     */
    public fun <P> compare_exchange(current: Shared<T>, new: P, success: Ordering, failure: Ordering, arg4: Guard, from_usizePFn: (Any?) -> Any?): Result<Shared<T>> where P : Pointer<T> {
        val new1 = new.into_usize()
        return this.data.compare_exchange(current.into_usize(), new1, success, failure).map({ Shared.from_usize(new1) }).map_err({ current1 -> CompareExchangeError(current = Shared.from_usize(current1), new = from_usizePFn(new1)) })
    }

    /**
     * Stores the pointer `new` (either `Shared` or `Owned`) into the atomic pointer if the current
     * value is the same as `current`. The tag is also taken into account, so two pointers to the
     * same object, but with different tags, will not be considered equal.
     *
     * Unlike [compare_exchange], this method is allowed to spuriously fail even when comparison
     * succeeds, which can result in more efficient code on some platforms.  The return value is a
     * result indicating whether the new pointer was written. On success the pointer that was
     * written is returned. On failure the actual current value and `new` are returned.
     *
     * This method takes two `Ordering` arguments to describe the memory
     * ordering of this operation. `success` describes the required ordering for the
     * read-modify-write operation that takes place if the comparison with `current` succeeds.
     * `failure` describes the required ordering for the load operation that takes place when
     * the comparison fails. Using `Acquire` as success ordering makes the store part
     * of this operation `Relaxed`, and using `Release` makes the successful load
     * `Relaxed`. The failure ordering can only be `SeqCst`, `Acquire` or `Relaxed`
     * and must be equivalent to or weaker than the success ordering.
     *
     * [compare_exchange]: Atomic::compare_exchange
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import crossbeamepoch.Owned
     * import crossbeamepoch.Shared
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.new(1234)
     * val guard = epoch.pin()
     * var new = Owned.new(5678)
     * var ptr = a.load(SeqCst, guard)
     * while (true) {
     *     run {
     *         val matchVal = a.compareExchangeWeak(ptr, new, SeqCst, SeqCst, guard)
     *         if (matchVal.isSuccess) {
     *             val p = matchVal.getOrThrow()
     *             ptr = p
     *             break
     *         } else {
     *             val err = matchVal.exceptionOrNull()!!
     *             ptr = err.current
     *             new = err.new
     *         }
     *     }
     * }
     * var curr = a.load(SeqCst, guard)
     * while (true) {
     *     run {
     *         val matchVal1 = a.compareExchangeWeak(curr, null, SeqCst, SeqCst, guard)
     *         if (matchVal1.isSuccess) {
     *             break
     *         } else {
     *             val err1 = matchVal1.exceptionOrNull()!!
     *             curr = err1.current
     *         }
     *     }
     * }
     * ```
     */
    public fun <P> compare_exchange_weak(current: Shared<T>, new: P, success: Ordering, failure: Ordering, arg4: Guard, from_usizePFn: (Any?) -> Any?): Result<Shared<T>> where P : Pointer<T> {
        val new1 = new.into_usize()
        return this.data.compare_exchange_weak(current.into_usize(), new1, success, failure).map({ Shared.from_usize(new1) }).map_err({ current1 -> CompareExchangeError(current = Shared.from_usize(current1), new = from_usizePFn(new1)) })
    }

    /**
     * Fetches the pointer, and then applies a function to it that returns a new value.
     * Returns a `Result` of `Ok(previous_value)` if the function returned `Some`, else `Err(_)`.
     *
     * Note that the given function may be called multiple times if the value has been changed by
     * other threads in the meantime, as long as the function returns `Some(_)`, but the function
     * will have been applied only once to the stored value.
     *
     * `fetch_update` takes two [Ordering] arguments to describe the memory
     * ordering of this operation. The first describes the required ordering for
     * when the operation finally succeeds while the second describes the
     * required ordering for loads. These correspond to the success and failure
     * orderings of [Atomic.compare_exchange] respectively.
     *
     * Using [Acquire] as success ordering makes the store part of this
     * operation [Relaxed], and using [Release] makes the final successful
     * load [Relaxed]. The (failed) load ordering can only be [SeqCst],
     * [Acquire] or [Relaxed] and must be equivalent to or weaker than the
     * success ordering.
     *
     * [Relaxed]: Ordering::Relaxed
     * [Acquire]: Ordering::Acquire
     * [Release]: Ordering::Release
     * [SeqCst]: Ordering::SeqCst
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.new(1234)
     * val guard = epoch.pin()
     * val res1 = a.fetchUpdate(SeqCst, SeqCst, guard, { x -> x.withTag(1) })
     * check(res1.isOk())
     * val res2 = a.fetchUpdate(SeqCst, SeqCst, guard, { x -> null })
     * check(res2.isErr())
     * ```
     */
    public fun fetch_update(set_order: Ordering, fail_order: Ordering, guard: Guard, func: (Shared<T>) -> Shared<T>?): Result<Shared<T>> {
        var prev = this.load(fail_order, guard)
        while (true) {
            val matchVal = func(prev)
            if (matchVal == null) break
            val next = matchVal
            run {
                val matchVal1 = this.compare_exchange_weak(prev, next, set_order, fail_order, guard)
                if (matchVal1.isSuccess) {
                    val _result = matchVal1.getOrThrow()
                    return Result.success(prev)
                } else {
                    val next_prev = matchVal1.exceptionOrNull()!!
                    prev = next_prev.current
                }
            }
        }
        return Result.failure(prev)
    }

    // Note: remove in the next major version.

    /**
     * Stores the pointer `new` (either `Shared` or `Owned`) into the atomic pointer if the current
     * value is the same as `current`. The tag is also taken into account, so two pointers to the
     * same object, but with different tags, will not be considered equal.
     *
     * The return value is a result indicating whether the new pointer was written. On success the
     * pointer that was written is returned. On failure the actual current value and `new` are
     * returned.
     *
     * This method takes a [CompareAndSetOrdering] argument which describes the memory
     * ordering of this operation.
     *
     * # Migrating to `compare_exchange`
     *
     * `compare_and_set` is equivalent to `compare_exchange` with the following mapping for
     * memory orderings:
     *
     * Original | Success | Failure
     * -------- | ------- | -------
     * Relaxed  | Relaxed | Relaxed
     * Acquire  | Acquire | Acquire
     * Release  | Release | Relaxed
     * AcqRel   | AcqRel  | Acquire
     * SeqCst   | SeqCst  | SeqCst
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import crossbeamepoch.Owned
     * import crossbeamepoch.Shared
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.new(1234)
     * val guard = epoch.pin()
     * val curr = a.load(SeqCst, guard)
     * val res1 = a.compareAndSet(curr, null, SeqCst, guard)
     * val res2 = a.compareAndSet(curr, Owned.new(5678), SeqCst, guard)
     * ```
     */
    public fun <O, P> compare_and_set(current: Shared<T>, new: P, ord: O, guard: Guard): Result<Shared<T>> where O : CompareAndSetOrdering, P : Pointer<T> {
        return this.compare_exchange(current, new, ord.success(), ord.failure(), guard)
    }

    // Note: remove in the next major version.

    /**
     * Stores the pointer `new` (either `Shared` or `Owned`) into the atomic pointer if the current
     * value is the same as `current`. The tag is also taken into account, so two pointers to the
     * same object, but with different tags, will not be considered equal.
     *
     * Unlike [compare_and_set], this method is allowed to spuriously fail even when comparison
     * succeeds, which can result in more efficient code on some platforms.  The return value is a
     * result indicating whether the new pointer was written. On success the pointer that was
     * written is returned. On failure the actual current value and `new` are returned.
     *
     * This method takes a [CompareAndSetOrdering] argument which describes the memory
     * ordering of this operation.
     *
     * [compare_and_set]: Atomic::compare_and_set
     *
     * # Migrating to `compare_exchange_weak`
     *
     * `compare_and_set_weak` is equivalent to `compare_exchange_weak` with the following mapping for
     * memory orderings:
     *
     * Original | Success | Failure
     * -------- | ------- | -------
     * Relaxed  | Relaxed | Relaxed
     * Acquire  | Acquire | Acquire
     * Release  | Release | Relaxed
     * AcqRel   | AcqRel  | Acquire
     * SeqCst   | SeqCst  | SeqCst
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import crossbeamepoch.Owned
     * import crossbeamepoch.Shared
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.new(1234)
     * val guard = epoch.pin()
     * var new = Owned.new(5678)
     * var ptr = a.load(SeqCst, guard)
     * while (true) {
     *     run {
     *         val matchVal = a.compareAndSetWeak(ptr, new, SeqCst, guard)
     *         if (matchVal.isSuccess) {
     *             val p = matchVal.getOrThrow()
     *             ptr = p
     *             break
     *         } else {
     *             val err = matchVal.exceptionOrNull()!!
     *             ptr = err.current
     *             new = err.new
     *         }
     *     }
     * }
     * var curr = a.load(SeqCst, guard)
     * while (true) {
     *     run {
     *         val matchVal1 = a.compareAndSetWeak(curr, null, SeqCst, guard)
     *         if (matchVal1.isSuccess) {
     *             break
     *         } else {
     *             val err1 = matchVal1.exceptionOrNull()!!
     *             curr = err1.current
     *         }
     *     }
     * }
     * ```
     */
    public fun <O, P> compare_and_set_weak(current: Shared<T>, new: P, ord: O, guard: Guard): Result<Shared<T>> where O : CompareAndSetOrdering, P : Pointer<T> {
        return this.compare_exchange_weak(current, new, ord.success(), ord.failure(), guard)
    }

    /**
     * Bitwise "and" with the current tag.
     *
     * Performs a bitwise "and" operation on the current tag and the argument `val`, and sets the
     * new tag to the result. Returns the previous pointer.
     *
     * This method takes an [Ordering] argument which describes the memory ordering of this
     * operation.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import crossbeamepoch.Shared
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.from(null.withTag(3))
     * val guard = epoch.pin()
     * check(a.fetchAnd(2, SeqCst, guard).tag() == 3)
     * check(a.load(SeqCst, guard).tag() == 2)
     * ```
     */
    public fun fetch_and(value: Int, ord: Ordering, arg2: Guard): Shared<T> {
        return Shared.from_usize(this.data.fetch_and(value or low_bits<T>().inv(), ord))
    }

    /**
     * Bitwise "or" with the current tag.
     *
     * Performs a bitwise "or" operation on the current tag and the argument `val`, and sets the
     * new tag to the result. Returns the previous pointer.
     *
     * This method takes an [Ordering] argument which describes the memory ordering of this
     * operation.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import crossbeamepoch.Shared
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.from(null.withTag(1))
     * val guard = epoch.pin()
     * check(a.fetchOr(2, SeqCst, guard).tag() == 1)
     * check(a.load(SeqCst, guard).tag() == 3)
     * ```
     */
    public fun fetch_or(value: Int, ord: Ordering, arg2: Guard): Shared<T> {
        return Shared.from_usize(this.data.fetch_or(value and low_bits<T>().toInt(), ord))
    }

    /**
     * Bitwise "xor" with the current tag.
     *
     * Performs a bitwise "xor" operation on the current tag and the argument `val`, and sets the
     * new tag to the result. Returns the previous pointer.
     *
     * This method takes an [Ordering] argument which describes the memory ordering of this
     * operation.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import crossbeamepoch.Shared
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.from(null.withTag(1))
     * val guard = epoch.pin()
     * check(a.fetchXor(3, SeqCst, guard).tag() == 1)
     * check(a.load(SeqCst, guard).tag() == 2)
     * ```
     */
    public fun fetch_xor(value: Int, ord: Ordering, arg2: Guard): Shared<T> {
        return Shared.from_usize(this.data.fetch_xor(value and low_bits<T>().toInt(), ord))
    }

    /**
     * Takes ownership of the pointee.
     *
     * This consumes the atomic and converts it into [Owned]. As [Atomic] doesn't have a
     * destructor and doesn't drop the pointee while [Owned] does, this is suitable for
     * destructors of data structures.
     *
     * # Panics
     *
     * Panics if this pointer is null, but only in debug mode.
     *
     * # Safety
     *
     * This method may be called only if the pointer is valid and nobody else is holding a
     * reference to the same object.
     *
     * # Examples
     *
     * ```kotlin
     * open class DataStructure(public var ptr: Atomic<Int>) {
     *     public open fun drop() {
     *         // By now the DataStructure lives only in our thread and we are sure we don't hold
     *         // any Shared or & to it ourselves.
     *         run {
     *             drop(replace(this.ptr, null).intoOwned())
     *         }
     *     }
     * }
     * ```
     */
    public fun into_owned(): Owned<T> {
        return Owned.from_usize(this.data.into_inner())
    }

    /**
     * Takes ownership of the pointee if it is non-null.
     *
     * This consumes the atomic and converts it into [Owned]. As [Atomic] doesn't have a
     * destructor and doesn't drop the pointee while [Owned] does, this is suitable for
     * destructors of data structures.
     *
     * # Safety
     *
     * This method may be called only if the pointer is valid and nobody else is holding a
     * reference to the same object, or the pointer is null.
     *
     * # Examples
     *
     * ```kotlin
     * open class DataStructure(public var ptr: Atomic<Int>) {
     *     public open fun drop() {
     *         // By now the DataStructure lives only in our thread and we are sure we don't hold
     *         // any Shared or & to it ourselves, but it may be null, so we have to be careful.
     *         val old = replace(this.ptr, null)
     *         when (val matchVal = old.tryIntoOwned()) {
     *             null -> {}
     *             else -> {
     *                 val x = matchVal
     *                 drop(x)
     *             }
     *         }
     *     }
     * }
     * ```
     */
    public fun try_into_owned(): Owned<T>? {
        val data = this.data.into_inner()
        return if (decompose_tag<T>(data).first == 0) {
            null
        } else {
            Owned.from_usize(data)
        }
    }

    public fun fmt(f: StringBuilder) {
        val data = this.data.load(Ordering.SeqCst)
        val (raw, tag) = decompose_tag<T>(data)
        DebugStruct(f, "Atomic").field("raw", raw).field("tag", tag).finish()
    }

    public fun fmtPointer(f: StringBuilder) {
        val data = this.data.load(Ordering.SeqCst)
        val (raw, _) = decompose_tag<T>(data)
        fmt.Pointer.fmt((raw.toLong()), f)
    }

    /**
     * Returns a copy of the atomic value.
     *
     * Note that a `Relaxed` load is used here. If you need synchronization, use it with other
     * atomics or fences.
     */
    public fun clone(): Atomic<T> {
        val data = this.data.load(Ordering.Relaxed)
        return Atomic.from_usize(data)
    }

    public companion object {
        /**
         * Allocates `value` on the heap and returns a new atomic pointer pointing to it.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamepoch.Atomic
         *
         * val a = Atomic.new(1234)
         * ```
         */
        public fun <Init, T : Pointable<T>> new(init: T): Atomic<T> {
            return Atomic.init(init)
        }

        /**
         * Allocates `value` on the heap and returns a new atomic pointer pointing to it.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamepoch.Atomic
         *
         * val a = Atomic.init(1234)
         * ```
         */
        public fun <Init, T : Pointable<T>> init(init: Init): Atomic<T> {
            return Atomic.from(Owned.init(init))
        }

        /**
         * Returns a new atomic pointer pointing to the tagged pointer `data`.
         */
        public fun <Init, T : Pointable<T>> from_usize(data: Int): Atomic<T> {
            return Atomic(data = AtomicUsize.new(data), _marker = Unit)
        }

        /**
         * Returns a new null atomic pointer.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamepoch.Atomic
         *
         * val a = null
         * ```
         */
        public fun <Init, T : Pointable<T>> nullVal(): Atomic<T> {
            return Atomic(data = AtomicUsize.new(0), _marker = Unit)
        }

        /**
         * Returns a new atomic pointer pointing to `owned`.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamepoch.Atomic
         * import crossbeamepoch.Owned
         *
         * val a = Atomic.from(Owned.new(1234))
         * ```
         */
        public fun <Init, T : Pointable<T>> from(owned: Owned<T>): Atomic<T> {
            val data = owned.data
            mem.forget(owned)
            return Atomic.from_usize(data)
        }

        public fun <Init, T : Pointable<T>> from(b: T): Atomic<T> {
            return Atomic.from(Owned.from(b))
        }

        public fun <Init, T : Pointable<T>> fromT(t: T): Atomic<T> {
            return Atomic.new(t)
        }

        /**
         * Returns a new atomic pointer pointing to `ptr`.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamepoch.Atomic
         * import crossbeamepoch.Shared
         *
         * val a = Atomic.from(null)
         * ```
         */
        public fun <Init, T : Pointable<T>> from(ptr: Shared<T>): Atomic<T> {
            return Atomic.from_usize(ptr.data)
        }

        /**
         * Returns a new atomic pointer pointing to `raw`.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamepoch.Atomic
         *
         * val a = Atomic.from(null)
         * ```
         */
        public fun <Init, T : Pointable<T>> from(raw: Long): Atomic<T> {
            return Atomic.from_usize(raw.toInt())
        }
    }
}

/**
 * A trait for either `Owned` or `Shared` pointers.
 */
public interface Pointer<T : Pointable<T>, SelfType> {
    /**
     * Returns the machine representation of the pointer.
     */
    public fun into_usize(): ULong

    /**
     * Returns a new pointer pointing to the tagged pointer `data`.
     *
     * # Safety
     *
     * The given `data` should have been created by `Pointer.into_usize()`, and one `data` should
     * not be converted back by `Pointer.from_usize()` multiple times.
     */
    public fun from_usize(data: ULong): SelfType
}

/**
 * An owned heap-allocated object.
 *
 * This type is very similar to `Holder<T>`.
 *
 * The pointer must be properly aligned. Since it is aligned, a tag can be stored into the unused
 * least significant bits of the address.
 */
public class Owned<T : Pointable<T>>(public var data: ULong = 0uL, public var _marker: Unit = Unit) : Pointer<T>, From<T>, Borrow<T>, BorrowMut<T>, AsRef<T>, AsMut<T> {
    public override fun into_usize(): Int {
        val data = this.data
        mem.forget(this)
        return data
    }

    /**
     * Converts the owned pointer into a `Holder`.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Owned
     *
     * val o = Owned.new(1234)
     * val b: Int = o.intoBox()
     * check(b == 1234)
     * ```
     */
    public fun into_box(): T {
        val (raw, _) = decompose_tag<T>(this.data.toULong())
        mem.forget(this)
        return Box.from_raw(((raw as Any?) as T))
    }

    /**
     * Converts the owned pointer into a [Shared].
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Owned
     *
     * val o = Owned.new(1234)
     * val guard = epoch.pin()
     * val p = o.intoShared(guard)
     * ```
     */
    public fun into_shared(arg0: Guard): Shared<T> {
        return Shared.from_usize(this.into_usize())
    }

    /**
     * Returns the tag stored within the pointer.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Owned
     *
     * check(Owned.new(1234).tag() == 0)
     * ```
     */
    public fun tag(): Int {
        val (_, tag) = decompose_tag<T>(this.data.toULong())
        return tag
    }

    /**
     * Returns the same pointer, but tagged with `tag`. `tag` is truncated to be fit into the
     * unused bits of the pointer to `T`.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Owned
     *
     * val o = Owned.new(0uL)
     * check(o.tag() == 0)
     * val o1 = o.withTag(2)
     * check(o1.tag() == 2)
     * ```
     */
    public fun with_tag(tag: Int): Owned<T> {
        val data = this.into_usize()
        return Owned.from_usize(compose_tag<T>(data, tag.toULong()))
    }

    public fun drop(dropTFn: (Any?) -> Any?) {
        val (raw, _) = decompose_tag<T>(this.data.toULong())
        run {
            dropTFn(raw)
        }
    }

    public fun fmt(f: StringBuilder) {
        val (raw, tag) = decompose_tag<T>(this.data.toULong())
        DebugStruct(f, "Owned").field("raw", raw).field("tag", tag).finish()
    }

    public fun clone(): Owned<T> {
        return Owned.new(Owned(this.data, this._marker).clone()).with_tag(this.tag())
    }

    public fun deref(derefTFn: (Any?) -> Any?): T {
        val (raw, _) = decompose_tag<T>(this.data.toULong())
        return derefTFn(raw)
    }

    public fun deref_mut(deref_mutTFn: (Any?) -> Any?): T {
        val (raw, _) = decompose_tag<T>(this.data.toULong())
        return deref_mutTFn(raw)
    }

    public fun borrow(): T {
        return this.deref()
    }

    public fun borrow_mut(): T {
        return this.deref_mut()
    }

    public fun as_ref(): T {
        return this.deref()
    }

    public fun as_mut(): T {
        return this.deref_mut()
    }

    public companion object {
        /**
         * Returns a new pointer pointing to the tagged pointer `data`.
         *
         * # Panics
         *
         * Panics if the data is zero in debug mode.
         */
        public fun <Init, T : Pointable<T>> from_usize(data: Int): Owned<T> {
            check(data != 0) { "converting zero into `Owned`" }
            return Owned(data = data, _marker = Unit)
        }

        /**
         * Returns a new owned pointer pointing to `raw`.
         *
         * This function is unchecked because improper use may lead to memory problems. Argument `raw`
         * must be a valid pointer. Also, a double-free may occur if the function is called twice on
         * the same raw pointer.
         *
         * # Panics
         *
         * Panics if `raw` is not properly aligned.
         *
         * # Safety
         *
         * The given `raw` should have been derived from `Owned`, and one `raw` should not be converted
         * back by `Owned.from_raw()` multiple times.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamepoch.Owned
         *
         * val o = Owned.fromRaw(Holder.intoRaw(1234))
         * ```
         */
        public fun <Init, T : Pointable<T>> from_raw(raw: Long): Owned<T> {
            val raw1 = raw.toInt()
            ensure_aligned<T>(raw1.toULong())
            return Owned.from_usize(raw1)
        }

        /**
         * Allocates `value` on the heap and returns a new owned pointer pointing to it.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamepoch.Owned
         *
         * val o = Owned.new(1234)
         * ```
         */
        public fun <Init, T : Pointable<T>> new(init: T): Owned<T> {
            return Owned.init(init)
        }

        /**
         * Allocates `value` on the heap and returns a new owned pointer pointing to it.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamepoch.Owned
         *
         * val o = Owned.init(1234)
         * ```
         */
        public fun <Init, T : Pointable<T>> init(init: Init, initTFn: (Any?) -> Any?): Owned<T> {
            return Owned.from_usize(initTFn(init))
        }

        public fun <Init, T : Pointable<T>> from(t: T): Owned<T> {
            return Owned.new(t)
        }

        /**
         * Returns a new owned pointer pointing to `b`.
         *
         * # Panics
         *
         * Panics if the pointer (the `Holder`) is not properly aligned.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamepoch.Owned
         *
         * val o = Owned.fromRaw(Holder.intoRaw(1234))
         * ```
         */
        public fun <Init, T : Pointable<T>> fromBoxT(b: T): Owned<T> {
            return Owned.from_raw(Box.into_raw(b))
        }
    }
}

/**
 * A pointer to an object protected by the epoch GC.
 *
 * The pointer is valid for use only during the scope `g`.
 *
 * The pointer must be properly aligned. Since it is aligned, a tag can be stored into the unused
 * least significant bits of the address.
 */
public class Shared<T : Pointable<T>>(public var data: ULong = 0uL, public var _marker: Unit = Unit) : Pointer<T>, From<Long>, fmt.Pointer<Shared<T>> {
    public fun clone(): Shared<T> {
        return Shared(this.data, this._marker)
    }

    public override fun into_usize(): Int {
        return this.data
    }

    /**
     * Converts the pointer to a raw pointer (without the tag).
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import crossbeamepoch.Owned
     * import std.sync.atomic.ordering.SeqCst
     *
     * val o = Owned.new(1234)
     * val raw = ((o as Any?) as COpaquePointer?)
     * val a = Atomic.from(o)
     * val guard = epoch.pin()
     * val p = a.load(SeqCst, guard)
     * check(p.asRaw() == raw)
     * ```
     */
    public fun as_raw(): Long {
        val (raw, _) = decompose_tag<T>(this.data.toULong())
        return raw.toLong()
    }

    /**
     * Returns `true` if the pointer is null.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import crossbeamepoch.Owned
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = null
     * val guard = epoch.pin()
     * check((a.load(SeqCst, guard) == null))
     * a.store(Owned.new(1234), SeqCst)
     * check(!(a.load(SeqCst, guard) == null))
     * ```
     */
    public fun is_null(): Boolean {
        val (raw, _) = decompose_tag<T>(this.data.toULong())
        return raw == 0
    }

    /**
     * Dereferences the pointer.
     *
     * Returns a reference to the pointee that is valid during the scope `g`.
     *
     * # Safety
     *
     * Dereferencing a pointer is unchecked because it could be pointing to invalid memory.
     *
     * Another concern is the possibility of data races due to lack of proper synchronization.
     * For example, consider the following scenario:
     *
     * 1. A thread creates a new object: `a.store(Owned.new(10), Relaxed)`
     * 2. Another thread reads it: `* a.load(Relaxed, guard).as_ref().unwrap()`
     *
     * The problem is that relaxed orderings don't synchronize initialization of the object with
     * the read from the second thread. This is a data race. A possible solution would be to use
     * `Release` and `Acquire` orderings.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.new(1234)
     * val guard = epoch.pin()
     * val p = a.load(SeqCst, guard)
     * run {
     *     check(p.deref() == 1234)
     * }
     * ```
     */
    public fun deref(derefTFn: (Any?) -> Any?): T {
        val (raw, _) = decompose_tag<T>(this.data.toULong())
        return derefTFn(raw)
    }

    /**
     * Dereferences the pointer.
     *
     * Returns a mutable reference to the pointee that is valid during the scope `g`.
     *
     * # Safety
     *
     * * There is no guarantee that there are no more threads attempting to read/write from/to the
     *   actual object at the same time.
     *
     *   The user must know that there are no concurrent accesses towards the object itself.
     *
     * * Other than the above, all safety concerns of `deref()` applies here.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.new(mutableListOf(1, 2, 3, 4))
     * val guard = epoch.pin()
     * var p = a.load(SeqCst, guard)
     * run {
     *     check(!(p == null))
     *     val b = p.derefMut()
     *     check(b == mutableListOf(1, 2, 3, 4))
     *     b.push(5)
     *     check(b == mutableListOf(1, 2, 3, 4, 5))
     * }
     * val p1 = a.load(SeqCst, guard)
     * run {
     *     check(p1.deref() == mutableListOf(1, 2, 3, 4, 5))
     * }
     * ```
     */
    public fun deref_mut(deref_mutTFn: (Any?) -> Any?): T {
        val (raw, _) = decompose_tag<T>(this.data.toULong())
        return deref_mutTFn(raw)
    }

    /**
     * Converts the pointer to a reference.
     *
     * Returns `None` if the pointer is null, or else a reference to the object wrapped in `Some`.
     *
     * # Safety
     *
     * Dereferencing a pointer is unchecked because it could be pointing to invalid memory.
     *
     * Another concern is the possibility of data races due to lack of proper synchronization.
     * For example, consider the following scenario:
     *
     * 1. A thread creates a new object: `a.store(Owned.new(10), Relaxed)`
     * 2. Another thread reads it: `* a.load(Relaxed, guard).as_ref().unwrap()`
     *
     * The problem is that relaxed orderings don't synchronize initialization of the object with
     * the read from the second thread. This is a data race. A possible solution would be to use
     * `Release` and `Acquire` orderings.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.new(1234)
     * val guard = epoch.pin()
     * val p = a.load(SeqCst, guard)
     * run {
     *     check(p == 1234)
     * }
     * ```
     */
    public fun as_ref(derefTFn: (Any?) -> Any?): T? {
        val (raw, _) = decompose_tag<T>(this.data.toULong())
        return if (raw == 0) {
            null
        } else {
            derefTFn(raw)
        }
    }

    /**
     * Takes ownership of the pointee.
     *
     * # Panics
     *
     * Panics if this pointer is null, but only in debug mode.
     *
     * # Safety
     *
     * This method may be called only if the pointer is valid and nobody else is holding a
     * reference to the same object.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.new(1234)
     * run {
     *     val guard = epoch.unprotected()
     *     val p = a.load(SeqCst, guard)
     *     drop(p.intoOwned())
     * }
     * ```
     */
    public fun into_owned(): Owned<T> {
        check(!(this == 0L)) { "converting a null `Shared` into `Owned`" }
        return Owned.from_usize(this.data)
    }

    /**
     * Takes ownership of the pointee if it is not null.
     *
     * # Safety
     *
     * This method may be called only if the pointer is valid and nobody else is holding a
     * reference to the same object, or if the pointer is null.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.new(1234)
     * run {
     *     val guard = epoch.unprotected()
     *     val p = a.load(SeqCst, guard)
     *     when (val matchVal = p.tryIntoOwned()) {
     *         null -> {}
     *         else -> {
     *             val x = matchVal
     *             drop(x)
     *         }
     *     }
     * }
     * ```
     */
    public fun try_into_owned(): Owned<T>? {
        return if ((this == 0L)) {
            null
        } else {
            Owned.from_usize(this.data)
        }
    }

    /**
     * Returns the tag stored within the pointer.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import crossbeamepoch.Owned
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.from(Owned.new(0uL).withTag(2))
     * val guard = epoch.pin()
     * val p = a.load(SeqCst, guard)
     * check(p.tag() == 2)
     * ```
     */
    public fun tag(): Int {
        val (_, tag) = decompose_tag<T>(this.data.toULong())
        return tag
    }

    /**
     * Returns the same pointer, but tagged with `tag`. `tag` is truncated to be fit into the
     * unused bits of the pointer to `T`.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamepoch.Atomic
     * import std.sync.atomic.ordering.SeqCst
     *
     * val a = Atomic.new(0uL)
     * val guard = epoch.pin()
     * val p1 = a.load(SeqCst, guard)
     * val p2 = p1.withTag(2)
     * check(p1.tag() == 0)
     * check(p2.tag() == 2)
     * check(p1.asRaw() == p2.asRaw())
     * ```
     */
    public fun with_tag(tag: Int): Shared<T> {
        return Shared.from_usize(compose_tag<T>(this.data.toULong(), tag.toULong()))
    }

    public fun eq(other: Shared<T>): Boolean {
        return this.data == other.data
    }

    public fun partial_cmp(other: Shared<T>): cmp.Ordering? {
        return this.data.partial_cmp(other.data)
    }

    public fun cmp(other: Shared<T>): cmp.Ordering {
        return this.data.cmp(other.data)
    }

    public fun fmt(f: StringBuilder) {
        val (raw, tag) = decompose_tag<T>(this.data.toULong())
        DebugStruct(f, "Shared").field("raw", raw).field("tag", tag).finish()
    }

    public fun fmtPointer(f: StringBuilder) {
        val (raw, _) = decompose_tag<T>(this.data.toULong())
        fmt.Pointer.fmt((raw.toLong()), f)
    }

    public companion object {
        public fun <Init, T : Pointable<T>> from_usize(data: Int): Shared<T> {
            return Shared(data = data, _marker = Unit)
        }

        /**
         * Returns a new null pointer.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamepoch.Shared
         *
         * val p = null
         * check((p == null))
         * ```
         */
        public fun <Init, T : Pointable<T>> nullVal(): Shared<T> {
            return Shared(data = 0, _marker = Unit)
        }

        /**
         * Returns a new pointer pointing to `raw`.
         *
         * # Panics
         *
         * Panics if `raw` is not properly aligned.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamepoch.Shared
         *
         * val p = Shared.from(((Holder.intoRaw(1234) as Any?) as COpaquePointer?))
         * check(!(p == null))
         * ```
         */
        public fun <Init, T : Pointable<T>> from(raw: Long): Shared<T> {
            val raw1 = raw.toInt()
            ensure_aligned<T>(raw1.toULong())
            return Shared.from_usize(raw1)
        }
    }
}
