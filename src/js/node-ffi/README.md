# Crossbeam N-API platform bindings

This addon provides the real OS-thread identity, CPU spin hint, and scheduler
thread yield used by Crossbeam's Kotlin/JS and Kotlin/WasmJS ports on Node.js.
It uses the same `src/native/interop/cinterop/crossbeam_platform.h` primitives
as Native cinterop and JVM/Android JNI. There is no getpid example or queue
implementation in this addon.

## Generation and integration

`src/ffi_bindings.cpp` is the complete N-API companion emitted by the workspace's
Native `rust-kotlin.kexe` from the project-owned
`src/native/interop/rust/platform_inline.rs` inline-C bridge declarations. Those
declarations are platform glue, not upstream Crossbeam Rust source or tests.
The upstream Crossbeam modules themselves do not declare these C bindings.

From the project root, generate the full companion group for review:

```sh
../rust-kotlin/build/bin/macosArm64/releaseExecutable/rust-kotlin.kexe translate \
  --force --raw-abi --port-lint none \
  --package io.github.kotlinmania.crossbeam.utils.platform.ffi \
  --namespace io.github.kotlinmania.crossbeam \
  --target-arch aarch64 --target-os macos --target-family unix --target-vendor apple \
  src/native/interop/rust/platform_inline.rs \
  build/verification/platform-inline-generated/src/common/commonMain/kotlin/io/github/kotlinmania/crossbeam/utils/platform/ffi/Platform.kt
```

Review the complete generated files against the integrated sources rather than
blindly overwriting manual integration repairs. The N-API C++ companion is
retained byte-for-byte at the template's `src/ffi_bindings.cpp` location. The
JS/WasmJS companions retain their generated calls and unsigned-decimal return
conversion. Their loaders are adapted to the template's `Ffi` naming,
`@kotlinmania/ffi-native-bindings` package, and `dist` layout. They propagate a
broken installed addon's load error instead of silently selecting a stale
binary. The project-local source fallback is restricted to this addon's
`dist/index.js`, not the template's old build directory.

`binding.gyp` adds the canonical C header directory. The template Java
`NodeFfiBuilder` detects the generated registrar chain and supplies the module
entry point; no handwritten N-API exports replace the generated ones.

## Building

All three channels are configured independently in `gradle.properties`:
`project.ffi.native`, `project.ffi.jvm`, and `project.ffi.napi`.

```sh
./gradlew buildNodeFfi
./gradlew jsNodeTest wasmJsNodeTest
```

The template builder installs `node-addon-api` headers with package scripts
disabled, uses Gradle's pinned Node executable and matching verified headers,
and builds `dist/build/{Debug,Release}/ffi_native.node`. Each successful build
stages its payload under `dist/platform/<node-platform>-<arch>/<configuration>/`
using the Node platform and architecture recorded by the builder, rather than
those of the Gradle daemon. `copyNodeFfi` and `copyWasmNodeFfi` place this package
in the corresponding test runtime's `node_modules`. Build artifacts and temporary files stay in the owning project.
The addon must be built for the Node process's host OS and architecture. The
loader only selects `platform/<process.platform>-<process.arch>/Release/ffi_native.node`
or the same platform's `Debug` payload when Release is absent. It never falls
back to the build host's unqualified `build/` binary. A missing platform produces
an explicit error naming the required paths; an existing broken binary's load
error is preserved.

## Runtime contract

- JS and WasmJS **on Node.js** call the generated N-API functions. Missing or
  broken native bindings are errors; they do not silently become no-ops.
- The `uint64_t` thread identity crosses JavaScript as an unsigned decimal
  string. Kotlin converts it to `ULong`, then preserves the same 64 bits in
  `Long`. It is an OS identity valid for the current thread's lifetime, not a
  process-global, never-reused Rust `ThreadId`.
- In **browsers**, N-API is unavailable. The configured Kotlin runtime is
  single-agent, with agent-local identity. Its spin/yield behavior matches
  Rust's unsupported wasm backend: `library/core/src/hint.rs` has no wasm
  spin instruction and `library/std/src/sys/thread/unsupported.rs`
  implements `yield_now` as a no-op. Synchronous calls do not yield to the
  browser event loop, create threads, or enable shared Kotlin objects across
  workers.
- Standalone **WASI** cannot load Node addons. It has a separate source set
  and uses WASI imports for supported host operations, not this package.

These platform bindings do not establish complete Crossbeam/Rayon port parity.
Acceptance still requires unchanged upstream tests and validation on each
configured target's real runtime.

The separate `crossbeam_utils_sync_monitor_ffi_bindings.cpp` registrar is generated from
`src/native/interop/rust/monitor_inline.rs`. It exposes only the real OS mutex/condition-variable
primitives in `crossbeam_monitor.h`; the WaitGroup algorithm remains Kotlin source.
JS/Wasm WaitGroup uses the documented no-thread monitor, not a simulated blocking primitive.
