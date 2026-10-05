package org.fikohc.espkbd.ble

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// Map whose entries expire [ttl] after they were last set
class TtlCache<K, V>(private val ttl: Duration, private val timeSource: TimeSource = TimeSource.Monotonic) {
    private val entries = mutableMapOf<K, Pair<V, TimeMark>>()

    operator fun set(key: K, value: V) {
        entries[key] = value to timeSource.markNow()
    }

    operator fun get(key: K): V? = entries[key]?.takeIf { it.second.elapsedNow() < ttl }?.first

    // Live values; expired entries are dropped
    fun values(): List<V> {
        entries.values.removeAll { it.second.elapsedNow() >= ttl }
        return entries.values.map { it.first }
    }
}
