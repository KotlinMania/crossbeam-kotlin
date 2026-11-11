package io.github.kotlinmania.crossbeam.utils.sync.monitor.ffi

internal fun monitor_create(): ULong = monitor_createC()

internal fun monitor_destroy(handle: ULong): Int = monitor_destroyC1(handle)

internal fun monitor_lock(handle: ULong): Int = monitor_lockC2(handle)

internal fun monitor_unlock(handle: ULong): Int = monitor_unlockC3(handle)

internal fun monitor_wait(handle: ULong): Int = monitor_waitC4(handle)

internal fun monitor_notify_all(handle: ULong): Int = monitor_notify_allC5(handle)

internal expect fun monitor_createC(): ULong

internal expect fun monitor_destroyC1(handle: ULong): Int

internal expect fun monitor_lockC2(handle: ULong): Int

internal expect fun monitor_unlockC3(handle: ULong): Int

internal expect fun monitor_waitC4(handle: ULong): Int

internal expect fun monitor_notify_allC5(handle: ULong): Int
