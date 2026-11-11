// port-lint: source crossbeam-utils/src/thread.rs
package io.github.kotlinmania.crossbeam.utils.thread

import io.github.kotlinmania.crossbeam.utils.crossbeamchannel.src.utils.Mutex
import io.github.kotlinmania.crossbeam.utils.sync.waitgroup.WaitGroup

// Threads that can borrow variables from the stack.
//
// Create a scope when spawned threads need to access variables on the stack:
//
// ```kotlin
// import crossbeamutils.thread
//
// val people = mutableListOf("Alice".toString(), "Bob".toString(), "Carol".toString())
// thread.scope({ s ->
//     for (person in people) {
//         s.spawn({
//             println("Hello, $person!")
//         })
//     }
// }).unwrap()
// ```
//
// # Why scoped threads?
//
// Suppose we wanted to re-write the previous example using plain threads:
//
// ```kotlin
// val people = mutableListOf("Alice".toString(), "Bob".toString(), "Carol".toString())
// var threads = mutableListOf()
// for (person in people) {
//     threads.add(thread.spawn({
//         println("Hello, $person!")
//     }))
// }
// for (thread in threads) {
//     thread.join().unwrap()
// }
// ```
//
// This doesn't work because the borrow checker complains about `people` not living long enough:
//
// ```text
// error[E0597]: `people` does not live long enough
//   --> src/main.rs:12:20
//    |
// 12 |     for person in &people {
//    |                    ^^^^^^ borrowed value does not live long enough
// ...
// 21 | }
//    | - borrowed value only lives until here
//    |
//    = note: borrowed value must be valid for the static scope...
// ```
//
// The problem here is that spawned threads are not allowed to borrow variables on stack because
// the compiler cannot prove they will be joined before `people` is destroyed.
//
// Scoped threads are a mechanism to guarantee to the compiler that spawned threads will be joined
// before the scope ends.
//
// # How scoped threads work
//
// If a variable is borrowed by a thread, the thread must complete before the variable is
// destroyed. Threads spawned using [std.thread.spawn] can only borrow variables with the
// `static` scope because the borrow checker cannot be sure when the thread will complete.
//
// A scope creates a clear boundary between variables outside the scope and threads inside the
// scope. Whenever a scope spawns a thread, it promises to join the thread before the scope ends.
// This way we guarantee to the borrow checker that scoped threads only live within the scope and
// can safely access variables outside it.
//
// # Nesting scoped threads
//
// Sometimes scoped threads need to spawn more threads within the same scope. This is a little
// tricky because argument `s` lives *inside* the invocation of `thread.scope()` and as such
// cannot be borrowed by scoped threads:
//
// ```kotlin
// import crossbeamutils.thread
//
// thread.scope({ s ->
//     s.spawn({
//         // Not going to compile because we're trying to borrow `s`,
//         // which lives *inside* the scope! :(
//         s.spawn({ println("nested thread") })
//     })
// })
// ```
//
// Fortunately, there is a solution. Every scoped thread is passed a reference to its scope as an
// argument, which can be used for spawning nested threads:
//
// ```kotlin
// import crossbeamutils.thread
//
// thread.scope({ s ->
//     // Note the `| s |` here.
//     s.spawn({ s1 ->
//         // Yay, this works because we're using a fresh argument `s`! :)
//         s1.spawn({ println("nested thread") })
//     })
// }).unwrap()
// ```

public typealias SharedVec<T> = Mutex<List<T>>

public typealias SharedOption<T> = Mutex<T?>

/**
 * Creates a new scope for spawning threads.
 *
 * All child threads that haven't been manually joined will be automatically joined just before
 * this function invocation ends. If all joined threads have successfully completed, `Ok` is
 * returned with the return value of `f`. If any of the joined threads has panicked, an `Err` is
 * returned containing errors from panicked threads. Note that if panics are implemented by
 * aborting the process, no error is returned; see the notes of [std::panic::catch_unwind].
 *
 * **Note:** Since Rust 1.63, this function is soft-deprecated in favor of the more efficient [std.thread.scope].
 *
 * # Examples
 *
 * ```kotlin
 * import crossbeamutils.thread
 *
 * val variable = mutableListOf(1, 2, 3)
 * thread.scope({ s ->
 *     s.spawn({
 *         println("A child thread borrowing `var`: $variable")
 *     })
 * }).unwrap()
 * ```
 */
