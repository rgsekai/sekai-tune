/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.scrobble

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

class ScrobbleQueue(
    private val storageFile: File,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val items = ArrayDeque<ScrobbleItem>()

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    suspend fun load() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!storageFile.exists()) {
                items.clear()
                _count.value = 0
                return@withContext
            }
            try {
                val raw = storageFile.readText()
                if (raw.isNotBlank()) {
                    val deserialized = json.decodeFromString(ListSerializer(ScrobbleItem.serializer()), raw)
                    items.clear()
                    items.addAll(deserialized)
                    _count.value = items.size
                }
            } catch (e: Exception) {
                Timber.tag("ScrobbleQueue").w(e, "Failed to load scrobble queue from ${storageFile.name}")
            }
        }
    }

    suspend fun enqueue(item: ScrobbleItem) = mutex.withLock {
        items.addLast(item)
        _count.value = items.size
        saveToFileLocked()
    }

    suspend fun peekAll(): List<ScrobbleItem> = mutex.withLock {
        items.toList()
    }

    suspend fun remove(item: ScrobbleItem) = mutex.withLock {
        items.remove(item)
        _count.value = items.size
        saveToFileLocked()
    }

    suspend fun clear() = mutex.withLock {
        items.clear()
        _count.value = 0
        saveToFileLocked()
    }

    private suspend fun saveToFileLocked() {
        withContext(Dispatchers.IO) {
            try {
                if (items.isEmpty()) {
                    if (storageFile.exists()) storageFile.delete()
                } else {
                    val raw = json.encodeToString(ListSerializer(ScrobbleItem.serializer()), items.toList())
                    storageFile.writeText(raw)
                }
            } catch (e: Exception) {
                Timber.tag("ScrobbleQueue").w(e, "Failed to persist scrobble queue to ${storageFile.name}")
            }
        }
    }
}

