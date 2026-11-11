// port-lint: source crossbeam-utils/src/cache_padded.rs
package io.github.kotlinmania.crossbeam.utils.cachepadded

public class DebugStruct(
    private val out: StringBuilder,
    private val name: String,
) {
    private val fields: MutableList<String> = mutableListOf()

    public fun field(name: String, value: Any?): DebugStruct =
        apply {
            fields.add("$name: $value")
        }

    public fun finish() {
        if (fields.isEmpty()) {
            out.append(name)
        } else {
            out.append("$name { ${fields.joinToString(", ")} }")
        }
    }

    public fun finishNonExhaustive() {
        fields.add("..")
        finish()
    }
}

// Starting from Intel's Sandy Bridge, spatial prefetcher is now pulling pairs of 64-byte cache
// lines at a time, so we have to align to 128 bytes rather than 64.
//
// Sources:
// - https://www.intel.com/content/dam/www/public/us/en/documents/manuals/64-ia-32-architectures-optimization-manual.pdf
// - https://github.com/facebook/folly/blob/1b5288e6eea6df074758f877c849b6e73bbb9fbb/folly/lang/Align.h#L107
//
// aarch64/arm64ec's big.LITTLE architecture has asymmetric cores and "big" cores have 128-byte cache line size.
//
// Sources:
// - https://www.mono-project.com/news/2016/09/12/arm64-icache/
//
// powerpc64 has 128-byte cache line size.
//
// Sources:
// - https://github.com/golang/go/blob/3dd58676054223962cd915bb0934d1f9f489d4d2/src/internal/cpu/cpu_ppc64x.go#L9
// - https://github.com/torvalds/linux/blob/3516bd729358a2a9b090c1905bd2a3fa926e24c6/arch/powerpc/include/asm/cache.h#L26
// arm, mips, mips64, sparc, and hexagon have 32-byte cache line size.
//
// Sources:
// - https://github.com/golang/go/blob/3dd58676054223962cd915bb0934d1f9f489d4d2/src/internal/cpu/cpu_arm.go#L7
// - https://github.com/golang/go/blob/3dd58676054223962cd915bb0934d1f9f489d4d2/src/internal/cpu/cpu_mips.go#L7
// - https://github.com/golang/go/blob/3dd58676054223962cd915bb0934d1f9f489d4d2/src/internal/cpu/cpu_mipsle.go#L7
// - https://github.com/golang/go/blob/3dd58676054223962cd915bb0934d1f9f489d4d2/src/internal/cpu/cpu_mips64x.go#L9
// - https://github.com/torvalds/linux/blob/3516bd729358a2a9b090c1905bd2a3fa926e24c6/arch/sparc/include/asm/cache.h#L17
// - https://github.com/torvalds/linux/blob/3516bd729358a2a9b090c1905bd2a3fa926e24c6/arch/hexagon/include/asm/cache.h#L12
// m68k has 16-byte cache line size.
//
// Sources:
// - https://github.com/torvalds/linux/blob/3516bd729358a2a9b090c1905bd2a3fa926e24c6/arch/m68k/include/asm/cache.h#L9
// s390x has 256-byte cache line size.
//
// Sources:
// - https://github.com/golang/go/blob/3dd58676054223962cd915bb0934d1f9f489d4d2/src/internal/cpu/cpu_s390x.go#L7
// - https://github.com/torvalds/linux/blob/3516bd729358a2a9b090c1905bd2a3fa926e24c6/arch/s390/include/asm/cache.h#L13
// x86, wasm, riscv, and sparc64 have 64-byte cache line size.
//
// Sources:
// - https://github.com/golang/go/blob/dda2991c2ea0c5914714469c4defc2562a907230/src/internal/cpu/cpu_x86.go#L9
// - https://github.com/golang/go/blob/3dd58676054223962cd915bb0934d1f9f489d4d2/src/internal/cpu/cpu_wasm.go#L7
// - https://github.com/torvalds/linux/blob/3516bd729358a2a9b090c1905bd2a3fa926e24c6/arch/riscv/include/asm/cache.h#L10
// - https://github.com/torvalds/linux/blob/3516bd729358a2a9b090c1905bd2a3fa926e24c6/arch/sparc/include/asm/cache.h#L19
//
// All others are assumed to have 64-byte cache line size.

