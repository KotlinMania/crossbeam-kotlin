/* Crossbeam platform primitives. Keep OS thread yielding separate from CPU hints. */
#ifndef CROSSBEAM_PLATFORM_H
#define CROSSBEAM_PLATFORM_H
#include <stdint.h>
#ifdef _WIN32
#include <windows.h>
#ifdef _MSC_VER
#include <intrin.h>
#endif
#else
#include <pthread.h>
#include <sched.h>
#endif
static inline uint64_t crossbeam_platform_thread_id(void) {
#ifdef _WIN32
 return (uint64_t)GetCurrentThreadId();
#else
 return (uint64_t)(uintptr_t)pthread_self();
#endif
}
static inline void crossbeam_platform_spin_loop(void) {
#if defined(_MSC_VER) && defined(_M_ARM64)
 __isb(0xF);
#elif defined(_MSC_VER) && defined(_M_X64)
 _mm_pause();
#elif defined(__aarch64__)
 /* Rust core::hint::spin_loop uses an instruction synchronization barrier on AArch64. */
 __asm__ __volatile__("isb sy" ::: "memory");
#elif defined(__x86_64__)
 __asm__ __volatile__("pause" ::: "memory");
#else
 __asm__ __volatile__("" ::: "memory");
#endif
}
static inline void crossbeam_platform_yield_thread(void) {
#ifdef _WIN32
 (void)SwitchToThread();
#else
 (void)sched_yield();
#endif
}
#endif
