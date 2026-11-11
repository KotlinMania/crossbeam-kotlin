# Existing rust-lang-kotlin foundation review

Read-only inspection in response to Sydney identifying `rust-lang-kotlin` as the right shape. No files in that project were modified or copied.

The existing project centralizes shared Rust semantics, with `candidates/core/kotlin/org/rustlang/core`, `candidates/alloc/kotlin/org/rustlang/alloc`, and a separate `runtime` module. Its DESIGN.md explicitly gives foundation correctness priority, and requires a source-backed shared contract rather than repeated ownership/pointer/layout emulations.

Relevant existing source:

- `candidates/core/kotlin/org/rustlang/core/alloc/layout/Layout.kt` stores size and `Alignment`; layout metadata alone does not allocate a physically aligned Kotlin payload.
- `candidates/core/kotlin/org/rustlang/core/sync/atomic/private_impl/Align8_u64.kt` is a Kotlin class containing a Long field, with `RustCopy`; the class declaration itself supplies no native physical alignment guarantee.
- `runtime/src/commonMain/kotlin/org/rustlang/runtime/Pointer.kt` exposes allocation identity, byteOffset, viewSize and exposedAddress. It imports java.lang.invoke, java.lang.reflect and Java concurrent collections/atomics; its `commonMain` path does not make it a portable KMP implementation.
- `runtime/README.md` explicitly identifies the pinned runtime JAR as JVM-specific, with 479 backend/support classes and Java 25 classfiles for some members.
- Root README states that the full generated candidate build has not passed and that source generation counts are not semantic coverage.

No `CachePadded` implementation was found in the existing candidates or runtime source. These are useful canonical foundation contracts and shapes, but do not yet establish a validated portable CachePadded implementation or physical alignment guarantee for this KMP project. Existing raw-pointer/layout support must be evaluated through that foundation; no fake local pointer addresses or padding-field wrapper has been introduced here.

The present managed-reference epoch adaptation remains explicitly distinct from the shared raw Rust ABI. This review does not claim that importing the JVM runtime would repair KMP, native layout or deterministic destruction.

## Follow-up source audit

The allocation path makes the physical-layout limitation concrete. In
`runtime/src/commonMain/kotlin/org/rustlang/runtime/Pointer.kt`,
`allocateBytes` (lines 7175–7194 at inspection) allocates a Kotlin `ByteArray`
and records the requested alignment in allocation metadata.
`allocateAddress` (lines 8851–8862) rounds a monotonically allocated integer
from `NEXT_ADDRESS`; it does not obtain the backing object's physical address.
These facilities can model Rust address identity and offsets, but a successful
modulo/alignment check on those addresses cannot establish cache-line isolation.
They therefore cannot satisfy Crossbeam's `CachePadded` contract or prove that a
Kotlin object is compatible with the raw `Pointable` allocator ABI.

`runtime/src/commonMain/kotlin/org/rustlang/runtime/ThreadSupport.kt` includes
`tlsCreate`, `tlsGet`, `tlsSet`, and an explicit `tlsRunDestructors` dispatcher.
It depends on JVM `ThreadLocal`, `Thread`, and `MethodHandle` facilities. Its
`spawn` cleanup removes the TLS map; the inspected candidate sources do not
establish a portable automatic thread-exit hook for arbitrary caller threads.
The dispatcher alone does not establish the ordered TLS destruction and
reentrant pinning after TLS destruction required by upstream
`crossbeam-epoch/src/default.rs::pin_while_exiting`.

`runtime/src/commonMain/kotlin/org/rustlang/runtime/RustDrop.kt` provides an
explicit `rustDrop()` interface. Pointer's drop-glue helpers are useful shared
contracts, but neither that interface nor garbage collection inserts implicit
Kotlin scope/field destruction. Crossbeam continues to use explicit owners,
guards and disposer callbacks rather than claiming that implicit Rust Drop is
implemented.

No shared-foundation source was changed, no synthetic address or managed padding
wrapper was added, and none of the upstream physical-layout or TLS-exit tests
was replaced with a weaker metadata test.

## Atomic API audit, 2026-11-11

Inspected the existing atomics in both dependency projects before attempting imports.
`threadlocal-kotlin/src/common/commonMain/kotlin/io/github/kotlinmania/threadlocal/atomics/Atomic.kt`
contains `Atomic<T : AtomicPrimitive>` whose load, store and swap read and write the
ordinary mutable `UnsafeCell` from `compat/Compat.kt`. They do not synchronize.
`AtomicInt.kt` starts with an unmatched closing brace and a non-Kotlin `when order`
expression; it supplies no usable integer atomic implementation. `AtomicBool.kt`
uses `typealias AtomicBool = Atomic<Boolean>` despite that marker bound and contains
unlowered Rust syntax. `compat/Compat.kt` explicitly contains a no-op atomicStore
and throwing intrinsic functions. These files cannot safely replace Crossbeam's
atomic operations merely by changing imports.

The generated `rust-lang-kotlin/candidates/core/kotlin/org/rustlang/core/sync/atomic/Atomic_u64.kt`
provides `load(Ordering?)`, returning `Long`, through `AtomicModule` and runtime
`Pointer.receiverCellAligned`. It is not the emitted Crossbeam `AtomicU64.new` /
`compare_exchange` API. The dependency's `runtime/build.gradle.kts` applies the JVM
plugin, requires Java 25 and a pinned JVM runtime JAR. Runtime Pointer imports
`java.lang.invoke`, reflection and Java concurrent atomics. Wiring that runtime
into commonMain would violate the required Native/JS/Wasm target surface.

No dependency source was modified. The generated Crossbeam dereference and
collection repairs do not implement or replace these missing foundational APIs.
The remaining dependency blocker is a real portable atomic/pointer/synchronization
implementation, not locating the files. A full build is not rerun while those
known missing source dependencies remain unresolved.
