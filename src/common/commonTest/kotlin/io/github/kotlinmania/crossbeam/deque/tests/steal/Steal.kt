// port-lint: tests crossbeam-deque/tests/steal.rs
package io.github.kotlinmania.crossbeam.deque.tests.steal

import io.github.kotlinmania.crossbeam.deque.Injector
import io.github.kotlinmania.crossbeam.deque.Worker
import io.github.kotlinmania.crossbeam.deque.steal.Success

@kotlin.test.Test
public fun steal_fifo() {
    val w = Worker.new_fifo()
    for (i in 1..3) {
        w.push(i)
    }
    val s = w.stealer()
    check(s.steal() == Success(1))
    check(s.steal() == Success(2))
    check(s.steal() == Success(3))
}

@kotlin.test.Test
public fun steal_lifo() {
    val w = Worker.new_lifo()
    for (i in 1..3) {
        w.push(i)
    }
    val s = w.stealer()
    check(s.steal() == Success(1))
    check(s.steal() == Success(2))
    check(s.steal() == Success(3))
}

@kotlin.test.Test
public fun steal_injector() {
    val q = Injector.new()
    for (i in 1..3) {
        q.push(i)
    }
    check(q.steal() == Success(1))
    check(q.steal() == Success(2))
    check(q.steal() == Success(3))
}

@kotlin.test.Test
public fun steal_batch_fifo_fifo() {
    val w = Worker.new_fifo()
    for (i in 1..4) {
        w.push(i)
    }
    val s = w.stealer()
    val w2 = Worker.new_fifo()
    check(s.steal_batch(w2) == Success())
    check(w2.pop() == 1)
    check(w2.pop() == 2)
}

@kotlin.test.Test
public fun steal_batch_lifo_lifo() {
    val w = Worker.new_lifo()
    for (i in 1..4) {
        w.push(i)
    }
    val s = w.stealer()
    val w2 = Worker.new_lifo()
    check(s.steal_batch(w2) == Success())
    check(w2.pop() == 2)
    check(w2.pop() == 1)
}

@kotlin.test.Test
public fun steal_batch_fifo_lifo() {
    val w = Worker.new_fifo()
    for (i in 1..4) {
        w.push(i)
    }
    val s = w.stealer()
    val w2 = Worker.new_lifo()
    check(s.steal_batch(w2) == Success())
    check(w2.pop() == 1)
    check(w2.pop() == 2)
}

@kotlin.test.Test
public fun steal_batch_lifo_fifo() {
    val w = Worker.new_lifo()
    for (i in 1..4) {
        w.push(i)
    }
    val s = w.stealer()
    val w2 = Worker.new_fifo()
    check(s.steal_batch(w2) == Success())
    check(w2.pop() == 2)
    check(w2.pop() == 1)
}

@kotlin.test.Test
public fun steal_batch_injector_fifo() {
    val q = Injector.new()
    for (i in 1..4) {
        q.push(i)
    }
    val w2 = Worker.new_fifo()
    check(q.steal_batch(w2) == Success())
    check(w2.pop() == 1)
    check(w2.pop() == 2)
}

@kotlin.test.Test
public fun steal_batch_injector_lifo() {
    val q = Injector.new()
    for (i in 1..4) {
        q.push(i)
    }
    val w2 = Worker.new_lifo()
    check(q.steal_batch(w2) == Success())
    check(w2.pop() == 1)
    check(w2.pop() == 2)
}

@kotlin.test.Test
public fun steal_batch_and_pop_fifo_fifo() {
    val w = Worker.new_fifo()
    for (i in 1..6) {
        w.push(i)
    }
    val s = w.stealer()
    val w2 = Worker.new_fifo()
    check(s.steal_batch_and_pop(w2) == Success(1))
    check(w2.pop() == 2)
    check(w2.pop() == 3)
}

@kotlin.test.Test
public fun steal_batch_and_pop_lifo_lifo() {
    val w = Worker.new_lifo()
    for (i in 1..6) {
        w.push(i)
    }
    val s = w.stealer()
    val w2 = Worker.new_lifo()
    check(s.steal_batch_and_pop(w2) == Success(3))
    check(w2.pop() == 2)
    check(w2.pop() == 1)
}

@kotlin.test.Test
public fun steal_batch_and_pop_fifo_lifo() {
    val w = Worker.new_fifo()
    for (i in 1..6) {
        w.push(i)
    }
    val s = w.stealer()
    val w2 = Worker.new_lifo()
    check(s.steal_batch_and_pop(w2) == Success(1))
    check(w2.pop() == 2)
    check(w2.pop() == 3)
}

@kotlin.test.Test
public fun steal_batch_and_pop_lifo_fifo() {
    val w = Worker.new_lifo()
    for (i in 1..6) {
        w.push(i)
    }
    val s = w.stealer()
    val w2 = Worker.new_fifo()
    check(s.steal_batch_and_pop(w2) == Success(3))
    check(w2.pop() == 2)
    check(w2.pop() == 1)
}

@kotlin.test.Test
public fun steal_batch_and_pop_injector_fifo() {
    val q = Injector.new()
    for (i in 1..6) {
        q.push(i)
    }
    val w2 = Worker.new_fifo()
    check(q.steal_batch_and_pop(w2) == Success(1))
    check(w2.pop() == 2)
    check(w2.pop() == 3)
}

@kotlin.test.Test
public fun steal_batch_and_pop_injector_lifo() {
    val q = Injector.new()
    for (i in 1..6) {
        q.push(i)
    }
    val w2 = Worker.new_lifo()
    check(q.steal_batch_and_pop(w2) == Success(1))
    check(w2.pop() == 2)
    check(w2.pop() == 3)
}
