// Project-owned OS monitor binding glue, not upstream Crossbeam source or tests.
// Handles are private opaque resource identities, not a general Rust pointer ABI.
c! {
    #include "crossbeam_monitor.h"
}

pub(crate) fn monitor_create() -> u64 {
    c!([] -> u64 { return crossbeam_monitor_create(); })
}

pub(crate) fn monitor_destroy(handle: u64) -> i32 {
    c!([handle: u64] -> i32 { return crossbeam_monitor_destroy(handle); })
}

pub(crate) fn monitor_lock(handle: u64) -> i32 {
    c!([handle: u64] -> i32 { return crossbeam_monitor_lock(handle); })
}

pub(crate) fn monitor_unlock(handle: u64) -> i32 {
    c!([handle: u64] -> i32 { return crossbeam_monitor_unlock(handle); })
}

pub(crate) fn monitor_wait(handle: u64) -> i32 {
    c!([handle: u64] -> i32 { return crossbeam_monitor_wait(handle); })
}

pub(crate) fn monitor_notify_all(handle: u64) -> i32 {
    c!([handle: u64] -> i32 { return crossbeam_monitor_notify_all(handle); })
}
