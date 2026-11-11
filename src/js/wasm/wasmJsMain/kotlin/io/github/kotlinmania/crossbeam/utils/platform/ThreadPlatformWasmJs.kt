@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.kotlinmania.crossbeam.utils.platform

import io.github.kotlinmania.crossbeam.utils.platform.ffi.crossbeam_spin_loop
import io.github.kotlinmania.crossbeam.utils.platform.ffi.crossbeam_thread_id
import io.github.kotlinmania.crossbeam.utils.platform.ffi.crossbeam_yield_thread

@JsFun("() => typeof process !== 'undefined' && !!process.versions && typeof process.versions.node === 'string'")
private external fun isNodeRuntime(): Boolean

private val nodeRuntime = isNodeRuntime()

// Node uses real OS primitives through the generated N-API companion. Browser
// agents do not share Kotlin objects: 1 identifies only this single-agent runtime.
// This is not an implementation of Rust's never-reused process-wide ThreadId.
internal actual fun currentThreadId(): Long = if (nodeRuntime) crossbeam_thread_id().toLong() else 1L

// Rust library/core/src/hint.rs has no spin instruction for wasm. A browser
// therefore follows that no-op branch; a Node host executes the real CPU hint.
internal actual fun spinLoop() {
    if (nodeRuntime) crossbeam_spin_loop()
}

// Rust library/std/src/sys/thread/unsupported.rs::yield_now is a no-op for
// unsupported wasm threads. This does not schedule browser tasks or add workers.
internal actual fun yieldThread() {
    if (nodeRuntime) crossbeam_yield_thread()
}
