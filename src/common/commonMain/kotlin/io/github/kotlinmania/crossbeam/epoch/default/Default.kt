// port-lint: source crossbeam-epoch/src/default.rs
package io.github.kotlinmania.crossbeam.epoch.default

import io.github.kotlinmania.crossbeam.epoch.collector.Collector
import io.github.kotlinmania.crossbeam.epoch.collector.LocalHandle
import io.github.kotlinmania.crossbeam.epoch.guard.Guard
import io.github.kotlinmania.crossbeam.epoch.primitive.thread_local
import io.github.kotlinmania.crossbeam.epoch.sync.oncelock.OnceLock

// The default garbage collector.
//
// For each thread, a participant is lazily initialized on its first use, when the current thread
// is registered in the default collector.  If initialized, the thread's participant will get
// destructed on thread exit, which in turn unregisters the thread.

/** The global data for the default garbage collector. */
private val COLLECTOR: OnceLock<Collector> = OnceLock.new()

public fun collector(): Collector = COLLECTOR.get_or_init(Collector::new)

/**
 * Pins the current thread.
 */
public fun pin(): Guard {
    return with_handle({ handle -> handle.pin() })
}

/**
 * Returns `true` if the current thread is pinned.
 */
public fun is_pinned(): Boolean {
    return with_handle({ handle -> handle.is_pinned() })
}

/**
 * Returns the default global collector.
 */
public fun default_collector(): Collector {
    return collector()
}

public fun <R> with_handle(f: (LocalHandle) -> R): R {
    return HANDLE.try_with({ h -> f(h) }).unwrap_or_else({ f(collector().register()) })
}
