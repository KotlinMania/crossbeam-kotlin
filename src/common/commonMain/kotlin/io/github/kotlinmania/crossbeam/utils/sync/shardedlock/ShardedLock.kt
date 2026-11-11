// port-lint: source crossbeam-utils/src/sync/sharded_lock.rs
package io.github.kotlinmania.crossbeam.utils.sync.shardedlock

import io.github.kotlinmania.crossbeam.utils.crossbeamchannel.src.utils.Mutex
import io.github.kotlinmania.crossbeam.utils.cachepadded.CachePadded
import io.github.kotlinmania.crossbeam.utils.sync.oncelock.OnceLock

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
 * The number of shards per sharded lock. Must be a power of two.
 */
public val NUM_SHARDS: ULong = 8uL

/**
 * A shard containing a single reader-writer lock.
 */
public data class Shard(
    /**
     * The inner reader-writer lock.
     */
    public var lock: RwLock<Unit> = RwLock<Unit>(),
    /**
     * The write-guard keeping this shard locked.
     *
     * Write operations will lock each shard and store the guard here. These guards get dropped at
     * the same time the big guard is dropped.
     */
    public var write_guard: UnsafeCell<RwLockWriteGuard<Unit>> = UnsafeCell<RwLockWriteGuard<Unit>>(),
)

/**
 * A sharded reader-writer lock.
 *
 * This lock is equivalent to [RwLock], except read operations are faster and write operations
 * are slower.
 *
 * A `ShardedLock` is internally made of a list of *shards*, each being a [RwLock] occupying a
 * single cache line. Read operations will pick one of the shards depending on the current thread
 * and lock it. Write operations need to lock all shards in succession.
 *
 * By splitting the lock into shards, concurrent read operations will in most cases choose
 * different shards and thus update different cache lines, which is good for scalability. However,
 * write operations need to do more work and are therefore slower than usual.
 *
 * The priority policy of the lock is dependent on the underlying operating system's
 * implementation, and this type does not guarantee that any particular policy will be used.
 *
 * # Poisoning
 *
 * A `ShardedLock`, like [RwLock], will become poisoned on a panic. Note that it may only be
 * poisoned if a panic occurs while a write operation is in progress. If a panic occurs in any
 * read operation, the lock will not be poisoned.
 *
 * # Examples
 *
 * ```kotlin
 * import crossbeamutils.sync.ShardedLock
 *
 * val lock = ShardedLock.new(5)
 * // Any number of read locks can be held at once.
 * run {
 *     val r1 = lock.unwrap()
 *     val r2 = lock.unwrap()
 *     check(r1 == 5)
 *     check(r2 == 5)
 * }
 * // Read locks are dropped at this point.
 * // However, only one write lock may be held.
 * run {
 *     var w = lock.write().unwrap()
 *     w += 1
 *     check(w == 6)
 * }
 * // Write lock is dropped here.
 * ```
 */
