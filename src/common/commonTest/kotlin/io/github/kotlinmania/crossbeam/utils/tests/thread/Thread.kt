// port-lint: tests crossbeam-utils/tests/thread.rs
package io.github.kotlinmania.crossbeam.utils.tests.thread

import io.github.kotlinmania.crossbeam.utils.thread

public val THREADS: ULong = 10uL

public val SMALL_STACK_SIZE: ULong = 20uL

@kotlin.test.Test
public fun join() {
    val counter = AtomicUsize.new(0)
    thread.scope({ scope ->
        val handle = scope.spawn({
            counter.store(1, Ordering.Relaxed)
        })
        check(handle.join().is_ok())
        val panic_handle = scope.spawn({
            error("\"My honey is running out!\", said Pooh.")
        })
        check(panic_handle.join().is_err())
    }).unwrap()
    // There should be sufficient synchronization.
    check(1 == counter.load(Ordering.Relaxed))
}

@kotlin.test.Test
public fun counter() {
    val counter = AtomicUsize.new(0)
    thread.scope({ scope ->
        for (unused in 0 until THREADS) {
            scope.spawn({
                counter.fetch_add(1, Ordering.Relaxed)
            })
        }
    }).unwrap()
    check(THREADS == counter.load(Ordering.Relaxed))
}

@kotlin.test.Test
public fun counter_builder() {
    val counter = AtomicUsize.new(0)
    thread.scope({ scope ->
        for (i in 0 until THREADS) {
            scope.builder().name("child-$i").stack_size(SMALL_STACK_SIZE).spawn({
                counter.fetch_add(1, Ordering.Relaxed)
            }).unwrap()
        }
    }).unwrap()
    check(THREADS == counter.load(Ordering.Relaxed))
}

@kotlin.test.Test
public fun counter_panic() {
    val counter = AtomicUsize.new(0)
    val result = thread.scope({ scope ->
        scope.spawn({
            error("\"My honey is running out!\", said Pooh.")
        })
        sleep(Duration.from_millis(100))
        for (unused in 0 until THREADS) {
            scope.spawn({
                counter.fetch_add(1, Ordering.Relaxed)
            })
        }
    })
    check(THREADS == counter.load(Ordering.Relaxed))
    check(result.is_err())
}

@kotlin.test.Test
public fun panic_twice() {
    val result = thread.scope({ scope ->
        scope.spawn({
            sleep(Duration.from_millis(500))
            error("thread #1")
        })
        scope.spawn({
            error("thread #2")
        })
    })
    val err = result.unwrap_err()
    val vec = err.downcast_ref<List<Any>>().unwrap()
    check(2 == vec.len())
    val first = vec[0].downcast_ref<String>().unwrap()
    val second = vec[1].downcast_ref<String>().unwrap()
    check("thread #1" == first)
    check("thread #2" == second)
}

@kotlin.test.Test
public fun panic_many() {
    val result = thread.scope({ scope ->
        scope.spawn({ error("deliberate panic #1") })
        scope.spawn({ error("deliberate panic #2") })
        scope.spawn({ error("deliberate panic #3") })
    })
    val err = result.unwrap_err()
    val vec = err.downcast_ref<List<Any>>().unwrap()
    check(3 == vec.len())
    for (panic in vec.iter()) {
        val panic1 = panic.downcast_ref<String>().unwrap()
        check(panic1 == "deliberate panic #1" || panic1 == "deliberate panic #2" || panic1 == "deliberate panic #3")
    }
}

@kotlin.test.Test
public fun nesting() {
    data class Wrapper(public var variable: String = "") {
        public fun recurse(scope: thread.Scope, depth: Int) {
            check(this.variable == "foo")
            if (depth > 0) {
                scope.spawn({ scope1 ->
                    this.recurse(scope1, depth - 1)
                })
            }
        }
    }
    val variable = "foo".to_string()
    val wrapper = Wrapper(variable = variable)
    thread.scope({ scope ->
        scope.spawn({ scope1 ->
            scope1.spawn({ scope2 ->
                wrapper.recurse(scope2, 5)
            })
        })
    }).unwrap()
}

@kotlin.test.Test
public fun join_nested() {
    thread.scope({ scope ->
        scope.spawn({ scope1 ->
            val handle = scope1.spawn({ 7 })
            sleep(Duration.from_millis(200))
            handle.join().unwrap()
        })
        sleep(Duration.from_millis(100))
    }).unwrap()
}

@kotlin.test.Test
public fun scope_returns_ok() {
    val result = thread.scope({ scope -> scope.spawn({ 1234 }).join().unwrap() }).unwrap()
    check(result == 1234)
}

@kotlin.test.Test
public fun as_pthread_t() {
    thread.scope({ scope ->
        val handle = scope.spawn({ _scope ->
            sleep(Duration.from_millis(100))
            42
        })
        val _pthread_t = handle.as_pthread_t()
        handle.join().unwrap()
    }).unwrap()
}