public fun <R> scope(f: (Scope) -> R): thread.Result<R> {
    class AbortOnPanic {
        public fun drop() {
            if (thread.panicking()) {
                abort()
            }
        }
    }
    val wg = WaitGroup.new()
    val scope = Scope(handles = SharedVec.default(), wait_group = wg.clone(), _marker = Unit)
    // Execute the scoped function, but catch any panics.
    val result = panic.catch_unwind(panic.AssertUnwindSafe({ f(scope) }))
    // If an unwinding panic occurs before all threads are joined
    // promote it to an aborting panic to prevent any threads from escaping the scope.
    val guard = AbortOnPanic()
    // Wait until all nested scopes are dropped.
    drop(scope.wait_group)
    wg.wait()
    // Join all remaining spawned threads.
    val panics: Vec = scope.handles.lock().unwrap().drain(0 until 0).filter_map({ handle -> handle.lock().unwrap().take() }).filter_map({ handle -> handle.join().err() }).collect()
    mem.forget(guard)
    // If `f` has panicked, resume unwinding.
    // If any of the child threads have panicked, return the panic errors.
    // Otherwise, everything is OK and return the result of `f`.
    return if (result.isSuccess) {
        val res = result.getOrThrow()
        if (panics.is_empty()) {
            Result.success(res)
        } else {
            Result.failure(panics)
        }
    } else {
        val err = result.exceptionOrNull()!!
        panic.resume_unwind(err)
    }
}

/**
 * A scope for spawning threads.
 */
public data class Scope(
    /**
     * The list of the thread join handles.
     */
    public var handles: SharedVec<SharedOption<thread.JoinHandle<Unit>>> = SharedVec<SharedOption<thread.JoinHandle<Unit>>>(),
    /**
     * Used to wait until all subscopes all dropped.
     */
    public var wait_group: WaitGroup = WaitGroup(),
    /**
     * Borrows data with invariant scope `env`.
     */
    public var _marker: Unit = Unit,
) {
    /**
     * Spawns a scoped thread.
     *
     * This method is similar to the [spawn] function in Rust's standard library. The difference
     * is that this thread is scoped, meaning it's guaranteed to terminate before the scope exits,
     * allowing it to reference variables outside the scope.
     *
     * The scoped thread is passed a reference to this scope as an argument, which can be used for
     * spawning nested threads.
     *
     * The returned [handle](ScopedJoinHandle) can be used to manually
     * [join](ScopedJoinHandle::join) the thread before the scope exits.
     *
     * This will create a thread using default parameters of [ScopedThreadBuilder], if you want to specify the
     * stack size or the name of the thread, use this API instead.
     *
     * # Panics
     *
     * Panics if the OS fails to create a thread; use [ScopedThreadBuilder.spawn]
     * to recover from such errors.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.thread
     *
     * thread.scope({ s ->
     *     val handle = s.spawn({
     *         println("A child thread is running")
     *         42
     *     })
     *     // Join the thread and retrieve its result.
     *     val res = handle.join().unwrap()
     *     check(res == 42)
     * }).unwrap()
     * ```
     */
    public fun <T> spawn(f: (Scope) -> T): ScopedJoinHandle<T> {
        return this.builder().spawn(f).expect("failed to spawn scoped thread")
    }

    /**
     * Creates a builder that can configure a thread before spawning.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.thread
     *
     * thread.scope({ s ->
     *     s.builder().spawn({ println("A child thread is running") }).unwrap()
     * }).unwrap()
     * ```
     */
    public fun builder(): ScopedThreadBuilder {
        return ScopedThreadBuilder(scope = this, builder = thread.Builder.new())
    }

    public fun fmt(f: StringBuilder) {
        f.append("Scope { .. }")
    }
}

/**
 * Configures the properties of a new thread.
 *
 * The two configurable properties are:
 *
 * - [name]: Specifies an [associated name for the thread][naming-threads].
 * - [stack_size]: Specifies the [desired stack size for the thread][stack-size].
 *
 * The [spawn] method will take ownership of the builder and return an [io.Result] of the
 * thread handle with the given configuration.
 *
 * The [Scope.spawn] method uses a builder with default configuration and unwraps its return
 * value. You may want to use this builder when you want to recover from a failure to launch a
 * thread.
 *
 * # Examples
 *
 * ```kotlin
 * import crossbeamutils.thread
 *
 * thread.scope({ s ->
 *     s.builder().spawn({ println("Running a child thread") }).unwrap()
 * }).unwrap()
 * ```
 *
 * [name]: ScopedThreadBuilder::name
 * [stack_size]: ScopedThreadBuilder::stack_size
 * [spawn]: ScopedThreadBuilder::spawn
 */
public data class ScopedThreadBuilder(public var scope: Scope = Scope(), public var builder: thread.Builder = thread.Builder()) {
    /**
     * Sets the name for the new thread.
     *
     * The name must not contain null bytes (`\0`).
     *
     * For more information about named threads, see [here][naming-threads].
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.thread
     * import std.thread.current
     *
     * thread.scope({ s ->
     *     s.builder().name("my thread".toString()).spawn({ check(current().name() == "my thread") }).unwrap()
     * }).unwrap()
     * ```
     */
    public fun name(name: String): ScopedThreadBuilder {
        this.builder = this.builder.name(name)
        return this
    }

    /**
     * Sets the size of the stack for the new thread.
     *
     * The stack size is measured in bytes.
     *
     * For more information about the stack size for threads, see [here][stack-size].
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.thread
     *
     * thread.scope({ s ->
     *     s.builder().stackSize(32 * 1024).spawn({ println("Running a child thread") }).unwrap()
     * }).unwrap()
     * ```
     */
    public fun stack_size(size: Int): ScopedThreadBuilder {
        this.builder = this.builder.stack_size(size)
        return this
    }