public class ShardedLock<T>(
    /**
     * A list of locks protecting the internal data.
     */
    public var shards: List<CachePadded<Shard>> = emptyList(),
    /**
     * The internal data.
     */
    public var value: UnsafeCell<T> = UnsafeCell<T>(),
) : From<T> {
    /**
     * Consumes this lock, returning the underlying data.
     *
     * # Errors
     *
     * This method will return an error if the lock is poisoned. A lock gets poisoned when a write
     * operation panics.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.sync.ShardedLock
     *
     * val lock = ShardedLock.new(String.new())
     * run {
     *     var s = lock.write().unwrap()
     *     s = "modified".toOwned()
     * }
     * check(lock.intoInner().unwrap() == "modified")
     * ```
     */
    public fun into_inner(): LockResult<T> {
        val is_poisoned = this.is_poisoned()
        val inner = this.value.into_inner()
        return if (is_poisoned) {
            Result.failure(PoisonError.new(inner))
        } else {
            Result.success(inner)
        }
    }

    /**
     * Returns `true` if the lock is poisoned.
     *
     * If another thread can still access the lock, it may become poisoned at any time. A `false`
     * result should not be trusted without additional synchronization.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.sync.ShardedLock
     *
     * val lock = ShardedLock.new(0)
     * val cLock = lock.clone()
     * thread.spawn({
     *     val lock = cLock.write().unwrap()
     *     error("panic")
     * }).join()
     * check(lock.isPoisoned() == true)
     * ```
     */
    public fun is_poisoned(): Boolean {
        return this.shards[0].lock.is_poisoned()
    }

    /**
     * Returns a mutable reference to the underlying data.
     *
     * Since this call borrows the lock mutably, no actual locking needs to take place.
     *
     * # Errors
     *
     * This method will return an error if the lock is poisoned. A lock gets poisoned when a write
     * operation panics.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.sync.ShardedLock
     *
     * var lock = ShardedLock.new(0)
     * lock.getMut()!! = 10
     * check(lock.unwrap() == 10)
     * ```
     */
    public fun get_mut(): LockResult<T> {
        val is_poisoned = this.is_poisoned()
        val inner = this.value.get()
        return if (is_poisoned) {
            Result.failure(PoisonError.new(inner))
        } else {
            Result.success(inner)
        }
    }

    /**
     * Attempts to acquire this lock with shared read access.
     *
     * If the access could not be granted at this time, an error is returned. Otherwise, a guard
     * is returned which will release the shared access when it is dropped. This method does not
     * provide any guarantees with respect to the ordering of whether contentious readers or
     * writers will acquire the lock first.
     *
     * # Errors
     *
     * This method will return an error if the lock is poisoned. A lock gets poisoned when a write
     * operation panics.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.sync.ShardedLock
     *
     * val lock = ShardedLock.new(1)
     * run {
     *     val matchVal = lock.tryRead()
     *     if (matchVal.isSuccess) {
     *         val n = matchVal.getOrThrow()
     *         check(n == 1)
     *     } else {
     *         error("unreachable")
     *     }
     * }
     * ```
     */
    public fun try_read(): TryLockResult<ShardedLockReadGuard<T>> {
        // Take the current thread index and map it to a shard index. Thread indices will tend to
        // distribute shards among threads equally, thus reducing contention due to read-locking.
        val current_index = current_index().unwrap_or(0)
        val shard_index = current_index and (this.shards.len() - 1)
        return run {
            val matchVal = this.shards[shard_index].lock.try_read()
            when {
                matchVal is Ok -> {
                    val (guard) = matchVal
                    Result.success(ShardedLockReadGuard(lock = this, _guard = guard, _marker = Unit))
                }
                matchVal is Err && matchVal.value is TryLockError.Poisoned -> {
                    val (err) = matchVal.value as TryLockError.Poisoned
                    val guard1 = ShardedLockReadGuard(lock = this, _guard = err.into_inner(), _marker = Unit)
                    Result.failure(TryLockError.Poisoned(PoisonError.new(guard1)))
                }
                else -> Result.failure(TryLockError.WouldBlock)
            }
        }
    }

    /**
     * Locks with shared read access, blocking the current thread until it can be acquired.
     *
     * The calling thread will be blocked until there are no more writers which hold the lock.
     * There may be other readers currently inside the lock when this method returns. This method
     * does not provide any guarantees with respect to the ordering of whether contentious readers
     * or writers will acquire the lock first.
     *
     * Returns a guard which will release the shared access when dropped.
     *
     * # Errors
     *
     * This method will return an error if the lock is poisoned. A lock gets poisoned when a write
     * operation panics.
     *
     * # Panics
     *
     * This method might panic when called if the lock is already held by the current thread.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.sync.ShardedLock
     *
     * val lock = ShardedLock.new(1)
     * val cLock = lock.clone()
     * val n = lock.unwrap()
     * check(n == 1)
     * thread.spawn({
     *     val r = cLock
     *     check(r.isOk())
     * }).join().unwrap()
     * ```
     */
    public fun read(): LockResult<ShardedLockReadGuard<T>> {
        // Take the current thread index and map it to a shard index. Thread indices will tend to
        // distribute shards among threads equally, thus reducing contention due to read-locking.
        val current_index = current_index().unwrap_or(0)
        val shard_index = current_index and (this.shards.len() - 1)
        return run {
            val matchVal = this.shards[shard_index].lock
            if (matchVal.isSuccess) {
                val guard = matchVal.getOrThrow()
                Result.success(ShardedLockReadGuard(lock = this, _guard = guard, _marker = Unit))
            } else {
                val err = matchVal.exceptionOrNull()!!
                Result.failure(PoisonError.new(ShardedLockReadGuard(lock = this, _guard = err.into_inner(), _marker = Unit)))
            }
        }
    }

    /**
     * Attempts to acquire this lock with exclusive write access.
     *
     * If the access could not be granted at this time, an error is returned. Otherwise, a guard
     * is returned which will release the exclusive access when it is dropped. This method does
     * not provide any guarantees with respect to the ordering of whether contentious readers or
     * writers will acquire the lock first.
     *
     * # Errors
     *
     * This method will return an error if the lock is poisoned. A lock gets poisoned when a write
     * operation panics.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.sync.ShardedLock
     *
     * val lock = ShardedLock.new(1)
     * val n = lock.unwrap()
     * check(n == 1)
     * check(lock.tryWrite().isErr())
     * ```
     */
    public fun try_write(): TryLockResult<ShardedLockWriteGuard<T>> {
        var poisoned = false
        var blocked = null
        // Write-lock each shard in succession.
        for ((i, shard) in this.shards.enumerate()) {
            val guard1 = run {
                val matchVal = shard.lock.try_write()
                when {
                    matchVal is Ok -> {
                        val (guard) = matchVal
                        guard
                    }
                    matchVal is Err && matchVal.value is TryLockError.Poisoned -> {
                        val (err) = matchVal.value as TryLockError.Poisoned
                        poisoned = true
                        err.into_inner()
                    }
                    else -> {
                        blocked = i
                        break
                    }
                }
            }
            // Store the guard into the shard.
            run {
                val guard2: RwLockWriteGuard<Unit> = mem.transmute(guard1)
                val dest: Long = shard.write_guard.get()
                dest = MaybeUninit.new(guard2)
            }
        }
        return if (blocked != null) {
            val i1 = blocked
            // Unlock the shards in reverse order of locking.
            for (shard1 in ((this.shards).slice(0 until i1)).iter().rev()) {
                val dest1: Long = shard1.write_guard.get()
                // SAFETY: we've acquired lock and filled write_guard of self.shards[0..i].
                // Note that drop_in_place cannot be used because it releases the lock during the drop.
                val guard3 = dest1!!
                drop(guard3)
            }
            Result.failure(TryLockError.WouldBlock)
        } else if (poisoned) {
            val guard4 = ShardedLockWriteGuard(lock = this, _marker = Unit)
            Result.failure(TryLockError.Poisoned(PoisonError.new(guard4)))
        } else {
            Result.success(ShardedLockWriteGuard(lock = this, _marker = Unit))
        }
    }

    /**
     * Locks with exclusive write access, blocking the current thread until it can be acquired.
     *
     * The calling thread will be blocked until there are no more writers which hold the lock.
     * There may be other readers currently inside the lock when this method returns. This method
     * does not provide any guarantees with respect to the ordering of whether contentious readers
     * or writers will acquire the lock first.
     *
     * Returns a guard which will release the exclusive access when dropped.
     *
     * # Errors
     *
     * This method will return an error if the lock is poisoned. A lock gets poisoned when a write
     * operation panics.
     *
     * # Panics
     *
     * This method might panic when called if the lock is already held by the current thread.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.sync.ShardedLock
     *
     * val lock = ShardedLock.new(1)
     * var n = lock.write().unwrap()
     * n = 2
     * check(lock.tryRead().isErr())
     * ```
     */
    public fun write(): LockResult<ShardedLockWriteGuard<T>> {
        var poisoned = false
        // Write-lock each shard in succession.
        for (shard in this.shards.iter()) {
            val guard1 = run {
                val matchVal = shard.lock.write()
                if (matchVal.isSuccess) {
                    val guard = matchVal.getOrThrow()
                    guard
                } else {
                    val err = matchVal.exceptionOrNull()!!
                    poisoned = true
                    err.into_inner()
                }
            }
            // Store the guard into the shard.
            run {
                val guard2: RwLockWriteGuard<Unit> = guard1
                val guard3: RwLockWriteGuard<Unit> = mem.transmute(guard2)
                val dest: Long = shard.write_guard.get()
                dest = MaybeUninit.new(guard3)
            }
        }
        return if (poisoned) {
            Result.failure(PoisonError.new(ShardedLockWriteGuard(lock = this, _marker = Unit)))
        } else {
            Result.success(ShardedLockWriteGuard(lock = this, _marker = Unit))
        }
    }

    public fun fmt(f: StringBuilder) {
        run {
            val matchVal = this.try_read()
            when {
                matchVal is Ok -> {
                    val (guard) = matchVal
                    DebugStruct(f, "ShardedLock").field("data", guard).finish()
                }
                matchVal is Err && matchVal.value is TryLockError.Poisoned -> {
                    val (err) = matchVal.value as TryLockError.Poisoned
                    DebugStruct(f, "ShardedLock").field("data", err.get_ref()).finish()
                }
                else -> {
                    class LockedPlaceholder {
                        public fun fmt(f1: StringBuilder) {
                            f1.append("<locked>")
                        }
                    }
                    DebugStruct(f, "ShardedLock").field("data", LockedPlaceholder()).finish()
                }
            }
        }
    }

    public companion object {
        /**
         * Creates a new sharded reader-writer lock.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamutils.sync.ShardedLock
         *
         * val lock = ShardedLock.new(5)
         * ```
         */
        public fun <T> new(value: T): ShardedLock<T> {
            return ShardedLock(shards = (0 until NUM_SHARDS).map({
                CachePadded.new(Shard(lock = RwLock.new(), write_guard = UnsafeCell.new(0L)))
            }).collect<List<Any?>>(), value = UnsafeCell.new(value))
        }

        public fun <T> from(t: T): ShardedLock<T> {
            return ShardedLock.new(t)
        }
    }
}

