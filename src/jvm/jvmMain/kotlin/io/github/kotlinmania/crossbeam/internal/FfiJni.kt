package io.github.kotlinmania.crossbeam.internal

/** JNI backend for the same OS primitives used by the Kotlin/Native port. */
internal object FfiJni {
    init {
        FfiNativeLibrary.load()
    }

    @JvmStatic external fun threadId(): Long

    @JvmStatic external fun spinLoop()

    @JvmStatic external fun yieldThread()
}
