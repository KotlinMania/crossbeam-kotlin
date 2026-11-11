// port-lint: tests crossbeam-epoch/src/sync/queue.rs
package io.github.kotlinmania.crossbeam.epoch.sync.queue

import io.github.kotlinmania.crossbeam.epoch.default.pin

// Michael-Scott lock-free queue.
//
// Usable with any number of producers and consumers.
//
// Michael and Scott.  Simple, Fast, and Practical Non-Blocking and Blocking Concurrent Queue
// Algorithms.  PODC 1996.  <http://dl.acm.org/citation.cfm?id=248106>
//
// Simon Doherty, Lindsay Groves, Victor Luchangco, and Mark Moir. 2004b. Formal Verification of a
// Practical Lock-Free Queue Algorithm. <https://doi.org/10.1007/978-3-540-30232-2_7>

public class Queue<T>(public var queue: io.github.kotlinmania.crossbeam.epoch.crossbeamepoch.src.sync.queue.Queue<T> = io.github.kotlinmania.crossbeam.epoch.crossbeamepoch.src.sync.queue.Queue<T>()) {
    internal fun push(t: T) {
        val guard = pin()
        this.queue.push(t, guard)
    }

    internal fun is_empty(): Boolean {
        val guard = pin()
        val head = this.queue.head.load(Acquire, guard)
        val h = head.deref()
        return (h.next.load(Acquire, guard) == 0L)
    }

    internal fun try_pop(): T? {
        val guard = pin()
        return this.queue.try_pop(guard)
    }

    internal fun pop(): T {
        while (true) {
            when (val matchVal = this.try_pop()) {
                null -> { continue }
                else -> {
                    val t = matchVal
                    return t
                }
            }
        }
    }

    public companion object {
        internal fun <T> new(): Queue<T> {
            return Queue(queue = io.github.kotlinmania.crossbeam.epoch.crossbeamepoch.src.sync.queue.Queue.new())
        }
    }
}

public val CONC_COUNT: Long = 1000000L

@kotlin.test.Test
public fun push_try_pop_1() {
    val q: Queue<Long> = Queue.new()
    check(q.is_empty())
    q.push(37)
    check(!q.is_empty())
    check(q.try_pop() == 37)
    check(q.is_empty())
}

@kotlin.test.Test
public fun push_try_pop_2() {
    val q: Queue<Long> = Queue.new()
    check(q.is_empty())
    q.push(37)
    q.push(48)
    check(q.try_pop() == 37)
    check(!q.is_empty())
    check(q.try_pop() == 48)
    check(q.is_empty())
}

@kotlin.test.Test
public fun push_try_pop_many_seq() {
    val q: Queue<Long> = Queue.new()
    check(q.is_empty())
    for (i in 0 until 200) {
        q.push(i)
    }
    check(!q.is_empty())
    for (i1 in 0 until 200) {
        check(q.try_pop() == i1)
    }
    check(q.is_empty())
}

@kotlin.test.Test
public fun push_pop_1() {
    val q: Queue<Long> = Queue.new()
    check(q.is_empty())
    q.push(37)
    check(!q.is_empty())
    check(q.pop() == 37)
    check(q.is_empty())
}

@kotlin.test.Test
public fun push_pop_2() {
    val q: Queue<Long> = Queue.new()
    q.push(37)
    q.push(48)
    check(q.pop() == 37)
    check(q.pop() == 48)
}

@kotlin.test.Test
public fun push_pop_many_seq() {
    val q: Queue<Long> = Queue.new()
    check(q.is_empty())
    for (i in 0 until 200) {
        q.push(i)
    }
    check(!q.is_empty())
    for (i1 in 0 until 200) {
        check(q.pop() == i1)
    }
    check(q.is_empty())
}

@kotlin.test.Test
public fun push_try_pop_many_spsc() {
    val q: Queue<Long> = Queue.new()
    check(q.is_empty())
    io.github.kotlinmania.crossbeam.utils.thread.scope({ scope ->
        scope.spawn({
            var next = 0
            while (next < CONC_COUNT) {
                when (val matchVal = q.try_pop()) {
                    null -> {}
                    else -> {
                        val elem = matchVal
                        check(elem == next)
                        next += 1
                    }
                }
            }
        })
        for (i in 0 until CONC_COUNT) {
            q.push(i)
        }
    }).unwrap()
}

@kotlin.test.Test
public fun push_try_pop_many_spmc() {
    fun recv(_t: Int, q: Queue<Long>) {
        var cur = -1
        for (_i in 0 until CONC_COUNT) {
            when (val matchVal = q.try_pop()) {
                null -> {}
                else -> {
                    val elem = matchVal
                    check(elem > cur)
                    cur = elem
                    if (cur == CONC_COUNT - 1L) {
                        break
                    }
                }
            }
        }
    }
    val q: Queue<Long> = Queue.new()
    check(q.is_empty())
    io.github.kotlinmania.crossbeam.utils.thread.scope({ scope ->
        for (i in 0 until 3) {
            val q1 = q
            scope.spawn({ recv(i, q1) })
        }
        scope.spawn({
            for (i1 in 0 until CONC_COUNT) {
                q.push(i1)
            }
        })
    }).unwrap()
}

@kotlin.test.Test
public fun push_try_pop_many_mpmc() {
    sealed class LR {
        public data class Left(public var value: Long) : LR()
        public data class Right(public var value: Long) : LR()
    }
    val q: Queue<LR> = Queue.new()
    check(q.is_empty())
    io.github.kotlinmania.crossbeam.utils.thread.scope({ scope ->
        for (_t in 0 until 2) {
            scope.spawn({
                for (i in CONC_COUNT - 1L until CONC_COUNT) {
                    q.push(LR.Left(i))
                }
            })
            scope.spawn({
                for (i in CONC_COUNT - 1L until CONC_COUNT) {
                    q.push(LR.Right(i))
                }
            })
            scope.spawn({
                var vl = mutableListOf()
                var vr = mutableListOf()
                for (_i in 0 until CONC_COUNT) {
                    when (val matchVal = q.try_pop()) {
                        is LR.Left -> {
                            val (x) = matchVal as LR.Left
                            vl.add(x)
                        }
                        is LR.Right -> {
                            val (x1) = matchVal as LR.Right
                            vr.add(x1)
                        }
                        else -> {}
                    }
                }
                var vl2 = vl.toMutableList()
                var vr2 = vr.toMutableList()
                vl2.sort_unstable()
                vr2.sort_unstable()
                check(vl == vl2)
                check(vr == vr2)
            })
        }
    }).unwrap()
}

@kotlin.test.Test
public fun push_pop_many_spsc() {
    val q: Queue<Long> = Queue.new()
    io.github.kotlinmania.crossbeam.utils.thread.scope({ scope ->
        scope.spawn({
            var next = 0
            while (next < CONC_COUNT) {
                check(q.pop() == next)
                next += 1
            }
        })
        for (i in 0 until CONC_COUNT) {
            q.push(i)
        }
    }).unwrap()
    check(q.is_empty())
}

@kotlin.test.Test
public fun is_empty_dont_pop() {
    val q: Queue<Long> = Queue.new()
    q.push(20)
    q.push(20)
    check(!q.is_empty())
    check(!q.is_empty())
    check(q.try_pop() != null)
}
