// port-lint: tests crossbeam-deque/tests/injector.rs
@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.kotlinmania.crossbeam.deque.tests.injector

import io.github.kotlinmania.crossbeam.deque.Injector
import io.github.kotlinmania.crossbeam.deque.Worker
import io.github.kotlinmania.crossbeam.deque.crossbeamchannel.src.utils.Mutex
import io.github.kotlinmania.crossbeam.deque.crossbeamutils.tests.thread.THREADS
import io.github.kotlinmania.crossbeam.deque.steal.Empty
import io.github.kotlinmania.crossbeam.deque.steal.Success
import io.github.kotlinmania.crossbeam.utils.thread.scope
import io.github.kotlinmania.rand.Rng

@kotlin.test.Test
public fun smoke() {
    val q = Injector.new()
    check(q.steal() == Empty)
    q.push(1)
    q.push(2)
    check(q.steal() == Success(1))
    check(q.steal() == Success(2))
    check(q.steal() == Empty)
    q.push(3)
    check(q.steal() == Success(3))
    check(q.steal() == Empty)
}

@kotlin.test.Test
public fun is_empty() {
    val q = Injector.new()
    check(q.is_empty())
    q.push(1)
    check(!q.is_empty())
    q.push(2)
    check(!q.is_empty())
    q.steal()
    check(!q.is_empty())
    q.steal()
    check(q.is_empty())
    q.push(3)
    check(!q.is_empty())
    q.steal()
    check(q.is_empty())
}

@kotlin.test.Test
public fun spsc() {
    val COUNT: Int = 100000
    val q = Injector.new()
    scope({ scope ->
        scope.spawn({
            for (i in 0 until COUNT) {
                while (true) {
                    when (val matchVal = q.steal()) {
                        is Success -> {
                            val (v) = matchVal
                            check(i == v)
                            break
                        }
                        else -> {}
                    }
                    spin_loop()
                }
            }
            check(q.steal() == Empty)
        })
        for (i in 0 until COUNT) {
            q.push(i)
        }
    }).unwrap()
}

@kotlin.test.Test
public fun mpmc() {
    val COUNT: Int = 25000
    val THREADS: Int = 4
    val q = Injector.new()
    val v = (0 until COUNT).map({ AtomicUsize.new(0) }).collect<Vec>()
    scope({ scope ->
        for (unused in 0 until THREADS) {
            scope.spawn({
                for (i in 0 until COUNT) {
                    q.push(i)
                }
            })
        }
        for (unused1 in 0 until THREADS) {
            scope.spawn({
                for (unused2 in 0 until COUNT) {
                    while (true) {
                        when (val matchVal = q.steal()) {
                            is Success -> {
                                val (n) = matchVal
                                v[n].fetch_add(1, SeqCst)
                                break
                            }
                            else -> {}
                        }
                        spin_loop()
                    }
                }
            })
        }
    }).unwrap()
    for (c in v) {
        check(c.load(SeqCst) == THREADS)
    }
}

@kotlin.test.Test
public fun stampede() {
    val THREADS: Int = 8
    val COUNT: Int = 50000
    val q = Injector.new()
    for (i in 0 until COUNT) {
        q.push(i + 1)
    }
    val remaining = AtomicUsize.new(COUNT)
    scope({ scope ->
        for (unused in 0 until THREADS) {
            val remaining1 = remaining.clone()
            val q1 = q
            scope.spawn({
                var last = 0
                while (remaining1.load(SeqCst) > 0) {
                    when (val matchVal = q1.steal()) {
                        is Success -> {
                            val (x) = matchVal
                            check(last < x)
                            last = x
                            remaining1.fetch_sub(1, SeqCst)
                        }
                        else -> {}
                    }
                }
            })
        }
        var last = 0
        while (remaining.load(SeqCst) > 0) {
            when (val matchVal = q.steal()) {
                is Success -> {
                    val (x) = matchVal
                    check(last < x)
                    last = x
                    remaining.fetch_sub(1, SeqCst)
                }
                else -> {}
            }
        }
    }).unwrap()
}

@kotlin.test.Test
public fun stress() {
    val THREADS: Int = 8
    val COUNT: Int = 50000
    val q = Injector.new()
    val done = AtomicBool.new(false)
    val hits = AtomicUsize.new(0)
    scope({ scope ->
        for (unused in 0 until THREADS) {
            val done1 = done.clone()
            val hits1 = hits.clone()
            val q1 = q
            scope.spawn({
                val w2 = Worker.new_fifo()
                while (!done1.load(SeqCst)) {
                    when (val matchVal = q1.steal()) {
                        is Success -> {
                            hits1.fetch_add(1, SeqCst)
                        }
                        else -> {}
                    }
                    q1.steal_batch(w2)
                    when (val matchVal1 = q1.steal_batch_and_pop(w2)) {
                        is Success -> {
                            hits1.fetch_add(1, SeqCst)
                        }
                        else -> {}
                    }
                    while (w2.pop() != null) {
                        hits1.fetch_add(1, SeqCst)
                    }
                }
            })
        }
        var rng = thread_rng()
        var expected = 0
        while (expected < COUNT) {
            if (rng.gen_range(0 until 3) == 0) {
                while (true) {
                    val matchVal = q.steal()
                    if (matchVal !is Success) break
                    hits.fetch_add(1, SeqCst)
                }
            } else {
                q.push(expected)
                expected += 1
            }
        }
        while (hits.load(SeqCst) < COUNT) {
            while (true) {
                val matchVal1 = q.steal()
                if (matchVal1 !is Success) break
                hits.fetch_add(1, SeqCst)
            }
        }
        done.store(true, SeqCst)
    }).unwrap()
}

