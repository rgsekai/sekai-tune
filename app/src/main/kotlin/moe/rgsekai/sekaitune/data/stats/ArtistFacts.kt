package moe.rgsekai.sekaitune.data.stats

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Artist metadata facts (images, genres, and page IDs) for Replay.
 */
object ArtistFacts {

    private const val TAG = "ArtistFacts"
    private const val FILE_NAME = "artist_facts.json"
    private const val MAX_ARTISTS = 2_000
    private const val SAVE_INTERVAL_MS = 10_000L
    private const val REQUEST_SPACING_MS = 250L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val known = ConcurrentHashMap<String, StoredArtist>()
    private val requests = Channel<String>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var file: File
    private var dirty = false
    private val ready: Boolean get() = ::file.isInitialized

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun init(context: Context) {
        file = File(context.filesDir, FILE_NAME)
        scope.launch {
            load()
            worker()
        }
        scope.launch {
            while (true) {
                delay(SAVE_INTERVAL_MS)
                save()
            }
        }
    }

    val genresAvailable: Boolean
        get() = false

    fun imageFor(artist: String): String? = known[key(artist)]?.image

    fun browseIdFor(artist: String): String? = known[key(artist)]?.browseId

    fun genresFor(artist: String): List<String> = known[key(artist)]?.genres.orEmpty()

    fun noticed(artist: String) {
        if (artist.isBlank()) return
        requests.trySend(artist)
    }

    private suspend fun worker() {
        for (name in requests) {
            delay(REQUEST_SPACING_MS)
        }
    }

    private fun key(artist: String): String = artist.trim().lowercase(Locale.ROOT)

    private fun load() {
        if (!ready || !file.exists()) return
        runCatching {
            json.decodeFromString(Stored.serializer(), file.readText())
        }.onSuccess { stored ->
            stored.artists.forEach { known[it.key] = it }
        }.onFailure {
            Log.w(TAG, "Discarding unreadable artist cache", it)
            file.delete()
        }
    }

    private fun save() {
        if (!ready || !dirty) return
        dirty = false
        runCatching {
            val stored = Stored(
                artists = known.values
                    .sortedByDescending { maxOf(it.cardAt, it.genresAt) }
                    .take(MAX_ARTISTS),
            )
            file.writeText(json.encodeToString(Stored.serializer(), stored))
        }.onFailure { Log.w(TAG, "Could not write artist cache", it) }
    }

    @Serializable
    private data class Stored(val version: Int = 2, val artists: List<StoredArtist> = emptyList())

    @Serializable
    data class StoredArtist(
        val key: String,
        val genres: List<String> = emptyList(),
        val genresAt: Long = 0L,
        val image: String? = null,
        val browseId: String? = null,
        val cardAt: Long = 0L,
    )
}
