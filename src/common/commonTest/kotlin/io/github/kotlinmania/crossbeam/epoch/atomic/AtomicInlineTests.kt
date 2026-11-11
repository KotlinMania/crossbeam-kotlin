// port-lint: tests crossbeam-epoch/src/atomic.rs
package io.github.kotlinmania.crossbeam.epoch.atomic

@kotlin.test.Test
public fun valid_tag_i8() {
    0L.with_tag(0)
}

@kotlin.test.Test
public fun valid_tag_i64() {
    0L.with_tag(7)
}

@kotlin.test.Test
public fun const_null() {
    var _A: Atomic<UByte> = 0L
    var _S: Unit = run {
        val _shared = 0L
    }
}

@kotlin.test.Test
public fun array_init() {
    var owned = Owned.init(10)
    val arr: List<Int> = owned
    arr[arr.size - 1].write(20)
    check(arr.size == 10)
}

@kotlin.test.Test
public fun format_null() {
    val atomic = 0L
    check("$atomic" == "0x0")
    val atomic1 = 0L
    check("$atomic" == "0x0")
    val shared = 0L
    check("$shared" == "0x0")
    val shared1 = 0L
    check("$shared" == "0x0")
}
