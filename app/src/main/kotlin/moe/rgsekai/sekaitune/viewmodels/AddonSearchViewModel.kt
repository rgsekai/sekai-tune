/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.viewmodels

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.db.entities.ArtistEntity
import moe.rgsekai.sekaitune.db.entities.FormatEntity
import moe.rgsekai.sekaitune.db.entities.SongArtistMap
import moe.rgsekai.sekaitune.db.entities.SongEntity
import moe.rgsekai.sekaitune.playback.PlayerConnection
import moe.rgsekai.sekaitune.playback.queues.ListQueue
import moe.rgsekai.sekaitune.playback.sources.AudioQualityManager
import moe.rgsekai.sekaitune.playback.sources.SourceKind
import moe.rgsekai.sekaitune.playback.sources.SourceRegistry
import moe.rgsekai.sekaitune.playback.sources.SourceTrack
import moe.rgsekai.sekaitune.playback.sources.StreamFormat
import moe.rgsekai.sekaitune.playback.sources.StreamRequest
import moe.rgsekai.sekaitune.playback.sources.addon.AddonClient
import moe.rgsekai.sekaitune.playback.sources.addon.AddonMusicSource
import moe.rgsekai.sekaitune.playback.sources.addon.AddonResponseClassifier
import timber.log.Timber
import javax.inject.Inject

data class AddonSourceResults(
    val sourceId: String,
    val sourceName: String,
    val isLoading: Boolean = false,
    val realFailure: String? = null,
    val tracks: List<SourceTrack> = emptyList(),
) {
    val isNoMatch: Boolean
        get() = tracks.isEmpty() && realFailure == null
}

