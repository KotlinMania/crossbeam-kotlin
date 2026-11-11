package io.github.kotlinmania.crossbeam.utils.platform

import io.github.kotlinmania.crossbeam.utils.platform.ffi.crossbeam_spin_loop
import io.github.kotlinmania.crossbeam.utils.platform.ffi.crossbeam_thread_id
import io.github.kotlinmania.crossbeam.utils.platform.ffi.crossbeam_yield_thread

internal actual fun currentThreadId(): Long = crossbeam_thread_id().toLong()
internal actual fun spinLoop() = crossbeam_spin_loop()
internal actual fun yieldThread() = crossbeam_yield_thread()
