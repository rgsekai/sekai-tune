package moe.rgsekai.sekaitune.shared.platform

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

// ponytail: views (keys/values/entries) are snapshot copies, so writes through them don't reach the map
// and iteration isn't live; upgrade to a locking view wrapper if a caller needs that.
actual class ConcurrentMap<K, V> actual constructor() : MutableMap<K, V> {
    private val lock = SynchronizedObject()
    private val map = HashMap<K, V>()

    override val size get() = synchronized(lock) { map.size }
    override fun isEmpty() = synchronized(lock) { map.isEmpty() }
    override fun containsKey(key: K) = synchronized(lock) { map.containsKey(key) }
    override fun containsValue(value: V) = synchronized(lock) { map.containsValue(value) }
    override fun get(key: K) = synchronized(lock) { map[key] }
    override fun put(key: K, value: V) = synchronized(lock) { map.put(key, value) }
    override fun remove(key: K) = synchronized(lock) { map.remove(key) }
    override fun putAll(from: Map<out K, V>) = synchronized(lock) { map.putAll(from) }
    override fun clear() = synchronized(lock) { map.clear() }
    override val keys: MutableSet<K> get() = synchronized(lock) { HashSet(map.keys) }
    override val values: MutableCollection<V> get() = synchronized(lock) { ArrayList(map.values) }
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = synchronized(lock) { map.entries.mapTo(HashSet()) { java(it.key, it.value) } }

    private fun java(k: K, v: V): MutableMap.MutableEntry<K, V> = object : MutableMap.MutableEntry<K, V> {
        override val key = k
        override val value = v
        override fun setValue(newValue: V): V = throw UnsupportedOperationException()
    }
}
