package moe.rgsekai.sekaitune.shared.platform

import java.util.concurrent.ConcurrentHashMap

// Delegation, not a typealias: ConcurrentHashMap.keys is KeySetView, which an expect MutableMap can't match.
// ponytail: java default methods (computeIfAbsent...) are not atomic through the delegate; use getOrPut-under-lock or a typealias in jvm-only code.
actual class ConcurrentMap<K, V> actual constructor() : MutableMap<K, V> by ConcurrentHashMap<K, V>()
