// port-lint: tests crossbeam-utils/tests/wait_group.rs
package io.github.kotlinmania.crossbeam.utils.tests.waitgroup

import io.github.kotlinmania.crossbeam.utils.sync.waitgroup.WaitGroup

public val THREADS: ULong = 10uL

@kotlin.test.Test
public fun wait() {
    val wg = WaitGroup.new()
    val (tx, rx) = mpsc.channel()
    for (unused in 0 until THREADS) {
        val wg1 = wg.clone()
        val tx1 = tx.clone()
        thread.spawn({
            wg1.await()
            tx1.send().unwrap()
        })
    }
    thread.sleep(Duration.from_millis(100))
    // At this point, all spawned threads should be blocked, so we shouldn't get anything from the
    // channel.
    check(rx.try_recv().is_err())
    wg.await()
    // Now, the wait group is cleared and we should receive messages.
    for (unused1 in 0 until THREADS) {
        rx.recv().unwrap()
    }
}

@kotlin.test.Test
public fun wait_and_drop() {
    val wg = WaitGroup.new()
    val wg2 = WaitGroup.new()
    val (tx, rx) = mpsc.channel()
    for (unused in 0 until THREADS) {
        val wg1 = wg.clone()
        val wg21 = wg2.clone()
        val tx1 = tx.clone()
        thread.spawn({
            wg21.await()
            tx1.send().unwrap()
            drop(wg1)
        })
    }
    // At this point, no thread has gotten past `wg2.await()`, so we shouldn't get anything from the
    // channel.
    check(rx.try_recv().is_err())
    drop(wg2)
    wg.await()
    // Now, the wait group is cleared and we should receive messages.
    for (unused1 in 0 until THREADS) {
        rx.try_recv().unwrap()
    }
}
