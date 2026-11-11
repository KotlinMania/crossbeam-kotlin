# Current emitted caller paths

This source redo does not compile and is not a usable Rayon prerequisite. The manual algorithms/tests were deleted, then actual native-kexe source/test output was written directly into real paths. No crate re-export facade or handwritten substitute was supplied.

- Deque emitted declarations are in io.github.kotlinmania.crossbeam.deque, Deque.kt. Real allocation/atomic/pointer APIs are unresolved.
- Epoch Atomic/Owned/Shared are emitted under io.github.kotlinmania.crossbeam.epoch.atomic. Collector/Guard/Internal and the UnsafeCell module-root implementation are currently absent because the translator explicitly rejects unsupported semantics.
- Backoff is io.github.kotlinmania.crossbeam.utils.backoff.Backoff, emitted-first with narrow recorded repairs. Raw ABI identifiers otherwise retain source naming; do not assume the former manual API exists.
- WaitGroup and AtomicCell are absent after current explicit rejection. CachePadded emission is not a validated physical-layout implementation.

Do not integrate callers as if these were working dependencies. See verification/crossbeam-port/regeneration/redo-adoption.json and redo-compile-summary.json. Current tests are original Rust emission, not old manual bodies, and have not executed because product compilation fails.
