// port-lint: source crossbeam-epoch/benches/flush.rs
package io.github.kotlinmania.crossbeam.epoch.benches.flush

import io.github.kotlinmania.crossbeam.utils.thread.scope
import io.github.kotlinmania.test.Bencher

public fun single_flush(b: Bencher) {
    val THREADS: Int = 16
    val start = Barrier.new(THREADS + 1uL)
    val end = Barrier.new(THREADS + 1uL)
    scope({ s ->
        for (unused in 0 until THREADS) {
            s.spawn({
                epoch.pin()
                start.wait()
                end.wait()
            })
        }
        start.wait()
        b.iter({ epoch.pin().flush() })
        end.wait()
    }).unwrap()
}

public fun multi_flush(b: Bencher) {
    val THREADS: Int = 16
    val STEPS: Int = 10000
    b.iter({
        scope({ s ->
            for (unused in 0 until THREADS) {
                s.spawn({
                    for (unused1 in 0 until STEPS) {
                        val guard = epoch.pin()
                        guard.flush()
                    }
                })
            }
        }).unwrap()
    })
}
