package com.my.kizzy.rpc

import moe.rgsekai.sekaitune.shared.platform.ConcurrentMap

internal object ArtworkCache {
    private val cache = ConcurrentMap<String, String>()

    suspend fun getOrFetch(key: String, fetch: suspend () -> String?): String? {
        return cache[key] ?: fetch()?.also { cache[key] = it }
    }
}
