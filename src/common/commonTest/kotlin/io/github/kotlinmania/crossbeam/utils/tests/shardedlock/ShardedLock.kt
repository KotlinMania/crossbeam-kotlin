// port-lint: tests crossbeam-utils/tests/sharded_lock.rs
package io.github.kotlinmania.crossbeam.utils.tests.shardedlock

import io.github.kotlinmania.crossbeam.utils.sync.shardedlock.ShardedLock
import io.github.kotlinmania.crossbeam.utils.tests.subcrates.channel
import io.github.kotlinmania.rand.Rng

public data class NonCopy(public var value: Int = 0) {
    public var field0: Int
        get() = value
        set(v) {
            value = v
        }
}

@kotlin.test.Test
public fun smoke() {
    val l = ShardedLock.new()
    drop(l.unwrap())
    drop(l.write().unwrap())
    drop(Pair(l.unwrap(), l.unwrap()))
    drop(l.write().unwrap())
}

@kotlin.test.Test
public fun frob() {
    val N: UInt = 10u
    val M: Int = 1000
    val r = ShardedLock.new()
    val (tx, rx) = channel<Unit>()
    for (unused in 0 until N) {
        val tx1 = tx.clone()
        val r1 = r.clone()
        thread.spawn({
            var rng = thread_rng()
            for (unused1 in 0 until M) {
                if (rng.gen_bool(1.0 / (N.toDouble()))) {
                    drop(r1.write().unwrap())
                } else {
                    drop(r1.unwrap())
                }
            }
            drop(tx1)
        })
    }
    drop(tx)
    rx.recv()
}

@kotlin.test.Test
public fun arc_poison_wr() {
    val arc = ShardedLock.new(1)
    val arc2 = arc.clone()
    val unused: Result<Unit> = thread.spawn({
        val _lock = arc2.write().unwrap()
        error("panic")
    }).join()
    check(arc.is_err())
}

@kotlin.test.Test
public fun arc_poison_ww() {
    val arc = ShardedLock.new(1)
    check(!arc.is_poisoned())
    val arc2 = arc.clone()
    val unused: Result<Unit> = thread.spawn({
        val _lock = arc2.write().unwrap()
        error("panic")
    }).join()
    check(arc.write().is_err())
    check(arc.is_poisoned())
}

@kotlin.test.Test
public fun arc_no_poison_rr() {
    val arc = ShardedLock.new(1)
    val arc2 = arc.clone()
    val unused: Result<Unit> = thread.spawn({
        val _lock = arc2.unwrap()
        error("panic")
    }).join()
    val lock = arc.unwrap()
    check(lock == 1)
}

@kotlin.test.Test
public fun arc_no_poison_sl() {
    val arc = ShardedLock.new(1)
    val arc2 = arc.clone()
    val unused: Result<Unit> = thread.spawn({
        val _lock = arc2.unwrap()
        error("panic")
    }).join()
    val lock = arc.write().unwrap()
    check(lock == 1)
}

@kotlin.test.Test
public fun arc() {
    val arc = ShardedLock.new(0)
    val arc2 = arc.clone()
    val (tx, rx) = channel()
    thread.spawn({
        var lock = arc2.write().unwrap()
        for (unused in 0 until 10) {
            val tmp = lock
            lock = -1
            thread.yield_now()
            lock = tmp + 1
        }
        tx.send().unwrap()
    })
    // Readers try to catch the writer in the act
    var children = mutableListOf()
    for (unused in 0 until 5) {
        val arc3 = arc.clone()
        children.add(thread.spawn({
            val lock = arc3.unwrap()
            check(lock >= 0)
        }))
    }
    // Wait for children to pass their asserts
    for (r in children) {
        check(r.join().is_ok())
    }
    // Wait for writer to finish
    rx.recv().unwrap()
    val lock = arc.unwrap()
    check(lock == 10)
}

