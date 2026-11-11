// Project-owned bridge to crossbeam-kotlin's canonical C platform primitives.
// This is binding glue, not translated upstream Crossbeam source or tests.
c! {
    #include "crossbeam_platform.h"
}

pub(crate) fn crossbeam_thread_id() -> u64 {
    c!([] -> u64 { return crossbeam_platform_thread_id(); })
}

pub(crate) fn crossbeam_spin_loop() {
    c!([] { crossbeam_platform_spin_loop(); });
}

pub(crate) fn crossbeam_yield_thread() {
    c!([] { crossbeam_platform_yield_thread(); });
}
