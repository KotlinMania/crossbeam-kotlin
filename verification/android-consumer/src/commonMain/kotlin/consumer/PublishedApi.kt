// port-lint: source crossbeam-utils/src/backoff.rs
// The original Backoff::spin Rustdoc function, calling the published artifact.
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package consumer

import io.github.kotlinmania.crossbeam.utils.backoff.Backoff
import kotlin.concurrent.atomics.AtomicLong

fun fetchMul(a: AtomicLong, b: Long): Long {
    val backoff = Backoff.new()
    while (true) {
        val value = a.load()
        if (a.compareAndSet(value, value * b)) return value
        backoff.spin()
    }
}
