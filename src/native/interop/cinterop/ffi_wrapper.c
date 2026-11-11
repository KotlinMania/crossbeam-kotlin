#include "ffi_wrapper.h"
#include "crossbeam_platform.h"
uint64_t crossbeam_thread_id(void) { return crossbeam_platform_thread_id(); }
void crossbeam_spin_loop(void) { crossbeam_platform_spin_loop(); }
void crossbeam_yield_thread(void) { crossbeam_platform_yield_thread(); }