/**
 * A guard used to release the shared read access of a [ShardedLock] when dropped.
 */
public class ShardedLockReadGuard<T>(
    public var lock: ShardedLock<T> = ShardedLock<T>(),
    public var _guard: RwLockReadGuard<Unit> = RwLockReadGuard<Unit>(),
    public var _marker: Unit = Unit,
) {
    public fun deref(): T {
        return this.lock.value.get()
    }

    public fun fmt(f: StringBuilder) {
        DebugStruct(f, "ShardedLockReadGuard").field("lock", this.lock).finish()
    }

    public fun fmtDisplay(f: StringBuilder) {
        this.fmt(f)
    }
}

/**
 * A guard used to release the exclusive write access of a [ShardedLock] when dropped.
 */
public class ShardedLockWriteGuard<T>(public var lock: ShardedLock<T> = ShardedLock<T>(), public var _marker: Unit = Unit) {
    public fun drop() {
        // Unlock the shards in reverse order of locking.
        for (shard in this.lock.shards.iter().rev()) {
            val dest: Long = shard.write_guard.get()
            // SAFETY: ShardedLockWriteGuard is constructed only when all locks have been acquired.
            // Note that drop_in_place cannot be used because it releases the lock during the drop.
            val guard = dest!!
            drop(guard)
        }
    }

