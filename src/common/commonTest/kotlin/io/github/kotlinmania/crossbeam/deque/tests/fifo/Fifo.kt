// port-lint: tests crossbeam-deque/tests/fifo.rs
package io.github.kotlinmania.crossbeam.deque.tests.fifo

import io.github.kotlinmania.crossbeam.deque.Worker
import io.github.kotlinmania.crossbeam.deque.crossbeamchannel.src.utils.Mutex
import io.github.kotlinmania.crossbeam.deque.crossbeamutils.tests.thread.THREADS
import io.github.kotlinmania.crossbeam.deque.steal.Empty
import io.github.kotlinmania.crossbeam.deque.steal.Success
import io.github.kotlinmania.crossbeam.utils.thread.scope
import io.github.kotlinmania.rand.Rng

@kotlin.test.Test
public fun smoke() {
    val w = Worker.new_fifo()
    val s = w.stealer()
    check(w.pop() == null)
    check(s.steal() == Empty)
    w.push(1)
    check(w.pop() == 1)
    check(w.pop() == null)
    check(s.steal() == Empty)
    w.push(2)
    check(s.steal() == Success(2))
    check(s.steal() == Empty)
    check(w.pop() == null)
    w.push(3)
    w.push(4)
    w.push(5)
    check(s.steal() == Success(3))
    check(s.steal() == Success(4))
    check(s.steal() == Success(5))
    check(s.steal() == Empty)
    w.push(6)
    w.push(7)
    w.push(8)
    w.push(9)
    check(w.pop() == 6)
    check(s.steal() == Success(7))
    check(w.pop() == 8)
    check(w.pop() == 9)
    check(w.pop() == null)
}

@kotlin.test.Test
public fun is_empty() {
    val w = Worker.new_fifo()
    val s = w.stealer()
    check(w.is_empty())
    w.push(1)
    check(!w.is_empty())
    w.push(2)
    check(!w.is_empty())
    w.pop()
    check(!w.is_empty())
    w.pop()
    check(w.is_empty())
    check(s.is_empty())
    w.push(1)
    check(!s.is_empty())
    w.push(2)
    check(!s.is_empty())
    s.steal()
    check(!s.is_empty())
    s.steal()
    check(s.is_empty())
}

@kotlin.test.Test
public fun spsc() {
    val STEPS: Int = 50000
    val w = Worker.new_fifo()
    val s = w.stealer()
    scope({ scope ->
        scope.spawn({
            for (i in 0 until STEPS) {
                while (true) {
                    when (val matchVal = s.steal()) {
                        is Success -> {
                            val (v) = matchVal
                            check(i == v)
                            break
                        }
                        else -> {}
                    }
                }
            }
            check(s.steal() == Empty)
        })
        for (i in 0 until STEPS) {
            w.push(i)
        }
    }).unwrap()
}

@kotlin.test.Test
public fun stampede() {
    val THREADS: Int = 8
    val COUNT: Int = 50000
    val w = Worker.new_fifo()
    for (i in 0 until COUNT) {
        w.push(i + 1)
    }
    val remaining = AtomicUsize.new(COUNT)
    scope({ scope ->
        for (unused in 0 until THREADS) {
            val s = w.stealer()
            val remaining1 = remaining.clone()
            scope.spawn({
                var last = 0
                while (remaining1.load(SeqCst) > 0) {
                    when (val matchVal = s.steal()) {
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
            when (val matchVal = w.pop()) {
                null -> {}
                else -> {
                    val x = matchVal
                    check(last < x)
                    last = x
                    remaining.fetch_sub(1, SeqCst)
                }
            }
        }
    }).unwrap()
}

@kotlin.test.Test
public fun stress() {
    val THREADS: Int = 8
    val COUNT: Int = 50000
    val w = Worker.new_fifo()
    val done = AtomicBool.new(false)
    val hits = AtomicUsize.new(0)
    scope({ scope ->
        for (unused in 0 until THREADS) {
            val s = w.stealer()
            val done1 = done.clone()
            val hits1 = hits.clone()
            scope.spawn({
                val w2 = Worker.new_fifo()
                while (!done1.load(SeqCst)) {
                    when (val matchVal = s.steal()) {
                        is Success -> {
                            hits1.fetch_add(1, SeqCst)
                        }
                        else -> {}
                    }
                    s.steal_batch(w2)
                    when (val matchVal1 = s.steal_batch_and_pop(w2)) {
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
                while (w.pop() != null) {
                    hits.fetch_add(1, SeqCst)
                }
            } else {
                w.push(expected)
                expected += 1
            }
        }
        while (hits.load(SeqCst) < COUNT) {
            while (w.pop() != null) {
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
    val w = Worker.new_fifo()
    val done = AtomicBool.new(false)
    var all_hits = mutableListOf()
    scope({ scope ->
        for (unused in 0 until THREADS) {
            val s = w.stealer()
            val done1 = done.clone()
            val hits = AtomicUsize.new(0)
            all_hits.add(hits.clone())
            scope.spawn({
                val w2 = Worker.new_fifo()
                while (!done1.load(SeqCst)) {
                    when (val matchVal = s.steal()) {
                        is Success -> {
                            hits.fetch_add(1, SeqCst)
                        }
                        else -> {}
                    }
                    s.steal_batch(w2)
                    when (val matchVal1 = s.steal_batch_and_pop(w2)) {
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
                    while (w.pop() != null) {
                        my_hits += 1
                    }
                } else {
                    w.push(i)
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
    val w = Worker.new_fifo()
    val dropped = Mutex.new(mutableListOf())
    val remaining = AtomicUsize.new(COUNT)
    for (i in 0 until COUNT) {
        w.push(Elem(i, dropped.clone()))
    }
    scope({ scope ->
        for (unused in 0 until THREADS) {
            val remaining1 = remaining.clone()
            val s = w.stealer()
            scope.spawn({
                val w2 = Worker.new_fifo()
                var cnt = 0
                while (cnt < STEPS) {
                    when (val matchVal = s.steal()) {
                        is Success -> {
                            cnt += 1
                            remaining1.fetch_sub(1, SeqCst)
                        }
                        else -> {}
                    }
                    s.steal_batch(w2)
                    when (val matchVal1 = s.steal_batch_and_pop(w2)) {
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
            if (w.pop() != null) {
                remaining.fetch_sub(1, SeqCst)
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
    drop(w)
    run {
        var v1 = dropped.lock().unwrap()
        check(v1.len() == rem)
        v1.sort_unstable()
        for (pair in v1.windows(2)) {
            check(pair[0] + 1 == pair[1])
        }
    }
}
