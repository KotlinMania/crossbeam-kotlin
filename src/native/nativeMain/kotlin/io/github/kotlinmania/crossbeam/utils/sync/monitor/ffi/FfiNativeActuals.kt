@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.ExperimentalUnsignedTypes::class)

package io.github.kotlinmania.crossbeam.utils.sync.monitor.ffi

import io.github.kotlinmania.crossbeam.utils.sync.monitor.ffi.cinterop.crossbeam_utils_sync_monitor_ffi_inline_0_monitor_create
import io.github.kotlinmania.crossbeam.utils.sync.monitor.ffi.cinterop.crossbeam_utils_sync_monitor_ffi_inline_1_monitor_destroy
import io.github.kotlinmania.crossbeam.utils.sync.monitor.ffi.cinterop.crossbeam_utils_sync_monitor_ffi_inline_2_monitor_lock
import io.github.kotlinmania.crossbeam.utils.sync.monitor.ffi.cinterop.crossbeam_utils_sync_monitor_ffi_inline_3_monitor_unlock
import io.github.kotlinmania.crossbeam.utils.sync.monitor.ffi.cinterop.crossbeam_utils_sync_monitor_ffi_inline_4_monitor_wait
import io.github.kotlinmania.crossbeam.utils.sync.monitor.ffi.cinterop.crossbeam_utils_sync_monitor_ffi_inline_5_monitor_notify_all

internal actual fun monitor_createC(): ULong =
    crossbeam_utils_sync_monitor_ffi_inline_0_monitor_create()

internal actual fun monitor_destroyC1(handle: ULong): Int =
    crossbeam_utils_sync_monitor_ffi_inline_1_monitor_destroy(handle)

internal actual fun monitor_lockC2(handle: ULong): Int =
    crossbeam_utils_sync_monitor_ffi_inline_2_monitor_lock(handle)

internal actual fun monitor_unlockC3(handle: ULong): Int =
    crossbeam_utils_sync_monitor_ffi_inline_3_monitor_unlock(handle)

internal actual fun monitor_waitC4(handle: ULong): Int =
    crossbeam_utils_sync_monitor_ffi_inline_4_monitor_wait(handle)

internal actual fun monitor_notify_allC5(handle: ULong): Int =
    crossbeam_utils_sync_monitor_ffi_inline_5_monitor_notify_all(handle)