    public fun fmt(f: StringBuilder) {
        DebugStruct(f, "ShardedLockWriteGuard").field("lock", this.lock).finish()
    }

    public fun fmtDisplay(f: StringBuilder) {
        this.fmt(f)
    }

    public fun deref(): T {
        return this.lock.value.get()
    }

    public fun deref_mut(): T {
        return this.lock.value.get()
    }
}

/**
 * Returns a `Int` that identifies the current thread.
 *
 * Each thread is associated with an 'index'. While there are no particular guarantees, indices
 * usually tend to be consecutive numbers between 0 and the number of running threads.
 *
 * Since this function accesses TLS, `None` might be returned if the current thread's TLS is
 * tearing down.
 */
public fun current_index(): ULong? {
    return REGISTRATION.try_with({ reg -> reg.index }).ok()
}

/**
 * The global registry keeping track of registered threads and indices.
 */
public data class ThreadIndices(
    /**
     * Mapping from `ThreadId` to thread index.
     */
    public var mapping: MutableMap<Any, ULong> = mutableMapOf(),
    /**
     * A list of free indices.
     */
    public var free_list: MutableList<ULong> = mutableListOf(),
    /**
     * The next index to allocate if the free list is empty.
     */
    public var next_index: ULong = 0uL,
)

private val THREAD_INDICES: OnceLock<Mutex<ThreadIndices>> = OnceLock.new()

public fun thread_indices(): Mutex<ThreadIndices> {
    fun init(): Mutex<ThreadIndices> {
        return Mutex.new(ThreadIndices(mapping = mutableMapOf(), free_list = mutableListOf(), next_index = 0uL))
    }
    return THREAD_INDICES.get_or_init(::init)
}

/**
 * A registration of a thread with an index.
 *
 * When dropped, unregisters the thread and frees the reserved index.
 */
public data class Registration(public var index: ULong = 0uL, public var thread_id: Any = Unit) {
    public fun drop() {
        var indices = thread_indices().lock().unwrap()
        indices.mapping.remove(this.thread_id)
        indices.free_list.add(this.index)
    }
}
