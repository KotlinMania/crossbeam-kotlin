// port-lint: tests crossbeam-utils/tests/cache_padded.rs
package io.github.kotlinmania.crossbeam.utils.tests.cachepadded

import io.github.kotlinmania.crossbeam.utils.cachepadded.CachePadded

@kotlin.test.Test
public fun default() {
    val x: CachePadded<ULong> = Default.default()
    check(x == 0)
}

@kotlin.test.Test
public fun store_u64() {
    val x: CachePadded<ULong> = CachePadded.new(17)
    check(x == 17)
}

@kotlin.test.Test
public fun store_pair() {
    val x: CachePadded<Pair<ULong, ULong>> = CachePadded.new(Pair(17, 37))
    check(x.first == 17)
    check(x.second == 37)
}

@kotlin.test.Test
public fun distance() {
    val arr = listOf(CachePadded.new(17u.toUByte()), CachePadded.new(37u.toUByte()))
    val a = arr[0].toLong()
    val b = arr[1].toLong()
    val align = mem.align_of<CachePadded<Unit>>()
    check(align >= 32uL)
    check((a + align.toLong()) == b)
}

@kotlin.test.Test
public fun different_sizes() {
    CachePadded.new(17u.toUByte())
    CachePadded.new(17u.toUShort())
    CachePadded.new(17u)
    CachePadded.new(List(0) { 17uL })
    CachePadded.new(List(1) { 17uL })
    CachePadded.new(List(2) { 17uL })
    CachePadded.new(List(3) { 17uL })
    CachePadded.new(List(4) { 17uL })
    CachePadded.new(List(5) { 17uL })
    CachePadded.new(List(6) { 17uL })
    CachePadded.new(List(7) { 17uL })
    CachePadded.new(List(8) { 17uL })
}

@kotlin.test.Test
public fun large() {
    val a = List(9) { 17uL }
    val b = CachePadded.new(a)
    check(mem.size_of_val(a) <= mem.size_of_val(b))
}

@kotlin.test.Test
public fun debug() {
    check("${CachePadded.new(17uL)}" == "CachePadded { value: 17 }")
}

@kotlin.test.Test
public fun drops() {
    data class Foo(public var value: Cell<Int> = Cell<Int>()) {
        public var field0: Cell<Int>
            get() = value
            set(v) {
                value = v
            }

        public fun drop() {
            this.value.set(this.value.get() + 1)
        }
    }
    val count = Cell.new(0)
    val a = CachePadded.new(Foo(count))
    val b = CachePadded.new(Foo(count))
    check(count.get() == 0)
    drop(a)
    check(count.get() == 1)
    drop(b)
    check(count.get() == 2)
}

// This is intentional.
@kotlin.test.Test
public fun clone() {
    val a = CachePadded.new(17)
    val b = a.clone()
    check(a == b)
}

@kotlin.test.Test
public fun runs_custom_clone() {
    data class Foo(public var value: Cell<Int> = Cell<Int>()) {
        public var field0: Cell<Int>
            get() = value
            set(v) {
                value = v
            }

        public fun clone(): Foo {
            this.value.set(this.value.get() + 1)
            return Foo(this.value)
        }
    }
    val count = Cell.new(0)
    val a = CachePadded.new(Foo(count))
    a.clone()
    check(count.get() == 1)
}