    /**
     * Spawns a scoped thread with this configuration.
     *
     * The scoped thread is passed a reference to this scope as an argument, which can be used for
     * spawning nested threads.
     *
     * The returned handle can be used to manually join the thread before the scope exits.
     *
     * # Errors
     *
     * Unlike the [Scope.spawn] method, this method yields an
     * [io.Result] to capture any failure to create the thread at
     * the OS level.
     *
     * # Panics
     *
     * Panics if a thread name was set and it contained null bytes.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.thread
     *
     * thread.scope({ s ->
     *     val handle = s.builder().spawn({
     *         println("A child thread is running")
     *         42
     *     }).unwrap()
     *     // Join the thread and retrieve its result.
     *     val res = handle.join().unwrap()
     *     check(res == 42)
     * }).unwrap()
     * ```
     */
    public fun <T> spawn(f: (Scope) -> T): io.Result<ScopedJoinHandle<T>> {
        // The result of `f` will be stored here.
        val result = SharedOption.default()
        // Spawn the thread and grab its join handle and thread handle.
        val (handle2, thread1) = run {
            val result1 = Arc.clone(result)
            // A clone of the scope that will be moved into the new thread.
            val scope = Scope(handles = Arc.clone(this.scope.handles), wait_group = this.scope.wait_group.clone(), _marker = Unit)
            // Spawn the thread.
            val handle = run {
                val closure = {
                    // Make sure the scope is inside the closure with the proper `env` scope.
                    val scope1: Scope = scope
                    // Run the closure.
                    val res = f(scope1)
                    // Store the result if the closure didn't panic.
                    result1.lock().unwrap() = res
                }
                // Allocate `closure` on the heap and erase the `env` bound.
                val closure1: () -> Unit = closure
                val closure2: () -> Unit = mem.transmute(closure1)
                // Finally, spawn the closure.
                this.builder.spawn(closure2).getOrElse { return Result.failure(it) }
            }
            val thread = handle.thread().clone()
            val handle1 = Mutex.new(handle)
            Pair(handle1, thread)
        }
        // Add the handle to the shared list of join handles.
        this.scope.handles.lock().unwrap().push(Arc.clone(handle2))
        return Result.success(ScopedJoinHandle(handle = handle2, result = result, thread = thread1, _marker = Unit))
    }
}

/**
 * A handle that can be used to join its scoped thread.
 *
 * This struct is created by the [Scope.spawn] method and the
 * [ScopedThreadBuilder.spawn] method.
 */
public class ScopedJoinHandle<T>(
    /**
     * A join handle to the spawned thread.
     */
    public var handle: SharedOption<thread.JoinHandle<Unit>> = SharedOption<thread.JoinHandle<Unit>>(),
    /**
     * Holds the result of the inner closure.
     */
    public var result: SharedOption<T> = SharedOption<T>(),
    /**
     * A handle to the spawned thread.
     */
    public var thread: thread.Thread = thread.Thread(),
    /**
     * Borrows the parent scope with scope `scope`.
     */
    public var _marker: Unit = Unit,
) {
    /**
     * Waits for the thread to finish and returns its result.
     *
     * If the child thread panics, an error is returned. Note that if panics are implemented by
     * aborting the process, no error is returned; see the notes of [std::panic::catch_unwind].
     *
     * # Panics
     *
     * This function may panic on some platforms if a thread attempts to join itself or otherwise
     * may create a deadlock with joining threads.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.thread
     *
     * thread.scope({ s ->
     *     val handle1 = s.spawn({ println("I'm a happy thread :)") })
     *     val handle2 = s.spawn({ error("I'm a sad thread :(") })
     *     // Join the first thread and verify that it succeeded.
     *     val res = handle1.join()
     *     check(res.isOk())
     *     // Join the second thread and verify that it panicked.
     *     val res1 = handle2.join()
     *     check(res1.isErr())
     * }).unwrap()
     * ```
     */
    public fun join(): thread.Result<T> {
        // Take out the handle. The handle will surely be available because the root scope waits
        // for nested scopes before joining remaining threads.
        val handle = this.handle.lock().unwrap().take()!!
        // Join the thread and then take the result out of its inner closure.
        return handle.join().map({ this.result.lock().unwrap().take()!! })
    }

    public fun fmt(f: StringBuilder) {
        f.append("ScopedJoinHandle { .. }")
    }
}

public fun <T> ScopedJoinHandle<T>.as_pthread_t(): RawPthread {
    // Borrow the handle. The handle will surely be available because the root scope waits
    // for nested scopes before joining remaining threads.
    val handle = this.handle.lock().unwrap()
    return handle.unwrap().as_pthread_t()
}

public fun <T> ScopedJoinHandle<T>.into_pthread_t(): RawPthread {
    return this.as_pthread_t()
}
