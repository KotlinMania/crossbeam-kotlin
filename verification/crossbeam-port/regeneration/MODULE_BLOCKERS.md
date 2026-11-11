# Current emitted-source boundaries

The old manual algorithm/test implementations were deleted. This is actual native-kexe output in real source paths, not staged-only files. No source snapshots or handwritten substitutes remain. The authoritative current commands, raw hashes and repair diffs are in redo-adoption.json.

## Current profile and artifacts

Native debug SHA 4e882c692974abb057b6892486ab7503b71f80428e36f21deef029e596efb192. Raw ABI mode and target cfg printed by the existing Rust compiler are explicit. There are 32 normal source/standalone-test/example/benchmark artifacts and 7 inline-test artifacts, with 28 inline declarations. All 127 emitted test names have original Rust direct-function or macro-invocation matches; this is not body-equivalence or execution proof. Rustdoc executable-test emission is not supported, and no manual replacement was invented.

Raw ABI mode corrected the earlier invocation's lossy pointer representation: addresses are ULong/raw Long, and hashCode address substitutes are absent. This does not provide a real allocator, ownership runtime or physical layout.

## Explicit refusal, not manual substitutes

AtomicCell, WaitGroup, Collector, Guard and Internal currently reject for unsupported destructor suppression/ownership. Epoch lib.rs rejects the core UnsafeCell/local wrapper shadow-recursion defect. Stale files and test artifacts for those rejected inputs were removed. Inactive Loom now emits nothing because crate cfg is respected. Rejection reasons are recorded verbatim in redo-compile-summary.json.

## Genuine remaining implementation work

- Shared Rust atomics and ordering/consume APIs.
- Actual allocation/deallocation, pointer arithmetic, MaybeUninit, volatile access and ownership/destruction.
- Arc, Mutex, Condvar, Once, scoped thread and TLS-exit semantics.
- Associated-type arity, constFn macro lowering, typed Result and numeric-width errors.
- Persistent storage for block-local Rust static values, including the default collector.
- Real CachePadded physical alignment/embedded payload layout; source comments are not an implementation.

Current metadata compilation fails with 1,478 diagnostics. No current tests ran. All earlier passing XML/build reports are historical, tied to removed source. No target, FFI flag, validator or warning policy was weakened.

The scoped direct pass processes 44 Rust src/tests/examples/benches inputs in utils/epoch/deque. The three Cargo build.rs files are host build tooling and are not silently counted as ported runtime modules.
