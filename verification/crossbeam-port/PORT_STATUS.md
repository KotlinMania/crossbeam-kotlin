# Crossbeam port status

Work in progress. Product and original-test sources are generated from upstream commit `2920c984290229ab4e0ca0452ef09e48a82063f3`, then repaired directly in their actual source sets. The prior hand implementation is not retained in product sources.

The latest complete scoped generator pass processed 44 Rust inputs and placed 32 normal artifacts plus 7 inline-test artifacts. The existing regeneration driver replays exact reviewed repairs, including static collector/registry placement, atomics/stamp arithmetic and Steal variants. The real upstream Rand test dependency uses `io.github.kotlinmania:rand-kotlin:0.2.0` and its actual `threadRng`/`randomRange` API.

## Validation

On 2026-11-11, the requested single `./gradlew build` ran with no options and failed after 22 seconds at `:compileKotlinAndroidNativeArm64`. Remaining generated primitive imports and atomic, allocation/pointer, synchronization and thread calls do not resolve. No tests ran in that full build, and later target stages did not pass. The project-local log is `build/verification/generated-seqlock/full-build.log`.

Earlier focused Kotlin/Native 2.4.21 macOS ARM64 validation with `-Werror` and the real platform C bridge passed eight original-source tests: both seq_lock/seq_lock_wide `test_abort` tests, all five `deferred.rs` tests, and the Backoff::spin Rustdoc multiplication example. These focused results are not a full-project pass. The later full regeneration and static repairs have not received a successful complete build.

Deferred stores a Kotlin callback directly and invokes it once. Its upstream on_stack/on_heap assertions check callback behavior, not Kotlin physical storage placement. The independent Android consumer now contains only the original Backoff::spin Rustdoc check, replacing copied unrelated consumer fixtures; that publication consumer has not run.

## Remaining source work

Six Rust inputs still reject explicitly: AtomicCell, WaitGroup, Collector, Guard, Internal and epoch lib.rs. No old hand implementation substitutes for those missing outputs. Other generated modules still need their actual primitive APIs connected and remaining source repairs. CachePadded physical layout has not been established.

## Local preservation and holds

Local Git was initialized before further replacement. Commit `cb6b8aa6d081b545196574010e1de72c7133dace` preserves the initial current tree; `25f46a03696e8ab9c26e9e6637f54bc05693ac6d` preserves regeneration and configuration repairs. Earlier deleted files were not recovered. No remote is configured.

All configured targets and Native/JVM/N-API channels remain enabled. No CI, push, PR, merge, publication or release was performed.

## Direct source follow-up

On 2026-11-11, repaired the generated Deque and ShardedLock accesses to explicitly
unwrap CachePadded payloads, retain shared Kotlin references instead of calling
an absent Arc clone, and use actual Kotlin list size/iteration/reversal APIs.
Removed impossible generic CachePadded defaults and nonexistent From/Display
trait references, and connected CachePadded's debug string to its emitted fmt.
The CachePadded documentation explicitly discloses that physical layout is still
unimplemented. No physical-layout, ownership or concurrency test was weakened.

Exact regeneration patches for these changes were checked by reversing each to
its committed preimage and replaying it to the current source. Python driver
syntax and git whitespace checks passed. No compilation or tests were rerun:
the port is still unfinished and the existing atomics in threadlocal-kotlin and
rust-lang-kotlin are not presently a portable working foundation. See the dated
atomic API audit in RUST_LANG_FOUNDATION_REVIEW.md. The previous full-build failure
and eight focused-test result remain the latest execution evidence.
