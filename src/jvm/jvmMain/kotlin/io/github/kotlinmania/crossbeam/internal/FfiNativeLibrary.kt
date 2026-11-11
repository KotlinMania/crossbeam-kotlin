package io.github.kotlinmania.crossbeam.internal

import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.outputStream

/** Loads the packaged JNI payload when it is not installed on the system library path. */
internal object FfiNativeLibrary {
    private var loaded = false

    @Synchronized
    fun load() {
        if (loaded) return
        try {
            System.loadLibrary("ffi_jni")
            loaded = true
            return
        } catch (systemFailure: UnsatisfiedLinkError) {
            val osName = System.getProperty("os.name", "").lowercase()
            val platform =
                when {
                    osName.contains("windows") -> "windows"
                    osName.contains("mac") -> "macos"
                    osName.contains("linux") -> "linux"
                    else -> throw UnsatisfiedLinkError("Unsupported JNI operating system: $osName")
                }
            val architecture =
                when (val arch = System.getProperty("os.arch", "").lowercase()) {
                    "aarch64", "arm64" -> "aarch64"
                    "amd64", "x86_64" -> "x86_64"
                    else -> throw UnsatisfiedLinkError("Unsupported JNI architecture: $arch")
                }
            val libraryName = System.mapLibraryName("ffi_jni")
            val resource = "/io/github/kotlinmania/crossbeam/native/$platform-$architecture/$libraryName"
            val payload =
                FfiNativeLibrary::class.java.getResourceAsStream(resource)
                    ?: throw UnsatisfiedLinkError("Missing packaged Crossbeam JNI payload: $resource").apply {
                        addSuppressed(systemFailure)
                    }
            try {
                payload.use { input ->
                    val cache = Path(System.getProperty("user.home"), ".cache", "kotlinmania", "crossbeam", "native")
                    cache.createDirectories()
                    // A private unique directory prevents races and overwrites between library versions or classloaders.
                    val directory = createTempDirectory(cache, "$platform-$architecture-")
                    val library = directory.resolve(libraryName)
                    // JVM shutdown deletes files before their parent. Windows may keep a loaded DLL locked until then.
                    directory.toFile().deleteOnExit()
                    library.toFile().deleteOnExit()
                    library.outputStream().use { output -> input.copyTo(output) }
                    System.load(library.toAbsolutePath().toString())
                }
            } catch (failure: Throwable) {
                failure.addSuppressed(systemFailure)
                throw failure
            }
            loaded = true
        }
    }
}
