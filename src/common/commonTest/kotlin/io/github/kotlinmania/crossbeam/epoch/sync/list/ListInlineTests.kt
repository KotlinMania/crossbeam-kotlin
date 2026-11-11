// port-lint: tests crossbeam-epoch/src/sync/list.rs
package io.github.kotlinmania.crossbeam.epoch.sync.list

import io.github.kotlinmania.crossbeam.epoch.atomic.Owned
import io.github.kotlinmania.crossbeam.epoch.atomic.Shared
import io.github.kotlinmania.crossbeam.epoch.collector.Collector
import io.github.kotlinmania.crossbeam.epoch.guard.Guard

// Lock-free intrusive linked list.
//
// Ideas from Michael.  High Performance Dynamic Lock-Free Hash Tables and List-Based Sets.  SPAA
// 2002.  <http://dl.acm.org/citation.cfm?id=564870.564881>

public fun isElementEntry(entry: Entry): Entry {
    return entry
}

public fun isElementEntry(entry: Entry, guard: Guard) {
    guard.defer_destroy(Shared.from(((Entry.element_of(entry) as Any?) as Unit)))
}

/**
 * Checks whether the list retains inserted elements
 * and returns them in the correct order.
 */
@kotlin.test.Test
public fun insert() {
    val collector = Collector.new()
    val handle = collector.register()
    val guard = handle.pin()
    val l: List<Entry> = List.new()
    val e1 = Owned.new(Entry.default()).into_shared(guard)
    val e2 = Owned.new(Entry.default()).into_shared(guard)
    val e3 = Owned.new(Entry.default()).into_shared(guard)
    run {
        l.insert(e1, guard)
        l.insert(e2, guard)
        l.insert(e3, guard)
    }
    var iter = l.iter(guard)
    val maybe_e3 = iter.next()
    check(maybe_e3 != null)
    check(maybe_e3.unwrap().unwrap().toLong() == e3.as_raw())
    val maybe_e2 = iter.next()
    check(maybe_e2 != null)
    check(maybe_e2.unwrap().unwrap().toLong() == e2.as_raw())
    val maybe_e1 = iter.next()
    check(maybe_e1 != null)
    check(maybe_e1.unwrap().unwrap().toLong() == e1.as_raw())
    check(iter.next() == null)
    run {
        e1.unwrap().delete(guard)
        e2.unwrap().delete(guard)
        e3.unwrap().delete(guard)
    }
}

/**
 * Checks whether elements can be removed from the list and whether
 * the correct elements are removed.
 */
@kotlin.test.Test
public fun delete() {
    val collector = Collector.new()
    val handle = collector.register()
    val guard = handle.pin()
    val l: List<Entry> = List.new()
    val e1 = Owned.new(Entry.default()).into_shared(guard)
    val e2 = Owned.new(Entry.default()).into_shared(guard)
    val e3 = Owned.new(Entry.default()).into_shared(guard)
    run {
        l.insert(e1, guard)
        l.insert(e2, guard)
        l.insert(e3, guard)
        e2.unwrap().delete(guard)
    }
    var iter = l.iter(guard)
    val maybe_e3 = iter.next()
    check(maybe_e3 != null)
    check(maybe_e3.unwrap().unwrap().toLong() == e3.as_raw())
    val maybe_e1 = iter.next()
    check(maybe_e1 != null)
    check(maybe_e1.unwrap().unwrap().toLong() == e1.as_raw())
    check(iter.next() == null)
    run {
        e1.unwrap().delete(guard)
        e3.unwrap().delete(guard)
    }
    var iter1 = l.iter(guard)
    check(iter1.next() == null)
}

public val THREADS: ULong = 8uL

public val ITERS: ULong = 512uL

/**
 * Contends the list on insert and delete operations to make sure they can run concurrently.
 */
@kotlin.test.Test
public fun insert_delete_multi() {
    val collector = Collector.new()
    val l: List<Entry> = List.new()
    val b = Barrier.new(THREADS)
    io.github.kotlinmania.crossbeam.utils.thread.scope({ s ->
        for (unused in 0 until THREADS) {
            s.spawn({
                b.wait()
                val handle = collector.register()
                val guard: Guard = handle.pin()
                var v = Vec.with_capacity(ITERS)
                for (unused1 in 0 until ITERS) {
                    val e = Owned.new(Entry.default()).into_shared(guard)
                    v.push(e)
                    run {
                        l.insert(e, guard)
                    }
                }
                for (e1 in v) {
                    run {
                        e1.unwrap().delete(guard)
                    }
                }
            })
        }
    }).unwrap()
    val handle = collector.register()
    val guard = handle.pin()
    var iter = l.iter(guard)
    check(iter.next() == null)
}

/**
 * Contends the list on iteration to make sure that it can be iterated over concurrently.
 */
@kotlin.test.Test
public fun iter_multi() {
    val collector = Collector.new()
    val l: List<Entry> = List.new()
    val b = Barrier.new(THREADS)
    io.github.kotlinmania.crossbeam.utils.thread.scope({ s ->
        for (unused in 0 until THREADS) {
            s.spawn({
                b.wait()
                val handle = collector.register()
                val guard: Guard = handle.pin()
                var v = Vec.with_capacity(ITERS)
                for (unused1 in 0 until ITERS) {
                    val e = Owned.new(Entry.default()).into_shared(guard)
                    v.push(e)
                    run {
                        l.insert(e, guard)
                    }
                }
                var iter = l.iter(guard)
                for (unused2 in 0 until ITERS) {
                    check(iter.next() != null)
                }
                for (e1 in v) {
                    run {
                        e1.unwrap().delete(guard)
                    }
                }
            })
        }
    }).unwrap()
    val handle = collector.register()
    val guard = handle.pin()
    var iter = l.iter(guard)
    check(iter.next() == null)
}
