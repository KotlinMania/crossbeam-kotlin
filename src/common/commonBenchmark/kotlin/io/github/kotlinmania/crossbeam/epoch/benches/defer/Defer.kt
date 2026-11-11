// port-lint: source crossbeam-epoch/benches/defer.rs
package io.github.kotlinmania.crossbeam.epoch.benches.defer

import io.github.kotlinmania.crossbeam.epoch.atomic.Owned
import io.github.kotlinmania.crossbeam.utils.thread.scope
import io.github.kotlinmania.test.Bencher

public fun single_alloc_defer_free(b: Bencher) {
    b.iter({
        val guard = epoch.pin()
        val p = Owned.new(1).into_shared(guard)
        run {
            guard.defer_destroy(p)
        }
    })
}

public fun single_defer(b: Bencher) {
    b.iter({
        val guard = epoch.pin()
        guard.defer({ Unit })
    })
}

public fun multi_alloc_defer_free(b: Bencher) {
    val THREADS: Int = 16
    val STEPS: Int = 10000
    b.iter({
        scope({ s ->
            for (unused in 0 until THREADS) {
                s.spawn({
                    for (unused1 in 0 until STEPS) {
                        val guard = epoch.pin()
                        val p = Owned.new(1).into_shared(guard)
                        run {
                            guard.defer_destroy(p)
                        }
                    }
                })
            }
        }).unwrap()
    })
}

public fun multi_defer(b: Bencher) {
    val THREADS: Int = 16
    val STEPS: Int = 10000
    b.iter({
        scope({ s ->
            for (unused in 0 until THREADS) {
                s.spawn({
                    for (unused1 in 0 until STEPS) {
                        val guard = epoch.pin()
                        guard.defer({ Unit })
                    }
                })
            }
        }).unwrap()
    })
}
