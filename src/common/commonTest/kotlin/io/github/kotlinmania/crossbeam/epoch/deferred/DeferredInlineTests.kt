// port-lint: tests crossbeam-epoch/src/deferred.rs
package io.github.kotlinmania.crossbeam.epoch.deferred

@kotlin.test.Test
public fun on_stack() {
    var fired = false
    val a = List(1) { 0uL }
    val d = Deferred.new({
        a.also { }
        fired = true
    })
    check(!fired)
    d.call()
    check(fired)
}

@kotlin.test.Test
public fun on_heap() {
    var fired = false
    val a = List(10) { 0uL }
    val d = Deferred.new({
        a.also { }
        fired = true
    })
    check(!fired)
    d.call()
    check(fired)
}

@kotlin.test.Test
public fun string() {
    val a = "hello"
    val d = Deferred.new({ check(a == "hello") })
    d.call()
}

@kotlin.test.Test
public fun boxed_slice_i32() {
    val a: List<Int> = listOf(2, 3, 5, 7)
    val d = Deferred.new({ check(a == listOf(2, 3, 5, 7)) })
    d.call()
}

@kotlin.test.Test
public fun long_slice_usize() {
    val a: IntArray = intArrayOf(2, 3, 5, 7, 11)
    val d = Deferred.new({ check(a.contentEquals(intArrayOf(2, 3, 5, 7, 11))) })
    d.call()
}
