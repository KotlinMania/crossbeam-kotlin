@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package io.github.kotlinmania.crossbeam.utils.sync.monitor.ffi

private object UtilsSyncMonitorFfiJniBridge {
    init {
        io.github.kotlinmania.crossbeam.internal.FfiNativeLibrary
            .load()
    }

    @JvmStatic
    external fun crossbeam_utils_sync_monitor_ffi_inline_0_monitor_create(): Long

    @JvmStatic
    external fun crossbeam_utils_sync_monitor_ffi_inline_1_monitor_destroy(handle: Long): Int

    @JvmStatic
    external fun crossbeam_utils_sync_monitor_ffi_inline_2_monitor_lock(handle: Long): Int

    @JvmStatic
    external fun crossbeam_utils_sync_monitor_ffi_inline_3_monitor_unlock(handle: Long): Int

    @JvmStatic
    external fun crossbeam_utils_sync_monitor_ffi_inline_4_monitor_wait(handle: Long): Int

    @JvmStatic
    external fun crossbeam_utils_sync_monitor_ffi_inline_5_monitor_notify_all(handle: Long): Int
}

internal actual fun monitor_createC(): ULong =
    UtilsSyncMonitorFfiJniBridge.crossbeam_utils_sync_monitor_ffi_inline_0_monitor_create().toULong()

internal actual fun monitor_destroyC1(handle: ULong): Int =
    UtilsSyncMonitorFfiJniBridge.crossbeam_utils_sync_monitor_ffi_inline_1_monitor_destroy(handle.toLong())

internal actual fun monitor_lockC2(handle: ULong): Int =
    UtilsSyncMonitorFfiJniBridge.crossbeam_utils_sync_monitor_ffi_inline_2_monitor_lock(handle.toLong())

internal actual fun monitor_unlockC3(handle: ULong): Int =
    UtilsSyncMonitorFfiJniBridge.crossbeam_utils_sync_monitor_ffi_inline_3_monitor_unlock(handle.toLong())

internal actual fun monitor_waitC4(handle: ULong): Int =
    UtilsSyncMonitorFfiJniBridge.crossbeam_utils_sync_monitor_ffi_inline_4_monitor_wait(handle.toLong())

internal actual fun monitor_notify_allC5(handle: ULong): Int =
    UtilsSyncMonitorFfiJniBridge.crossbeam_utils_sync_monitor_ffi_inline_5_monitor_notify_all(handle.toLong())