data class AddonSearchUiState(
    val hasConfiguredAddons: Boolean = false,
    val isSearching: Boolean = false,
    val results: List<AddonSourceResults> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddonSearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sourceRegistry: SourceRegistry,
    private val database: MusicDatabase,
    private val audioQualityManager: AudioQualityManager,
) : ViewModel() {

    val query = MutableStateFlow("")

    private val _uiState = MutableStateFlow(AddonSearchUiState())
    val uiState: StateFlow<AddonSearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sourceRegistry.sourcesFlow.collectLatest { registered ->
                val hasAddons = registered.any { it.kind == SourceKind.ADDON && it.isEnabled }
                _uiState.value = _uiState.value.copy(hasConfiguredAddons = hasAddons)
                if (query.value.isNotBlank() && hasAddons) {
                    performSearch(query.value)
                }
            }
        }

        viewModelScope.launch {
            query
                .debounce(350)
                .distinctUntilChanged()
                .collectLatest { q ->
                    if (q.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            isSearching = false,
                            results = emptyList(),
                            errorMessage = null,
                        )
                    } else {
                        performSearch(q)
                    }
                }
        }
    }

    private suspend fun performSearch(q: String) {
        val addons =
            sourceRegistry.sourcesFlow.value
                .filter { it.kind == SourceKind.ADDON && it.isEnabled }
                .mapNotNull { registered -> (registered.instance as? AddonMusicSource)?.let { registered to it } }

        if (addons.isEmpty()) {
            _uiState.value =
                _uiState.value.copy(
                    hasConfiguredAddons = false,
                    isSearching = false,
                    results = emptyList(),
                )
            return
        }

        _uiState.value =
            _uiState.value.copy(
                hasConfiguredAddons = true,
                isSearching = true,
                errorMessage = null,
            )

        val results =
            withContext(Dispatchers.IO) {
                addons.map { (registered, source) ->
                    async {
                        val searchResult = source.search(q, limit = 20)
                        val tracks = searchResult.getOrDefault(emptyList())
                        val failure = searchResult.exceptionOrNull()
                        val realFailureReason = if (failure != null) {
                            val rawReason = AddonResponseClassifier.formatFailureReason(failure)
                            AddonClient.redactTokens(rawReason).ifBlank { "Connection error" }
                        } else null

                        AddonSourceResults(
                            sourceId = registered.id,
                            sourceName = registered.displayName,
                            isLoading = false,
                            realFailure = realFailureReason,
                            tracks = tracks,
                        )
                    }
                }.awaitAll()
            }

        _uiState.value =
            _uiState.value.copy(
                isSearching = false,
                results = results,
            )
    }

    fun playTrack(
        sourceId: String,
        track: SourceTrack,
        playerConnection: PlayerConnection,
    ) {
        viewModelScope.launch {
            try {
                val registeredSource = sourceRegistry.sourcesFlow.value.firstOrNull { it.id == sourceId }
                val source =
                    registeredSource?.instance as? AddonMusicSource
                        ?: throw IllegalStateException("Source not found")

                val request = StreamRequest.Best

                val streamResult =
                    withContext(Dispatchers.IO) {
                        source.stream(track.id, request)
                    }
                val stream = streamResult.getOrThrow()

                val mediaId = "addon:${source.configId}:${track.id}"
                val mimeType =
                    when (stream.format) {
                        StreamFormat.FLAC -> "audio/flac; codecs=\"flac\""
                        StreamFormat.AAC -> "audio/mp4; codecs=\"mp4a.40.2\""
                        StreamFormat.OPUS -> "audio/opus; codecs=\"opus\""
                        StreamFormat.MP3 -> "audio/mpeg"
                        else -> "audio/flac"
                    }

                val formatEntity =
                    FormatEntity(
                        id = mediaId,
                        itag = 999,
                        mimeType = mimeType,
                        codecs = stream.format.name.lowercase(),
                        bitrate = (stream.bitrateKbps ?: 1411) * 1000,
                        sampleRate = stream.sampleRateHz ?: 44100,
                        contentLength = 0L,
                        loudnessDb = null,
                        perceptualLoudnessDb = null,
                        playbackUrl = stream.url,
                    )

                val songEntity =
                    SongEntity(
                        id = mediaId,
                        title = track.title,
                        duration = track.durationSec ?: 0,
                        thumbnailUrl = track.artworkUrl,
                        albumName = track.album,
                    )

                withContext(Dispatchers.IO) {
                    database.query {
                        upsert(songEntity)
                        upsert(formatEntity)
                        track.artists.forEachIndexed { index, artistName ->
                            val artistId = "artist:${artistName.hashCode()}"
                            upsert(ArtistEntity(id = artistId, name = artistName))
                            insert(SongArtistMap(songId = mediaId, artistId = artistId, position = index))
                        }
                    }
                }

                val appMediaMetadata =
                    moe.rgsekai.sekaitune.models.MediaMetadata(
                        id = mediaId,
                        title = track.title,
                        artists = track.artists.map { moe.rgsekai.sekaitune.models.MediaMetadata.Artist(id = null, name = it) },
                        duration = track.durationSec ?: 0,
                        thumbnailUrl = track.artworkUrl,
                        album = track.album?.let { moe.rgsekai.sekaitune.models.MediaMetadata.Album(id = "album:${it.hashCode()}", title = it) },
                    )

                val mediaItem =
                    MediaItem.Builder()
                        .setMediaId(mediaId)
                        .setUri(stream.url.toUri())
                        .setCustomCacheKey(mediaId)
                        .setTag(appMediaMetadata)
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artists.joinToString())
                                .setAlbumTitle(track.album)
                                .setArtworkUri(track.artworkUrl?.toUri())
                                .setIsPlayable(true)
                                .build(),
                        )
                        .build()

                playerConnection.playQueue(
                    ListQueue(
                        title = registeredSource.displayName,
                        items = listOf(mediaItem),
                    ),
                )

                Timber.tag("ExternalSources").i(
                    "Playing track from addon '%s': %s (url=%s, format=%s, %s kbps)",
                    registeredSource.displayName,
                    track.title,
                    stream.url,
                    stream.format,
                    stream.bitrateKbps,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to play track from addon")
                _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "Failed to play stream")
            }
        }
    }
}
