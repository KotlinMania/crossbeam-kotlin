#ifndef CROSSBEAM_UTILS_PLATFORM_FFI_UTILS_PLATFORM_FFI_WRAPPER_H
#define CROSSBEAM_UTILS_PLATFORM_FFI_UTILS_PLATFORM_FFI_WRAPPER_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

uint64_t crossbeam_utils_platform_ffi_inline_0_crossbeam_thread_id(void);
void crossbeam_utils_platform_ffi_inline_1_crossbeam_spin_loop(void);
void crossbeam_utils_platform_ffi_inline_2_crossbeam_yield_thread(void);

#ifdef __cplusplus
}
#endif

#endif /* CROSSBEAM_UTILS_PLATFORM_FFI_UTILS_PLATFORM_FFI_WRAPPER_H */
