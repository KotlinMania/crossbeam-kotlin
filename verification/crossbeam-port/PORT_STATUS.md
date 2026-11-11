# Crossbeam emitted-source redo status

Work in progress. The manual Crossbeam algorithms/tests were removed. Current product/test files come from actual native-kexe output plus recorded necessary repairs. The full source does not compile. Eight original Rust-derived tests now compile and pass in focused Kotlin/Native checks (2026-11-11). No complete port, usable Rayon prerequisite, physical-layout or API-equivalence claim is made.

## Exact current operations

- Removed 55 manually adapted files, including old epoch/deque implementations, manual test/scoping helpers and the hand-transcribed Swift test. No source snapshots or manual substitutes remain.
- Regenerated all 44 scoped Rust src/test/example/benchmark inputs with verified native debug kexe SHA 4e882c692974abb057b6892486ab7503b71f80428e36f21deef029e596efb192. Raw ABI mode and actual compiler-reported target cfg are now explicit. The original upstream clone remains clean at 2920c984290229ab4e0ca0452ef09e48a82063f3.
- Native output was written directly into real source paths: 32 normal artifacts and 7 inline-test artifacts containing 28 original Rust declarations. The 127 total emitted test declarations have direct Rust function/macro name-origin matches; that is not runtime or body-equivalence proof.
- Raw mode preserves ULong pointer addresses and removes the former default-mode hashCode address substitutions. Real allocation/ownership APIs are still missing.
- Newly rejected files and stale test artifacts were removed. Inactive Loom emits no source. No old manual implementation is retained for a rejected file.
- Generated FFI companions, real OS/C/Rust bridge inputs and platform/loaders remain. All template targets/validators and native/JVM/N-API flags remain enabled. The three Cargo build.rs files are host tooling, not silently counted as translated runtime modules.

Commands, raw hashes, exact repair diffs and removals: regeneration/redo-adoption.json. Reproducible driver: regeneration/regenerate.py. Original test name/macro evidence: regeneration/test-origin-map.json.

## Current validation and blockers

The last full metadata compilation failed. Full-project compilation has not been rerun after the current focused repairs. Historical full-suite receipts still describe removed source, not current acceptance.

Current focused validation: Kotlin/Native 2.4.21, macOS ARM64, -Werror, using the real platform C bridge. Both original seq_lock/seq_lock_wide test_abort tests pass, as do all five original deferred.rs tests and the original Backoff::spin Rustdoc multiplication example. Generated SeqLock atomics and wrapping arithmetic were repaired; the abort path now prevents later close from incrementing the stamp. Deferred stores the Kotlin callback directly and consumes it once. Its upstream on_stack/on_heap assertions test callback behavior, not Kotlin physical storage placement. Subsequent file-static collector/registry and epoch-arithmetic repairs were inspected but not compiled. No whole-project or layout-equivalence pass is claimed.

Six inputs currently reject explicitly: AtomicCell, WaitGroup, Collector, Guard, Internal and epoch lib.rs. Reasons are recorded verbatim in regeneration/redo-compile-summary.json. These require genuine destructor/ownership or core UnsafeCell type resolution, not discarded semantics or local stand-ins.

Other emitted modules still need canonical Rust atomics, allocation/Box/pointers/MaybeUninit, Arc/Mutex/Condvar/Once, TLS/destruction, associated-type and typed Result semantics. CachePadded's comments do not supply physical alignment. The block-local Rust static collector is not faithfully lowered. Rustdoc examples are not automatically emitted as executable tests, and no manual replacements were invented. Source/API/body audit remains necessary even after compilation eventually succeeds.

## Holds

No CI, push, PR, merge, local publication or public release. New portable foreign JDK/Node download/execution remains held. Foreign payloads and previous XML counts are not current-source runtime proof.
