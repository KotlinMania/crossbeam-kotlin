// port-lint: source crossbeam-epoch/examples/sanitize.rs
package io.github.kotlinmania.crossbeam.epoch.examples.sanitize

import io.github.kotlinmania.crossbeam.epoch.atomic.Atomic
import io.github.kotlinmania.crossbeam.epoch.Collector
import io.github.kotlinmania.crossbeam.epoch.LocalHandle
import io.github.kotlinmania.crossbeam.epoch.atomic.Owned
import io.github.kotlinmania.crossbeam.epoch.atomic.Shared
import io.github.kotlinmania.rand.Rng

public fun worker(a: Atomic<AtomicUsize>, handle: LocalHandle): Int {
    var rng = thread_rng()
    var sum = 0
    if (rng.gen()) {
        thread.sleep(Duration.from_millis(1))
    }
    val timeout = Duration.from_millis(rng.gen_range(0 until 10))
    val now = Instant.now()
    while (now.elapsed() < timeout) {
        for (unused in 0 until 100) {
            val guard = handle.pin()
            guard.flush()
            val value = if (rng.gen()) {
                val p = a.swap(Owned.new(AtomicUsize.new(sum)), AcqRel, guard)
                run {
                    guard.defer_destroy(p)
                    guard.flush()
                    p.deref().load(Relaxed)
                }
            } else {
                val p1 = a.load(Acquire, guard)
                p1.deref().fetch_add(sum, Relaxed)
            }
            sum = (sum + value.toInt())
        }
    }
    return sum
}

public fun main() {
    for (unused in 0 until 100) {
        val collector = Collector.new()
        val a = Atomic.new(AtomicUsize.new(777))
        val threads = (0 until 16).map({
            val a1 = a.clone()
            val c = collector.clone()
            thread.spawn({ worker(a1, c.register()) })
        }).collect<Vec>()
        for (t in threads) {
            t.join().unwrap()
        }
        run {
            a.swap(0L, AcqRel, epoch.unprotected()).into_owned()
        }
    }
}
