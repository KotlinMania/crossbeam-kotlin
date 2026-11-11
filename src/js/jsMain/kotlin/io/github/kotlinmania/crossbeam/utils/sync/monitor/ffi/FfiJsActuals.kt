@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package io.github.kotlinmania.crossbeam.utils.sync.monitor.ffi

import io.github.kotlinmania.crossbeam.utils.sync.monitor.NoThreadsMonitorHandles

private val _utilsSyncMonitorFfiNapiAddon: dynamic =
    js("(function() { if (typeof process === 'undefined' || !process.versions || !process.versions.node) return null; if (typeof globalThis.__km_napi_crossbeam_monitor !== 'undefined') return globalThis.__km_napi_crossbeam_monitor; var mod = process.getBuiltinModule ? process.getBuiltinModule('module') : (process.mainModule && process.mainModule.constructor); var req = (mod && mod.createRequire) ? mod.createRequire(process.cwd() + '/package.json') : null; if (!req) throw new Error('Crossbeam N-API requires Node module.createRequire'); var resolved = null; try { resolved = req.resolve('@kotlinmania/ffi-native-bindings'); } catch (error) { if (error.code !== 'MODULE_NOT_FOUND') throw error; } if (resolved === null) { var fs = req('node:fs'); var path = req('node:path'); var dir = process.cwd(); for (var depth = 0; depth < 8; depth++) { var candidate = path.resolve(dir, 'src/js/node-ffi/dist/index.js'); if (fs.existsSync(candidate)) { resolved = candidate; break; } var parent = path.dirname(dir); if (parent === dir) break; dir = parent; } } if (resolved === null) throw new Error('Crossbeam N-API package @kotlinmania/ffi-native-bindings is missing'); var addon = req(resolved); var symbols = ['crossbeam_utils_sync_monitor_ffi_inline_0_monitor_create', 'crossbeam_utils_sync_monitor_ffi_inline_1_monitor_destroy', 'crossbeam_utils_sync_monitor_ffi_inline_2_monitor_lock', 'crossbeam_utils_sync_monitor_ffi_inline_3_monitor_unlock', 'crossbeam_utils_sync_monitor_ffi_inline_4_monitor_wait', 'crossbeam_utils_sync_monitor_ffi_inline_5_monitor_notify_all']; for (var index = 0; index < symbols.length; index++) { if (typeof addon[symbols[index]] !== 'function') throw new Error('Crossbeam N-API symbol is missing: ' + symbols[index]); } globalThis.__km_napi_crossbeam_monitor = addon; return addon; })()")

internal actual fun monitor_createC(): ULong =
    if (_utilsSyncMonitorFfiNapiAddon != null) (_utilsSyncMonitorFfiNapiAddon.crossbeam_utils_sync_monitor_ffi_inline_0_monitor_create() as String).toULong() else NoThreadsMonitorHandles.create()

internal actual fun monitor_destroyC1(handle: ULong): Int =
    if (_utilsSyncMonitorFfiNapiAddon != null) (_utilsSyncMonitorFfiNapiAddon.crossbeam_utils_sync_monitor_ffi_inline_1_monitor_destroy(handle.toString()) as Int) else NoThreadsMonitorHandles.destroy(handle)

internal actual fun monitor_lockC2(handle: ULong): Int =
    if (_utilsSyncMonitorFfiNapiAddon != null) (_utilsSyncMonitorFfiNapiAddon.crossbeam_utils_sync_monitor_ffi_inline_2_monitor_lock(handle.toString()) as Int) else NoThreadsMonitorHandles.lock(handle)

internal actual fun monitor_unlockC3(handle: ULong): Int =
    if (_utilsSyncMonitorFfiNapiAddon != null) (_utilsSyncMonitorFfiNapiAddon.crossbeam_utils_sync_monitor_ffi_inline_3_monitor_unlock(handle.toString()) as Int) else NoThreadsMonitorHandles.unlock(handle)

internal actual fun monitor_waitC4(handle: ULong): Int =
    if (_utilsSyncMonitorFfiNapiAddon != null) (_utilsSyncMonitorFfiNapiAddon.crossbeam_utils_sync_monitor_ffi_inline_4_monitor_wait(handle.toString()) as Int) else NoThreadsMonitorHandles.awaitNotification(handle)

internal actual fun monitor_notify_allC5(handle: ULong): Int =
    if (_utilsSyncMonitorFfiNapiAddon != null) (_utilsSyncMonitorFfiNapiAddon.crossbeam_utils_sync_monitor_ffi_inline_5_monitor_notify_all(handle.toString()) as Int) else NoThreadsMonitorHandles.notifyAllWaiters(handle)
