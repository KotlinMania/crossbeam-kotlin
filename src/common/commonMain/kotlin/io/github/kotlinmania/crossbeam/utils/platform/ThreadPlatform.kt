package io.github.kotlinmania.crossbeam.utils.platform

/** OS thread identity; valid only for the lifetime of the current thread. */
internal expect fun currentThreadId(): Long
internal expect fun spinLoop()
internal expect fun yieldThread()
