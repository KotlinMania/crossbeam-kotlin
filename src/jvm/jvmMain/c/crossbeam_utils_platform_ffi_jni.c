#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <jni.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <errno.h>
#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#else
#include <dlfcn.h>
#ifndef RTLD_DEFAULT
#define RTLD_DEFAULT ((void*)0)
#endif
#endif

#include "../../../native/interop/cinterop/crossbeam_platform.h"

static uint64_t crossbeam_utils_platform_ffi_inline_0_crossbeam_thread_id(void) {
    return crossbeam_platform_thread_id();
}

JNIEXPORT jlong JNICALL Java_io_github_kotlinmania_crossbeam_utils_platform_ffi_UtilsPlatformFfiJniBridge_crossbeam_1utils_1platform_1ffi_1inline_10_1crossbeam_1thread_1id(JNIEnv* km_env, jclass km_clazz) {
    (void)km_env;
    (void)km_clazz;
    uint64_t km_res = crossbeam_utils_platform_ffi_inline_0_crossbeam_thread_id();
    return (jlong)km_res;
}

static void crossbeam_utils_platform_ffi_inline_1_crossbeam_spin_loop(void) {
    crossbeam_platform_spin_loop();
}

JNIEXPORT void JNICALL Java_io_github_kotlinmania_crossbeam_utils_platform_ffi_UtilsPlatformFfiJniBridge_crossbeam_1utils_1platform_1ffi_1inline_11_1crossbeam_1spin_1loop(JNIEnv* km_env, jclass km_clazz) {
    (void)km_env;
    (void)km_clazz;
    crossbeam_utils_platform_ffi_inline_1_crossbeam_spin_loop();
}

static void crossbeam_utils_platform_ffi_inline_2_crossbeam_yield_thread(void) {
    crossbeam_platform_yield_thread();
}

JNIEXPORT void JNICALL Java_io_github_kotlinmania_crossbeam_utils_platform_ffi_UtilsPlatformFfiJniBridge_crossbeam_1utils_1platform_1ffi_1inline_12_1crossbeam_1yield_1thread(JNIEnv* km_env, jclass km_clazz) {
    (void)km_env;
    (void)km_clazz;
    crossbeam_utils_platform_ffi_inline_2_crossbeam_yield_thread();
}

