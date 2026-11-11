// port-lint: tests crossbeam-utils/src/backoff.rs
// Original Backoff::spin Rustdoc example; assertions unchanged.
package io.github.kotlinmania.crossbeam.utils.backoff

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
@kotlin.test.Test
public fun spin_example() {
    fun fetch_mul(a: AtomicLong, b: Long): Long {
        val backoff = Backoff.new()
        while (true) {
            val value = a.load()
            if (a.compareAndSet(value, value * b)) {
                return value
            }
            backoff.spin()
        }
    }
    val a = AtomicLong(7L)
    check(fetch_mul(a, 8L) == 7L)
    check(a.load() == 56L)
}
