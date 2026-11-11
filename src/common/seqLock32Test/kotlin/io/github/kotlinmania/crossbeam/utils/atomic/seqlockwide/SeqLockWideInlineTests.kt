// port-lint: tests crossbeam-utils/src/atomic/seq_lock_wide.rs
package io.github.kotlinmania.crossbeam.utils.atomic.seqlockwide

private val LK: SeqLock = SeqLock.new()

@kotlin.test.Test
public fun test_abort() {
    val before = checkNotNull(LK.optimistic_read())
    run {
        LK.write().use { guard ->
            guard.abort()
        }
    }
    val after = checkNotNull(LK.optimistic_read())
    check(before == after) { "aborted write does not update the stamp" }
}
