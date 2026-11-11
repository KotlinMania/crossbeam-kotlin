package io.github.kotlinmania.crossbeam.utils.platform.ffi

internal fun crossbeam_thread_id(): ULong = crossbeam_thread_idC()

internal fun crossbeam_spin_loop() {
    crossbeam_spin_loopC1()
}

internal fun crossbeam_yield_thread() {
    crossbeam_yield_threadC2()
}

internal expect fun crossbeam_thread_idC(): ULong

internal expect fun crossbeam_spin_loopC1()

internal expect fun crossbeam_yield_threadC2()
