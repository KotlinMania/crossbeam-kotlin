# JVM JNI Bindings

This directory holds the C source for the JVM-native bridge. It is only
active when `project.ffi.jvm=true` in `../../../../gradle.properties`.

## What's here

```
src/jvm/jvmMain/c/
└── <lowername>_jni.c   — JNI implementations, built into lib<lowername>_jni.{dylib,so,dll}
```

The matching Kotlin bridge class is
`io.github.kotlinmania.crossbeam.internal.FfiJni`, an `internal object`
declaring `external fun`s with `@JvmStatic`. The build wires the compiled
library into `jvmTest`'s `java.library.path`, so `System.loadLibrary(...)`
in the bridge's `init` resolves without any environment setup.

## How the wiring works

`../../../../build.gradle.kts`'s `if (jvmFfiEnabled)` block registers
`compile<Lib>Jni`, which:

1. Compiles `<lowername>_jni.c` with the host `clang` (`-shared -O2`,
   plus `-fPIC` on non-Windows) against the JDK's JNI headers
   (`$JAVA_HOME/include` plus the platform-specific subdirectory).
2. Emits `build/natives/lib<lowername>_jni.{dylib,so,dll}` with the
   platform-correct prefix and extension.
3. `tasks.named<Test>("jvmTest")` adds a dependency on the compile task and
   sets `-Djava.library.path=build/natives`, so the library resolves at
   runtime without `DYLD_LIBRARY_PATH`/`LD_LIBRARY_PATH` edits.

The task is included in `fullTargetBuildTaskNames` when the gate is on, so
a broken JNI compile fails `./gradlew build` loudly.

## The JNI symbol-name coupling (read this before renaming anything)

The C function names here must match the JVM's JNI mangling convention:
`Java_<package_with_underscores>_<ClassName>_<methodName>`. The Kotlin
bridge class and the C symbols are therefore coupled across two dimensions
that Gradle cannot validate:

- If you rename the Kotlin bridge class (`FfiJni` → `LibcJni`), every
  JNIEXPORT symbol here must change the class segment to match.
- If you change the Kotlin package (e.g. from `...crossbeam.internal` to
  `...libc.internal`), the package segment of every symbol must change.

Getting either wrong compiles cleanly on both sides and fails only at
`jvmTest` with `java.lang.UnsatisfiedLinkError: ...FfiJni.threadId()`. When
you see that error, this file's symbol names vs the Kotlin bridge class
are the first place to look.

This project default pairs:

- C symbol: `Java_io_github_kotlinmania_crossbeam_internal_FfiJni_threadId`
- Kotlin: `internal object FfiJni` in `io.github.kotlinmania.crossbeam.internal`
  declaring `@JvmStatic external fun threadId(): Long`

## Keeping the bridge invisible to Swift Export

The `${Lib}Jni` object is `internal` deliberately. Per the SWIFT.md §9
note ("internal expect fun symbols leak into the Swift Export bridge"),
public external surfaces on the JVM side can be bridged into the Apple
framework's Swift output, which is wrong — Swift consumers never need JNI.
`internal` keeps the bridge Kotlin/JVM-only.

For the same reason, do NOT name this file with the same stem as a
commonMain expect file (SWIFT.md §"JVM class name clashes"): both source
sets would emit `FooKt` on JVM and the build would fail with a duplicate
class error. `<Lib>Jni.kt` inside `internal/` is enough separation.

## Adding a function

1. Add the `JNIEXPORT` definition here, mangled to match the Kotlin side.
2. Add `@JvmStatic external fun <name>(...)` to the `<Lib>Jni` object.
3. No registration step — the JVM resolves `Java_..._<name>` by name on
   first call.

Use fixed-width JNI types for parameters and returns (`jint`, `jlong`,
`jstring`, `jbyteArray`). There is no pointer-sized primitive in the JNI
type set, so C pointers cross as `jlong` handles. The libc-kotlin version
of this file
(`kotlinmania/libc-kotlin/src/jvmMain/c/libc_jni.c`) is the worked example
for every common edge case: string conversion with `GetStringUTFChars` /
`ReleaseStringUTFChars`, `ByteArray` ↔ `void*` bridging, Windows-specific
replacements, and the per-OS `#ifdef _WIN32` pattern.

## Crossbeam generated bindings

`crossbeam_utils_platform_ffi_jni.c` is generated from the project-owned
`src/native/interop/rust/platform_inline.rs` input described in the native
cinterop README. It uses direct inline-C calls to the canonical platform
header, rather than looking up project-private symbols in the process or CRT.
The generated JVM and Android actuals use the configured `ffi_jni` library.

The JVM `FfiNativeLibrary` loader first accepts an installed library on
`java.library.path`. Otherwise, it reads the exact OS/architecture payload from
the JAR's `io/github/kotlinmania/crossbeam/native/` resource directory and
extracts it into a unique private directory under the user's cache. It never
compiles a runtime replacement or overwrites another library version.
The original-derived JVM suite is also run from the packaged JAR with an empty
native search path to exercise this delivery route.
