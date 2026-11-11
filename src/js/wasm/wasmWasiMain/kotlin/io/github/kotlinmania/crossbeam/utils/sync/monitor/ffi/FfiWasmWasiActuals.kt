// Rust std no_threads mutex/condvar semantics for the single-thread WASI backend.
package io.github.kotlinmania.crossbeam.utils.sync.monitor.ffi

import io.github.kotlinmania.crossbeam.utils.sync.monitor.NoThreadsMonitorHandles

internal actual fun monitor_createC(): ULong =
    NoThreadsMonitorHandles.create()

internal actual fun monitor_destroyC1(handle: ULong): Int =
    NoThreadsMonitorHandles.destroy(handle)

internal actual fun monitor_lockC2(handle: ULong): Int =
    NoThreadsMonitorHandles.lock(handle)

internal actual fun monitor_unlockC3(handle: ULong): Int =
    NoThreadsMonitorHandles.unlock(handle)

internal actual fun monitor_waitC4(handle: ULong): Int =
    NoThreadsMonitorHandles.awaitNotification(handle)

internal actual fun monitor_notify_allC5(handle: ULong): Int =
    NoThreadsMonitorHandles.notifyAllWaiters(handle)
