// port-lint: source crossbeam-utils/benches/atomic_cell.rs
package io.github.kotlinmania.crossbeam.utils.benches.atomiccell

import io.github.kotlinmania.crossbeam.utils.atomic.AtomicCell
import io.github.kotlinmania.crossbeam.utils.crossbeamchannel.benchmarks.atomicring.THREADS
import io.github.kotlinmania.crossbeam.utils.thread
import io.github.kotlinmania.test.Bencher

public fun load_u8(b: Bencher) {
    val a = AtomicCell.new(0u.toUByte())
    var sum = 0
    b.iter({ sum += a.load() })
    black_box(sum)
}

public fun store_u8(b: Bencher) {
    val a = AtomicCell.new(0u.toUByte())
    b.iter({ a.store(1) })
}

public fun fetch_add_u8(b: Bencher) {
    val a = AtomicCell.new(0u.toUByte())
    b.iter({ a.fetch_add(1) })
}

public fun compare_exchange_u8(b: Bencher) {
    val a = AtomicCell.new(0u.toUByte())
    var i = 0
    b.iter({
        a.compare_exchange(i, (i + 1.toLong()))
        i = (i + 1.toLong())
    })
}

public fun concurrent_load_u8(b: Bencher) {
    val THREADS: Int = 2
    val STEPS: Int = 1000000
    val start = Barrier.new(THREADS + 1uL)
    val end = Barrier.new(THREADS + 1uL)
    val exit = AtomicCell.new(false)
    val a = AtomicCell.new(0u.toUByte())
    thread.scope({ scope ->
        for (unused in 0 until THREADS) {
            scope.spawn({ while (true) {
                start.wait()
                var sum = 0
                for (unused1 in 0 until STEPS) {
                    sum += a.load()
                }
                black_box(sum)
                end.wait()
                if (exit.load()) {
                    break
                }
            } })
        }
        start.wait()
        end.wait()
        b.iter({
            start.wait()
            end.wait()
        })
        start.wait()
        exit.store(true)
        end.wait()
    }).unwrap()
}

public fun load_usize(b: Bencher) {
    val a = AtomicCell.new(0uL)
    var sum = 0
    b.iter({ sum += a.load() })
    black_box(sum)
}

public fun store_usize(b: Bencher) {
    val a = AtomicCell.new(0uL)
    b.iter({ a.store(1) })
}

public fun fetch_add_usize(b: Bencher) {
    val a = AtomicCell.new(0uL)
    b.iter({ a.fetch_add(1) })
}

public fun compare_exchange_usize(b: Bencher) {
    val a = AtomicCell.new(0uL)
    var i = 0
    b.iter({
        a.compare_exchange(i, (i + 1.toLong()))
        i = (i + 1.toLong())
    })
}

public fun concurrent_load_usize(b: Bencher) {
    val THREADS: Int = 2
    val STEPS: Int = 1000000
    val start = Barrier.new(THREADS + 1uL)
    val end = Barrier.new(THREADS + 1uL)
    val exit = AtomicCell.new(false)
    val a = AtomicCell.new(0uL)
    thread.scope({ scope ->
        for (unused in 0 until THREADS) {
            scope.spawn({ while (true) {
                start.wait()
                var sum = 0
                for (unused1 in 0 until STEPS) {
                    sum += a.load()
                }
                black_box(sum)
                end.wait()
                if (exit.load()) {
                    break
                }
            } })
        }
        start.wait()
        end.wait()
        b.iter({
            start.wait()
            end.wait()
        })
        start.wait()
        exit.store(true)
        end.wait()
    }).unwrap()
}
