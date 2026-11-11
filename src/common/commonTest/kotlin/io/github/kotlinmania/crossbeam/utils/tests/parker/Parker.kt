// port-lint: tests crossbeam-utils/tests/parker.rs
package io.github.kotlinmania.crossbeam.utils.tests.parker

import io.github.kotlinmania.crossbeam.utils.sync.parker.Parker
import io.github.kotlinmania.crossbeam.utils.thread

@kotlin.test.Test
public fun park_timeout_unpark_before() {
    val p = Parker.new()
    for (unused in 0 until 10) {
        p.unparker().unpark()
        p.park_timeout(Duration.from_millis(UInt.MAX_VALUE.toULong()))
    }
}

@kotlin.test.Test
public fun park_timeout_unpark_not_called() {
    val p = Parker.new()
    for (unused in 0 until 10) {
        p.park_timeout(Duration.from_millis(10))
    }
}

@kotlin.test.Test
public fun park_timeout_unpark_called_other_thread() {
    for (unused in 0 until 10) {
        val p = Parker.new()
        val u = p.unparker().clone()
        thread.scope({ scope ->
            scope.spawn({
                sleep(Duration.from_millis(50))
                u.unpark()
            })
            p.park_timeout(Duration.from_millis(UInt.MAX_VALUE.toULong()))
        }).unwrap()
    }
}
