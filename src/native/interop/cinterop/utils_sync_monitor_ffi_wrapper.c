#include "utils_sync_monitor_ffi_wrapper.h"
#include <errno.h>

#include "crossbeam_monitor.h"

uint64_t crossbeam_utils_sync_monitor_ffi_inline_0_monitor_create(void) {
    return crossbeam_monitor_create();
}

int32_t crossbeam_utils_sync_monitor_ffi_inline_1_monitor_destroy(uint64_t handle) {
    return crossbeam_monitor_destroy(handle);
}

int32_t crossbeam_utils_sync_monitor_ffi_inline_2_monitor_lock(uint64_t handle) {
    return crossbeam_monitor_lock(handle);
}

int32_t crossbeam_utils_sync_monitor_ffi_inline_3_monitor_unlock(uint64_t handle) {
    return crossbeam_monitor_unlock(handle);
}

int32_t crossbeam_utils_sync_monitor_ffi_inline_4_monitor_wait(uint64_t handle) {
    return crossbeam_monitor_wait(handle);
}

int32_t crossbeam_utils_sync_monitor_ffi_inline_5_monitor_notify_all(uint64_t handle) {
    return crossbeam_monitor_notify_all(handle);
}

