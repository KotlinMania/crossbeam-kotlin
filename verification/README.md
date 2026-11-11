# Crossbeam verification

The project enables Native cinterop, JVM JNI and Node N-API bindings. Its Gradle build retains every configured multiplatform target, warning policy and Swift validation task.

Run the full project build from the project root with `./gradlew build`. No publication is implied by a build.

## Independent Android consumer

`android-consumer` resolves `io.github.kotlinmania:crossbeam-kotlin` as a published dependency. It does not substitute project source or silently use Maven Local. Its only behavioral test is the original upstream `crossbeam-utils/src/backoff.rs` Backoff::spin Rustdoc example, retaining the assertions that multiplication returns 7 and stores 56.

`crossbeamVersion` defaults to the current project version, 0.1.1. `consumerCompileSdk` defaults to 34. An explicit `crossbeamRepository` may point to an already prepared publication repository. The consumer verifies root-to-Android AAR selection, records compile/runtime classpaths, and requires its original-source test to execute without skips or failures.

Run `./gradlew -p verification/android-consumer verifyAndroidConsumer` when the requested publication is available. This consumer has not been executed against the current unfinished port.

The copied unrelated Async Trait and Android System Properties fixture was replaced with this Crossbeam-specific original-source check.

## Existing shared helpers

`workspace-sync-crossbeam.sh` runs the workspace's Android publication planner against this project. It does not build, publish or authorize changes to another project.

`ALL_TARGET_VALIDATION.md` describes host constraints and cross-target runtime requirements. `crossbeam-port/PORT_STATUS.md` states which current-source checks actually ran. Historical receipts are not evidence that later regenerated source passed.
