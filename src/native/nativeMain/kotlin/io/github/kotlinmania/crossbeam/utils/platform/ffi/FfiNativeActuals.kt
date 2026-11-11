@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.ExperimentalUnsignedTypes::class)

package io.github.kotlinmania.crossbeam.utils.platform.ffi

import io.github.kotlinmania.crossbeam.utils.platform.ffi.cinterop.crossbeam_utils_platform_ffi_inline_0_crossbeam_thread_id
import io.github.kotlinmania.crossbeam.utils.platform.ffi.cinterop.crossbeam_utils_platform_ffi_inline_1_crossbeam_spin_loop
import io.github.kotlinmania.crossbeam.utils.platform.ffi.cinterop.crossbeam_utils_platform_ffi_inline_2_crossbeam_yield_thread

internal actual fun crossbeam_thread_idC(): ULong =
    crossbeam_utils_platform_ffi_inline_0_crossbeam_thread_id()

internal actual fun crossbeam_spin_loopC1() {
    crossbeam_utils_platform_ffi_inline_1_crossbeam_spin_loop()
}

internal actual fun crossbeam_yield_threadC2() {
    crossbeam_utils_platform_ffi_inline_2_crossbeam_yield_thread()
}
