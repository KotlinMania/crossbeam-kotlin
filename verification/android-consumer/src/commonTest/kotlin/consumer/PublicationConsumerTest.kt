// port-lint: tests crossbeam-utils/src/backoff.rs
// Original Backoff::spin Rustdoc assertions against the published Android artifact.
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package consumer

import kotlin.concurrent.atomics.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals

class PublicationConsumerTest {
    @Test
    fun publishedBackoffSpinRustdoc() {
        val a = AtomicLong(7L)
        assertEquals(7L, fetchMul(a, 8L))
        assertEquals(56L, a.load())
    }
}
