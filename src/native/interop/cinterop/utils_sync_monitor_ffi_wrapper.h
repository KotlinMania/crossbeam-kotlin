#ifndef CROSSBEAM_UTILS_SYNC_MONITOR_FFI_UTILS_SYNC_MONITOR_FFI_WRAPPER_H
#define CROSSBEAM_UTILS_SYNC_MONITOR_FFI_UTILS_SYNC_MONITOR_FFI_WRAPPER_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

uint64_t crossbeam_utils_sync_monitor_ffi_inline_0_monitor_create(void);
int32_t crossbeam_utils_sync_monitor_ffi_inline_1_monitor_destroy(uint64_t handle);
int32_t crossbeam_utils_sync_monitor_ffi_inline_2_monitor_lock(uint64_t handle);
int32_t crossbeam_utils_sync_monitor_ffi_inline_3_monitor_unlock(uint64_t handle);
int32_t crossbeam_utils_sync_monitor_ffi_inline_4_monitor_wait(uint64_t handle);
int32_t crossbeam_utils_sync_monitor_ffi_inline_5_monitor_notify_all(uint64_t handle);

#ifdef __cplusplus
}
#endif

#endif /* CROSSBEAM_UTILS_SYNC_MONITOR_FFI_UTILS_SYNC_MONITOR_FFI_WRAPPER_H */
