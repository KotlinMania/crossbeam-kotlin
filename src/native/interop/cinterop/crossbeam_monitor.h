/* Project-owned OS mutex/condition-variable glue. The WaitGroup algorithm stays in Kotlin. */
#ifndef CROSSBEAM_MONITOR_H
#define CROSSBEAM_MONITOR_H
#include <stdint.h>
#include <stdlib.h>
#ifdef _WIN32
#include <windows.h>
#else
#include <pthread.h>
#endif

/* This private token owns exactly one monitor resource. It is not a general Rust pointer ABI. */
typedef struct crossbeam_monitor {
#ifdef _WIN32
    SRWLOCK mutex;
    CONDITION_VARIABLE condition;
#else
    pthread_mutex_t mutex;
    pthread_cond_t condition;
#endif
} crossbeam_monitor;

static inline uint64_t crossbeam_monitor_create(void) {
    crossbeam_monitor *monitor = (crossbeam_monitor *)malloc(sizeof(crossbeam_monitor));
    if (monitor == NULL) return 0;
#ifdef _WIN32
    InitializeSRWLock(&monitor->mutex);
    InitializeConditionVariable(&monitor->condition);
#else
    int status = pthread_mutex_init(&monitor->mutex, NULL);
    if (status != 0) {
        free(monitor);
        return 0;
    }
    status = pthread_cond_init(&monitor->condition, NULL);
    if (status != 0) {
        (void)pthread_mutex_destroy(&monitor->mutex);
        free(monitor);
        return 0;
    }
#endif
    return (uint64_t)(uintptr_t)monitor;
}

static inline int32_t crossbeam_monitor_destroy(uint64_t handle) {
    crossbeam_monitor *monitor = (crossbeam_monitor *)(uintptr_t)handle;
#ifndef _WIN32
    int status = pthread_cond_destroy(&monitor->condition);
    if (status != 0) return (int32_t)status;
    status = pthread_mutex_destroy(&monitor->mutex);
    if (status != 0) return (int32_t)status;
#endif
    free(monitor);
    return 0;
}

static inline int32_t crossbeam_monitor_lock(uint64_t handle) {
    crossbeam_monitor *monitor = (crossbeam_monitor *)(uintptr_t)handle;
#ifdef _WIN32
    AcquireSRWLockExclusive(&monitor->mutex);
    return 0;
#else
    return (int32_t)pthread_mutex_lock(&monitor->mutex);
#endif
}

static inline int32_t crossbeam_monitor_unlock(uint64_t handle) {
    crossbeam_monitor *monitor = (crossbeam_monitor *)(uintptr_t)handle;
#ifdef _WIN32
    ReleaseSRWLockExclusive(&monitor->mutex);
    return 0;
#else
    return (int32_t)pthread_mutex_unlock(&monitor->mutex);
#endif
}

static inline int32_t crossbeam_monitor_wait(uint64_t handle) {
    crossbeam_monitor *monitor = (crossbeam_monitor *)(uintptr_t)handle;
#ifdef _WIN32
    return SleepConditionVariableSRW(&monitor->condition, &monitor->mutex, INFINITE, 0)
        ? 0 : (int32_t)GetLastError();
#else
    return (int32_t)pthread_cond_wait(&monitor->condition, &monitor->mutex);
#endif
}

static inline int32_t crossbeam_monitor_notify_all(uint64_t handle) {
    crossbeam_monitor *monitor = (crossbeam_monitor *)(uintptr_t)handle;
#ifdef _WIN32
    WakeAllConditionVariable(&monitor->condition);
    return 0;
#else
    return (int32_t)pthread_cond_broadcast(&monitor->condition);
#endif
}
#endif
