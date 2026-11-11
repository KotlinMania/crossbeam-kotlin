package io.github.kotlinmania.crossbeam.internal

/** JNI backend for the same OS primitives used by the Kotlin/Native port. */
internal object FfiJni {
    init {
        System.loadLibrary("ffi_jni")
    }

    @JvmStatic external fun threadId(): Long

    @JvmStatic external fun spinLoop()

    @JvmStatic external fun yieldThread()
}