/**
 * Pads and aligns a value to the length of a cache line.
 *
 * In concurrent programming, sometimes it is desirable to make sure commonly accessed pieces of
 * data are not placed into the same cache line. Updating an atomic value invalidates the whole
 * cache line it belongs to, which makes the next access to the same cache line slower for other
 * CPU cores. Use `CachePadded` to ensure updating one piece of data doesn't invalidate other
 * cached data.
 *
 * # Size and alignment
 *
 * Cache lines are assumed to be N bytes long, depending on the architecture:
 *
 * * On x86-64, aarch64, and powerpc64, N = 128.
 * * On arm, mips, mips64, sparc, and hexagon, N = 32.
 * * On m68k, N = 16.
 * * On s390x, N = 256.
 * * On all others, N = 64.
 *
 * Note that N is just a reasonable guess and is not guaranteed to match the actual cache line
 * length of the machine the program is running on. On modern Intel architectures, spatial
 * prefetcher is pulling pairs of 64-byte cache lines at a time, so we pessimistically assume that
 * cache lines are 128 bytes long.
 *
 * The size of `CachePadded<T>` is the smallest multiple of N bytes large enough to accommodate
 * a value of type `T`.
 *
 * The alignment of `CachePadded<T>` is the maximum of N bytes and the alignment of `T`.
 *
 * # Layout
 *
 * Since crossbeam-utils 0.8.22, this type is `@repr(C)` and is guaranteed that the pointer to
 * `CachePadded<T>` has the same address as the pointer to the underlying `T`.
 *
 * # Examples
 *
 * Alignment and padding:
 *
 * ```kotlin
 * import crossbeamutils.CachePadded
 *
 * val array = listOf(CachePadded.new(1), CachePadded.new(2))
 * val addr1 = ((((array[0] as Any?) as COpaquePointer?))?.rawValue ?: 0L).toInt()
 * val addr2 = ((((array[1] as Any?) as COpaquePointer?))?.rawValue ?: 0L).toInt()
 * check(addr2 - addr1 >= 16)
 * check(addr1 % 16 == 0)
 * check(addr2 % 16 == 0)
 * ```
 *
 * When building a concurrent queue with a head and a tail index, it is wise to place them in
 * different cache lines so that concurrent threads pushing and popping elements don't invalidate
 * each other's cache lines:
 *
 * ```kotlin
 * import crossbeamutils.CachePadded
 * import std.sync.atomic.AtomicUsize
 *
 * open class Queue<T>(
 *     public var head: CachePadded<AtomicUsize>,
 *     public var tail: CachePadded<AtomicUsize>,
 *     public var buffer: T?,
 * )
 * ```
 */
public class CachePadded<T>(public var value: T = T()) : From<T> {
    /**
     * Returns the inner value.
     *
     * # Examples
     *
     * ```kotlin
     * import crossbeamutils.CachePadded
     *
     * val paddedValue = CachePadded.new(7)
     * val value = paddedValue.intoInner()
     * check(value == 7)
     * ```
     */
    public fun into_inner(): T {
        return this.value
    }

    public fun deref(): T {
        return this.value
    }

    public fun deref_mut(): T {
        return this.value
    }

    public fun fmt(f: StringBuilder) {
        DebugStruct(f, "CachePadded").field("value", this.value).finish()
    }

    public fun fmtDisplay(f: StringBuilder) {
        fmt.Display.fmt(this.value, f)
    }

    public companion object {
        /**
         * Pads and aligns a value to the length of a cache line.
         *
         * # Examples
         *
         * ```kotlin
         * import crossbeamutils.CachePadded
         *
         * val paddedValue = CachePadded.new(1)
         * ```
         */
        public fun <T> new(t: T): CachePadded<T> {
            return CachePadded(value = t)
        }

        public fun <T> from(t: T): CachePadded<T> {
            return CachePadded.new(t)
        }
    }
}
