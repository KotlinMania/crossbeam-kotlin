// port-lint: tests crossbeam-epoch/src/default.rs
package io.github.kotlinmania.crossbeam.epoch.default

import io.github.kotlinmania.crossbeam.epoch.default.pin
import io.github.kotlinmania.crossbeam.epoch.primitive.thread_local

// The default garbage collector.
//
// For each thread, a participant is lazily initialized on its first use, when the current thread
// is registered in the default collector.  If initialized, the thread's participant will get
// destructed on thread exit, which in turn unregisters the thread.

@kotlin.test.Test
public fun pin_while_exiting() {
    class Foo {
        public fun drop() {
            // Pin after `HANDLE` has been dropped. This must not panic.
            pin()
        }
    }
    thread_local(FOO, Foo, Foo)
    io.github.kotlinmania.crossbeam.utils.thread.scope({ scope ->
        scope.spawn({
            // Initialize `FOO` and then `HANDLE`.
            FOO.with({ Unit })
            pin()
        })
    }).unwrap()
}
