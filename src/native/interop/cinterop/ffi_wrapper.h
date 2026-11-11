#ifndef CROSSBEAM_FFI_WRAPPER_H
#define CROSSBEAM_FFI_WRAPPER_H
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
uint64_t crossbeam_thread_id(void);
void crossbeam_spin_loop(void);
void crossbeam_yield_thread(void);
#ifdef __cplusplus
}
#endif
#endif