@kotlin.test.Test
public fun arc_access_in_unwind() {
    val arc = ShardedLock.new(1)
    val arc2 = arc.clone()
    thread.spawn({
        data class Unwinder(public var i: ShardedLock<Int> = ShardedLock<Int>())
        fun Unwinder.drop() {
            var lock = this.i.write().unwrap()
            lock += 1
        }
        val _u = Unwinder(i = arc2)
        error("panic")
    }).join()
    val lock = arc.unwrap()
    check(lock == 2)
}

@kotlin.test.Test
public fun unsized_type() {
    val sl: ShardedLock<List<Int>> = ShardedLock.new(listOf(1, 2, 3))
    run {
        val b = sl.write().unwrap()
        b[0] = 4
        b[2] = 5
    }
    val comp: List<Int> = listOf(4, 2, 5)
    check(sl.unwrap() == comp)
}

@kotlin.test.Test
public fun try_write() {
    val lock = ShardedLock.new(0L)
    val read_guard = lock.unwrap()
    val write_result = lock.try_write()
    when {
        write_result is Err && write_result.value == TryLockError.WouldBlock -> Unit
        write_result is Ok -> error("try_write should not succeed while read_guard is in scope")
        else -> error("unexpected error")
    }
    drop(read_guard)
}

@kotlin.test.Test
public fun test_into_inner() {
    val m = ShardedLock.new(NonCopy(10))
    check(m.into_inner().unwrap() == NonCopy(10))
}

@kotlin.test.Test
public fun test_into_inner_drop() {
    data class Foo(public var value: AtomicUsize = AtomicUsize()) {
        public var field0: AtomicUsize
            get() = value
            set(v) {
                value = v
            }

        public fun drop() {
            this.value.fetch_add(1, Ordering.SeqCst)
        }
    }
    val num_drops = AtomicUsize.new(0)
    val m = ShardedLock.new(Foo(num_drops.clone()))
    check(num_drops.load(Ordering.SeqCst) == 0)
    run {
        val _inner = m.into_inner().unwrap()
        check(num_drops.load(Ordering.SeqCst) == 0)
    }
    check(num_drops.load(Ordering.SeqCst) == 1)
}

@kotlin.test.Test
public fun test_into_inner_poison() {
    val m = ShardedLock.new(NonCopy(10))
    val m2 = m.clone()
    thread.spawn({
        val _lock = m2.write().unwrap()
        error("test panic in inner thread to poison ShardedLock")
    }).join()
    check(m.is_poisoned())
    run {
        val matchVal = Arc.try_unwrap(m).unwrap().into_inner()
        if (matchVal.isSuccess) {
            val x = matchVal.getOrThrow()
            error("into_inner of poisoned ShardedLock is Ok: $x")
        } else {
            val e = matchVal.exceptionOrNull()!!
            check(e.into_inner() == NonCopy(10))
        }
    }
}

@kotlin.test.Test
public fun test_get_mut() {
    var m = ShardedLock.new(NonCopy(10))
    m.get_mut()!! = NonCopy(20)
    check(m.into_inner().unwrap() == NonCopy(20))
}

@kotlin.test.Test
public fun test_get_mut_poison() {
    val m = ShardedLock.new(NonCopy(10))
    val m2 = m.clone()
    thread.spawn({
        val _lock = m2.write().unwrap()
        error("test panic in inner thread to poison ShardedLock")
    }).join()
    check(m.is_poisoned())
    run {
        val matchVal = Arc.try_unwrap(m).unwrap().get_mut()
        if (matchVal.isSuccess) {
            val x = matchVal.getOrThrow()
            error("get_mut of poisoned ShardedLock is Ok: $x")
        } else {
            val e = matchVal.exceptionOrNull()!!
            check(e.into_inner() == NonCopy(10))
        }
    }
}

@kotlin.test.Test
public fun forget_guard() {
    run {
        val lock = ShardedLock.new(mutableListOf(1, 2, 3))
        val guard = lock.write().unwrap()
        mem.forget(guard)
    }
    run {
        val lock1 = ShardedLock.new(mutableListOf(1, 2, 3))
        val guard1 = lock1.unwrap()
        mem.forget(guard1)
    }
}
