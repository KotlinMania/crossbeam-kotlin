@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package io.github.kotlinmania.crossbeam.utils.platform.ffi

private object UtilsPlatformFfiJniBridge {
    init {
        System.loadLibrary("ffi_jni")
    }

    @JvmStatic
    external fun crossbeam_utils_platform_ffi_inline_0_crossbeam_thread_id(): Long

    @JvmStatic
    external fun crossbeam_utils_platform_ffi_inline_1_crossbeam_spin_loop()

    @JvmStatic
    external fun crossbeam_utils_platform_ffi_inline_2_crossbeam_yield_thread()
}

internal actual fun crossbeam_thread_idC(): ULong =
    UtilsPlatformFfiJniBridge.crossbeam_utils_platform_ffi_inline_0_crossbeam_thread_id().toULong()

internal actual fun crossbeam_spin_loopC1() {
    UtilsPlatformFfiJniBridge.crossbeam_utils_platform_ffi_inline_1_crossbeam_spin_loop()
}

internal actual fun crossbeam_yield_threadC2() {
    UtilsPlatformFfiJniBridge.crossbeam_utils_platform_ffi_inline_2_crossbeam_yield_thread()
}
