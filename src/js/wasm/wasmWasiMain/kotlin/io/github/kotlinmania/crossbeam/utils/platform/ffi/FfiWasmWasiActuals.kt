@file:OptIn(kotlin.wasm.ExperimentalWasmInterop::class)

package io.github.kotlinmania.crossbeam.utils.platform.ffi

import kotlin.wasm.WasmImport

// Kotlin's configured wasmWasi backend has a single execution thread. The identity
// used by epoch registration is realm-local, not a fabricated native pointer.
internal actual fun crossbeam_thread_idC(): ULong = 1uL

// Rust core/src/hint.rs spin_loop selects its empty instruction-hint branch on Wasm.
internal actual fun crossbeam_spin_loopC1() = Unit

// Rust std's WASI thread backend selects Unix yield_now -> libc::sched_yield.
// Use the real WASI preview1 host call, rather than silently doing nothing.
@WasmImport("wasi_snapshot_preview1", "sched_yield")
private external fun wasiSchedYield(): Int

internal actual fun crossbeam_yield_threadC2() {
    wasiSchedYield()
}
