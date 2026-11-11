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

#include "../../../native/interop/cinterop/crossbeam_monitor.h"

static uint64_t crossbeam_utils_sync_monitor_ffi_inline_0_monitor_create(void) {
    return crossbeam_monitor_create();
}

JNIEXPORT jlong JNICALL Java_io_github_kotlinmania_crossbeam_utils_sync_monitor_ffi_UtilsSyncMonitorFfiJniBridge_crossbeam_1utils_1sync_1monitor_1ffi_1inline_10_1monitor_1create(JNIEnv* km_env, jclass km_clazz) {
    (void)km_env;
    (void)km_clazz;
    uint64_t km_res = crossbeam_utils_sync_monitor_ffi_inline_0_monitor_create();
    return (jlong)km_res;
}

static int32_t crossbeam_utils_sync_monitor_ffi_inline_1_monitor_destroy(uint64_t handle) {
    return crossbeam_monitor_destroy(handle);
}

JNIEXPORT jint JNICALL Java_io_github_kotlinmania_crossbeam_utils_sync_monitor_ffi_UtilsSyncMonitorFfiJniBridge_crossbeam_1utils_1sync_1monitor_1ffi_1inline_11_1monitor_1destroy(JNIEnv* km_env, jclass km_clazz, jlong handle) {
    (void)km_env;
    (void)km_clazz;
    int32_t km_res = crossbeam_utils_sync_monitor_ffi_inline_1_monitor_destroy((uint64_t)handle);
    return (jint)km_res;
}

static int32_t crossbeam_utils_sync_monitor_ffi_inline_2_monitor_lock(uint64_t handle) {
    return crossbeam_monitor_lock(handle);
}

JNIEXPORT jint JNICALL Java_io_github_kotlinmania_crossbeam_utils_sync_monitor_ffi_UtilsSyncMonitorFfiJniBridge_crossbeam_1utils_1sync_1monitor_1ffi_1inline_12_1monitor_1lock(JNIEnv* km_env, jclass km_clazz, jlong handle) {
    (void)km_env;
    (void)km_clazz;
    int32_t km_res = crossbeam_utils_sync_monitor_ffi_inline_2_monitor_lock((uint64_t)handle);
    return (jint)km_res;
}

static int32_t crossbeam_utils_sync_monitor_ffi_inline_3_monitor_unlock(uint64_t handle) {
    return crossbeam_monitor_unlock(handle);
}

JNIEXPORT jint JNICALL Java_io_github_kotlinmania_crossbeam_utils_sync_monitor_ffi_UtilsSyncMonitorFfiJniBridge_crossbeam_1utils_1sync_1monitor_1ffi_1inline_13_1monitor_1unlock(JNIEnv* km_env, jclass km_clazz, jlong handle) {
    (void)km_env;
    (void)km_clazz;
    int32_t km_res = crossbeam_utils_sync_monitor_ffi_inline_3_monitor_unlock((uint64_t)handle);
    return (jint)km_res;
}

static int32_t crossbeam_utils_sync_monitor_ffi_inline_4_monitor_wait(uint64_t handle) {
    return crossbeam_monitor_wait(handle);
}

JNIEXPORT jint JNICALL Java_io_github_kotlinmania_crossbeam_utils_sync_monitor_ffi_UtilsSyncMonitorFfiJniBridge_crossbeam_1utils_1sync_1monitor_1ffi_1inline_14_1monitor_1wait(JNIEnv* km_env, jclass km_clazz, jlong handle) {
    (void)km_env;
    (void)km_clazz;
    int32_t km_res = crossbeam_utils_sync_monitor_ffi_inline_4_monitor_wait((uint64_t)handle);
    return (jint)km_res;
}

static int32_t crossbeam_utils_sync_monitor_ffi_inline_5_monitor_notify_all(uint64_t handle) {
    return crossbeam_monitor_notify_all(handle);
}

JNIEXPORT jint JNICALL Java_io_github_kotlinmania_crossbeam_utils_sync_monitor_ffi_UtilsSyncMonitorFfiJniBridge_crossbeam_1utils_1sync_1monitor_1ffi_1inline_15_1monitor_1notify_1all(JNIEnv* km_env, jclass km_clazz, jlong handle) {
    (void)km_env;
    (void)km_clazz;
    int32_t km_res = crossbeam_utils_sync_monitor_ffi_inline_5_monitor_notify_all((uint64_t)handle);
    return (jint)km_res;
}

