# crossbeam-kotlin

Local Kotlin Multiplatform port of [Crossbeam](https://github.com/crossbeam-rs/crossbeam).

Upstream is checked out under `tmp/crossbeam` at `2920c984290229ab4e0ca0452ef09e48a82063f3`, the source commit recorded by Rayon's locked crossbeam-utils 0.8.23, crossbeam-epoch 0.9.21 and crossbeam-deque 0.8.8 crates.

This project uses the shared KotlinMania build infrastructure. Copied Git history was removed before local development. A new local Git history was initialized on 2026-11-11 to preserve the current project before further replacement. That snapshot cannot recover files deleted before initialization. No project remote or publication is configured by this work.

This port is in progress. Generation alone is not validation. See `verification/crossbeam-port/PORT_STATUS.md` for checks and remaining semantic gaps. Do not use it as a completed Rayon dependency or publish it before acceptance.

The original upstream README is preserved as `UPSTREAM_README.md`. Source and translated documentation remain under the original MIT OR Apache-2.0 licenses in `LICENSE-MIT` and `LICENSE-APACHE`.
