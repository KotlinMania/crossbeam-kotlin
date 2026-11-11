#include <jni.h>
#include "../../../native/interop/cinterop/crossbeam_platform.h"
JNIEXPORT jlong JNICALL Java_io_github_kotlinmania_crossbeam_internal_FfiJni_threadId(JNIEnv *env, jclass cls) {
 (void)env; (void)cls; return (jlong)crossbeam_platform_thread_id();
}
JNIEXPORT void JNICALL Java_io_github_kotlinmania_crossbeam_internal_FfiJni_spinLoop(JNIEnv *env, jclass cls) {
 (void)env; (void)cls; crossbeam_platform_spin_loop();
}
JNIEXPORT void JNICALL Java_io_github_kotlinmania_crossbeam_internal_FfiJni_yieldThread(JNIEnv *env, jclass cls) {
 (void)env; (void)cls; crossbeam_platform_yield_thread();
}
