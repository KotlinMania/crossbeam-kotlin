// port-lint: source crossbeam-epoch/benches/pin.rs
package io.github.kotlinmania.crossbeam.epoch.benches.pin

import io.github.kotlinmania.crossbeam.utils.thread.scope
import io.github.kotlinmania.test.Bencher

public fun single_pin(b: Bencher) {
    b.iter(epoch.pin)
}

public fun multi_pin(b: Bencher) {
    val THREADS: Int = 16
    val STEPS: Int = 100000
    b.iter({
        scope({ s ->
            for (unused in 0 until THREADS) {
                s.spawn({
                    for (unused1 in 0 until STEPS) {
                        epoch.pin()
                    }
                })
            }
        }).unwrap()
    })
}
