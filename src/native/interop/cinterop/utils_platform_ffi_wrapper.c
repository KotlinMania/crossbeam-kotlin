#include "utils_platform_ffi_wrapper.h"
#include <errno.h>

#include "crossbeam_platform.h"

uint64_t crossbeam_utils_platform_ffi_inline_0_crossbeam_thread_id(void) {
    return crossbeam_platform_thread_id();
}

void crossbeam_utils_platform_ffi_inline_1_crossbeam_spin_loop(void) {
    crossbeam_platform_spin_loop();
}

void crossbeam_utils_platform_ffi_inline_2_crossbeam_yield_thread(void) {
    crossbeam_platform_yield_thread();
}