// Miri is too slow
@kotlin.test.Test
public fun no_starvation() {
    val THREADS: Int = 8
    val COUNT: Int = 50000
    val q = Injector.new()
    val done = AtomicBool.new(false)
    var all_hits = mutableListOf()
    scope({ scope ->
        for (unused in 0 until THREADS) {
            val done1 = done.clone()
            val hits = AtomicUsize.new(0)
            all_hits.add(hits.clone())
            val q1 = q
            scope.spawn({
                val w2 = Worker.new_fifo()
                while (!done1.load(SeqCst)) {
                    when (val matchVal = q1.steal()) {
                        is Success -> {
                            hits.fetch_add(1, SeqCst)
                        }
                        else -> {}
                    }
                    q1.steal_batch(w2)
                    when (val matchVal1 = q1.steal_batch_and_pop(w2)) {
                        is Success -> {
                            hits.fetch_add(1, SeqCst)
                        }
                        else -> {}
                    }
                    while (w2.pop() != null) {
                        hits.fetch_add(1, SeqCst)
                    }
                }
            })
        }
        var rng = thread_rng()
        var my_hits = 0
        while (true) {
            for (i in 0 until rng.gen_range(0 until COUNT)) {
                if (rng.gen_range(0 until 3) == 0 && my_hits == 0) {
                    while (true) {
                        val matchVal = q.steal()
                        if (matchVal !is Success) break
                        my_hits += 1
                    }
                } else {
                    q.push(i)
                }
            }
            if (my_hits > 0 && ArrayDeque(all_hits).all({ h -> h.load(SeqCst) > 0 })) {
                break
            }
        }
        done.store(true, SeqCst)
    }).unwrap()
}

@kotlin.test.Test
public fun destructors() {
    val THREADS: Int = 8
    val COUNT: Int = 50000
    val STEPS: Int = 1000
    data class Elem(public var field0: Int = 0, public var field1: Mutex<List<Int>> = Mutex<List<Int>>()) {
        public fun drop() {
            this.field1.lock().unwrap().push(this.value)
        }
    }
    val q = Injector.new()
    val dropped = Mutex.new(mutableListOf())
    val remaining = AtomicUsize.new(COUNT)
    for (i in 0 until COUNT) {
        q.push(Elem(i, dropped.clone()))
    }
    scope({ scope ->
        for (unused in 0 until THREADS) {
            val remaining1 = remaining.clone()
            val q1 = q
            scope.spawn({
                val w2 = Worker.new_fifo()
                var cnt = 0
                while (cnt < STEPS) {
                    when (val matchVal = q1.steal()) {
                        is Success -> {
                            cnt += 1
                            remaining1.fetch_sub(1, SeqCst)
                        }
                        else -> {}
                    }
                    q1.steal_batch(w2)
                    when (val matchVal1 = q1.steal_batch_and_pop(w2)) {
                        is Success -> {
                            cnt += 1
                            remaining1.fetch_sub(1, SeqCst)
                        }
                        else -> {}
                    }
                    while (w2.pop() != null) {
                        cnt += 1
                        remaining1.fetch_sub(1, SeqCst)
                    }
                }
            })
        }
        for (unused1 in 0 until STEPS) {
            when (val matchVal = q.steal()) {
                is Success -> {
                    remaining.fetch_sub(1, SeqCst)
                }
                else -> {}
            }
        }
    }).unwrap()
    val rem = remaining.load(SeqCst)
    check(rem > 0)
    run {
        var v = dropped.lock().unwrap()
        check(v.len() == COUNT - rem)
        v.clear()
    }
    drop(q)
    run {
        var v1 = dropped.lock().unwrap()
        check(v1.len() == rem)
        v1.sort_unstable()
        for (pair in v1.windows(2)) {
            check(pair[0] + 1 == pair[1])
        }
    }
}

// If `Block` is created on the stack, the array of slots will multiply this `BigStruct` and
// probably overflow the thread stack. It's now directly created on the heap to avoid this.
@kotlin.test.Test
public fun stack_overflow() {
    val N: Int = 32768
    data class BigStruct(public var _data: UByteArray = UByteArray((N).toInt())) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BigStruct) return false
            if (!this._data.contentEquals(other._data)) return false
            return true
        }

        override fun hashCode(): Int = this._data.contentHashCode()
    }
    val q = Injector.new()
    q.push(BigStruct(_data = UByteArray((N).toInt()) { 0u.toUByte() }))
    while (!when (q.steal()) {
        Empty -> true
        else -> false
    }) {}
}
