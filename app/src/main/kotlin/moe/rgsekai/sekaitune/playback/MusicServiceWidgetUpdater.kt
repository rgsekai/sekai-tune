/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import moe.rgsekai.sekaitune.utils.ColdStartTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.extensions.SilentHandler
import moe.rgsekai.sekaitune.extensions.mediaItems
import moe.rgsekai.sekaitune.extensions.metadata
import moe.rgsekai.sekaitune.utils.makeTimeString
import moe.rgsekai.sekaitune.utils.reportException
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.widget.LoadWidgetInsightsUseCase
import moe.rgsekai.sekaitune.widget.LoadWidgetShortcutsUseCase
import moe.rgsekai.sekaitune.widget.MusicWidgetKeys
import moe.rgsekai.sekaitune.widget.WidgetInsightsSnapshot
import moe.rgsekai.sekaitune.widget.WidgetQueueItem
import moe.rgsekai.sekaitune.widget.WidgetShortcutItem
import moe.rgsekai.sekaitune.widget.WidgetShortcutType
import moe.rgsekai.sekaitune.widget.WidgetShortcutsSnapshot
import moe.rgsekai.sekaitune.widget.serializeWidgetQueueItems
import moe.rgsekai.sekaitune.widget.toWidgetPreferenceValue
import java.io.File

internal class MusicServiceWidgetUpdater(
    private val service: MusicService,
    private val player: Player,
    private val scope: CoroutineScope,
    private val loadWidgetInsights: LoadWidgetInsightsUseCase,
    private val loadWidgetShortcuts: LoadWidgetShortcutsUseCase,
) {
    private var progressJob: Job? = null
    private val updateMutex = Mutex()
    private val dominantColorCache = LruCache<String, Int>(50)

    fun update() {
        scope.launch(SilentHandler) {
            updateMutex.withLock {
                pushState()
            }
        }
    }

    fun setBuffering(buffering: Boolean) {
        ColdStartTimer.addStage("Widget: setBuffering($buffering)")
        scope.launch(SilentHandler) {
            updateMutex.withLock {
                playbackWidgets.forEach { target ->
                    val ids = GlanceAppWidgetManager(service).getGlanceIds(target.widgetClass)
                    if (ids.isEmpty()) return@forEach

                    ids.forEach { id ->
                        updateAppWidgetState(service, PreferencesGlanceStateDefinition, id) { prefs ->
                            prefs.toMutablePreferences().apply {
                                this[MusicWidgetKeys.IS_BUFFERING] = buffering
                            }
                        }
                    }
                    target.widget.updateAll(service)
                }
            }
        }
    }

    fun updateProgressTracking() {
        progressJob?.cancel()
        if (player.isPlaying && player.duration > 0) {
            progressJob =
                scope.launch(SilentHandler) {
                    while (isActive && player.isPlaying) {
                        updateProgress(player.playbackProgress())
                        delay(1_000)
                    }
                }
        }
    }

    private suspend fun pushState() {
        val mediaItem = player.currentMediaItem
        val mediaId = mediaItem?.mediaId
        val meta = mediaItem?.mediaMetadata
        val artFile = meta?.artworkUri?.let { cacheAlbumArt(it) }

        val dominantColor =
            if (mediaId != null) {
                dominantColorCache.get(mediaId) ?: artFile?.let { extractDominantColor(it) }?.also {
                    dominantColorCache.put(mediaId, it)
                }
            } else {
                null
            }

        val snapshot =
            WidgetSnapshot(
                title = meta?.title?.toString() ?: service.getString(R.string.no_track_playing),
                artist = meta?.artist?.toString().orEmpty(),
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                isAvailable = mediaItem != null,
                isLiked = service.isCurrentSongLiked(),
                playbackPosition = player.playbackProgress(),
                artPath = artFile?.absolutePath,
                dominantColor = dominantColor,
                mediaId = mediaId,
                duration = if (player.duration > 0) (player.duration / 1000).toInt() else 0,
                insights = WidgetInsightsSnapshot.Empty,
            )

        playbackWidgets.forEach { target ->
            updateWidget(target, snapshot)
        }
    }

    private suspend fun updateProgress(progress: Float) {
        progressWidgets.forEach { target ->
            val ids = GlanceAppWidgetManager(service).getGlanceIds(target.widgetClass)
            if (ids.isEmpty()) return@forEach

            ids.forEach { id ->
                updateAppWidgetState(service, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[MusicWidgetKeys.PLAYBACK_POSITION] = progress
                    }
                }
            }
            target.widget.updateAll(service)
        }
    }

    private suspend fun updateWidget(
        target: WidgetTarget,
        snapshot: WidgetSnapshot,
    ) {
        val ids = GlanceAppWidgetManager(service).getGlanceIds(target.widgetClass)
        if (ids.isEmpty()) return

        val targetSnapshot =
            when {
                target.requiresInsights -> snapshot.copy(insights = loadInsightsSnapshot())
                target.requiresShortcuts -> snapshot.copy(shortcuts = loadShortcutsSnapshot())
                else -> snapshot
            }

        ids.forEach { id ->
            updateAppWidgetState(service, PreferencesGlanceStateDefinition, id) { prefs ->
                val prevMediaId = prefs[MusicWidgetKeys.TRACK_MEDIA_ID]
                prefs.toMutablePreferences().apply {
                    if (prevMediaId != null && targetSnapshot.mediaId != null && prevMediaId != targetSnapshot.mediaId) {
                        remove(MusicWidgetKeys.LYRICS_TEXT)
                        this[MusicWidgetKeys.LYRICS_STATUS] = "IDLE"
                    }
                    writeSnapshot(targetSnapshot)
                }
            }
        }
        target.widget.updateAll(service)
    }

    private fun MutablePreferences.writeSnapshot(snapshot: WidgetSnapshot) {
        this[MusicWidgetKeys.TRACK_TITLE] = snapshot.title
        this[MusicWidgetKeys.TRACK_ARTIST] = snapshot.artist
        this[MusicWidgetKeys.IS_PLAYING] = snapshot.isPlaying
        this[MusicWidgetKeys.IS_BUFFERING] = snapshot.isBuffering
        this[MusicWidgetKeys.IS_AVAILABLE] = snapshot.isAvailable
        this[MusicWidgetKeys.IS_LIKED] = snapshot.isLiked
        this[MusicWidgetKeys.PLAYBACK_POSITION] = snapshot.playbackPosition

        val mediaId = snapshot.mediaId
        if (mediaId != null) {
            this[MusicWidgetKeys.TRACK_MEDIA_ID] = mediaId
        } else {
            remove(MusicWidgetKeys.TRACK_MEDIA_ID)
        }
        this[MusicWidgetKeys.TRACK_DURATION] = snapshot.duration

        val artPath = snapshot.artPath
        if (artPath != null) {
            this[MusicWidgetKeys.ART_PATH] = artPath
        } else {
            remove(MusicWidgetKeys.ART_PATH)
        }

        val dominantColor = snapshot.dominantColor
        if (dominantColor != null) {
            this[MusicWidgetKeys.DOMINANT_COLOR] = dominantColor
        } else {
            remove(MusicWidgetKeys.DOMINANT_COLOR)
        }

        writeInsights(snapshot.insights)
        writeShortcuts(snapshot.shortcuts)
    }

    private fun MutablePreferences.writeShortcuts(shortcuts: WidgetShortcutsSnapshot) {
        fun writeItem(
            sc: WidgetShortcutItem,
            titleKey: androidx.datastore.preferences.core.Preferences.Key<String>,
            subKey: androidx.datastore.preferences.core.Preferences.Key<String>,
            artKey: androidx.datastore.preferences.core.Preferences.Key<String>,
            typeKey: androidx.datastore.preferences.core.Preferences.Key<String>,
            idKey: androidx.datastore.preferences.core.Preferences.Key<String>,
        ) {
            if (sc.title.isNotBlank()) {
                this[titleKey] = sc.title
                this[subKey] = sc.subtitle
                this[typeKey] = sc.type.name
                this[idKey] = sc.targetId
                if (sc.artPathOrUrl != null) this[artKey] = sc.artPathOrUrl else remove(artKey)
            } else {
                remove(titleKey)
                remove(subKey)
                remove(artKey)
                remove(typeKey)
                remove(idKey)
            }
        }

        writeItem(shortcuts.shortcut1, MusicWidgetKeys.SHORTCUT_1_TITLE, MusicWidgetKeys.SHORTCUT_1_SUBTITLE, MusicWidgetKeys.SHORTCUT_1_ART, MusicWidgetKeys.SHORTCUT_1_TYPE, MusicWidgetKeys.SHORTCUT_1_ID)
        writeItem(shortcuts.shortcut2, MusicWidgetKeys.SHORTCUT_2_TITLE, MusicWidgetKeys.SHORTCUT_2_SUBTITLE, MusicWidgetKeys.SHORTCUT_2_ART, MusicWidgetKeys.SHORTCUT_2_TYPE, MusicWidgetKeys.SHORTCUT_2_ID)
        writeItem(shortcuts.shortcut3, MusicWidgetKeys.SHORTCUT_3_TITLE, MusicWidgetKeys.SHORTCUT_3_SUBTITLE, MusicWidgetKeys.SHORTCUT_3_ART, MusicWidgetKeys.SHORTCUT_3_TYPE, MusicWidgetKeys.SHORTCUT_3_ID)
        writeItem(shortcuts.shortcut4, MusicWidgetKeys.SHORTCUT_4_TITLE, MusicWidgetKeys.SHORTCUT_4_SUBTITLE, MusicWidgetKeys.SHORTCUT_4_ART, MusicWidgetKeys.SHORTCUT_4_TYPE, MusicWidgetKeys.SHORTCUT_4_ID)
        writeItem(shortcuts.shortcut5, MusicWidgetKeys.SHORTCUT_5_TITLE, MusicWidgetKeys.SHORTCUT_5_SUBTITLE, MusicWidgetKeys.SHORTCUT_5_ART, MusicWidgetKeys.SHORTCUT_5_TYPE, MusicWidgetKeys.SHORTCUT_5_ID)
        writeItem(shortcuts.shortcut6, MusicWidgetKeys.SHORTCUT_6_TITLE, MusicWidgetKeys.SHORTCUT_6_SUBTITLE, MusicWidgetKeys.SHORTCUT_6_ART, MusicWidgetKeys.SHORTCUT_6_TYPE, MusicWidgetKeys.SHORTCUT_6_ID)
        writeItem(shortcuts.shortcut7, MusicWidgetKeys.SHORTCUT_7_TITLE, MusicWidgetKeys.SHORTCUT_7_SUBTITLE, MusicWidgetKeys.SHORTCUT_7_ART, MusicWidgetKeys.SHORTCUT_7_TYPE, MusicWidgetKeys.SHORTCUT_7_ID)
        writeItem(shortcuts.shortcut8, MusicWidgetKeys.SHORTCUT_8_TITLE, MusicWidgetKeys.SHORTCUT_8_SUBTITLE, MusicWidgetKeys.SHORTCUT_8_ART, MusicWidgetKeys.SHORTCUT_8_TYPE, MusicWidgetKeys.SHORTCUT_8_ID)

        if (shortcuts.queueItems.isNotEmpty()) {
            this[MusicWidgetKeys.QUEUE_ITEMS] = serializeWidgetQueueItems(shortcuts.queueItems)
        } else {
            remove(MusicWidgetKeys.QUEUE_ITEMS)
        }
    }

    private fun MutablePreferences.writeInsights(insights: WidgetInsightsSnapshot) {
        if (insights.listeningTime.isNotBlank()) {
            this[MusicWidgetKeys.LISTENING_TIME] = insights.listeningTime
        } else {
            remove(MusicWidgetKeys.LISTENING_TIME)
        }
        if (insights.totalPlays.isNotBlank()) {
            this[MusicWidgetKeys.TOTAL_PLAYS] = insights.totalPlays
        } else {
            remove(MusicWidgetKeys.TOTAL_PLAYS)
        }
        writeList(MusicWidgetKeys.RECENT_SONGS, insights.recentSongs)
        writeList(MusicWidgetKeys.GENRES, insights.genres)
        writeList(MusicWidgetKeys.RECOMMENDATIONS, insights.recommendations)

        val topSongSummary = insights.topSongSummary
        if (!topSongSummary.isNullOrBlank()) {
            this[MusicWidgetKeys.TOP_SONG_SUMMARY] = topSongSummary
        } else {
            remove(MusicWidgetKeys.TOP_SONG_SUMMARY)
        }
    }

    private fun MutablePreferences.writeList(
        key: Preferences.Key<String>,
        values: List<String>,
    ) {
        if (values.isEmpty()) {
            remove(key)
        } else {
            this[key] = values.toWidgetPreferenceValue()
        }
    }

    private suspend fun loadInsightsSnapshot(): WidgetInsightsSnapshot =
        try {
            loadWidgetInsights()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            reportException(error)
            WidgetInsightsSnapshot.Empty
        }

    private suspend fun loadShortcutsSnapshot(): WidgetShortcutsSnapshot =
        try {
            val queueItems = buildQueueItems()
            val snapshot = loadWidgetShortcuts(queueItems)
            
            // Pre-cache shortcut art and top queue art in background
            val urlsToCache = listOfNotNull(
                snapshot.shortcut1.artPathOrUrl,
                snapshot.shortcut2.artPathOrUrl,
                snapshot.shortcut3.artPathOrUrl,
                snapshot.shortcut4.artPathOrUrl,
            ) + snapshot.queueItems.take(8).mapNotNull { it.artPathOrUrl }

            urlsToCache.forEach { url ->
                runCatching {
                    val uri = Uri.parse(url)
                    cacheAlbumArt(uri)
                }
            }

            snapshot
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            reportException(error)
            WidgetShortcutsSnapshot.Empty
        }

    private fun buildQueueItems(): List<WidgetQueueItem> {
        val currentIdx = player.currentMediaItemIndex
        val allItems = player.mediaItems
        if (allItems.isEmpty()) return emptyList()

        // Up next items starting after current track
        val upcoming = if (currentIdx in allItems.indices) {
            allItems.subList((currentIdx + 1).coerceAtMost(allItems.size), allItems.size)
        } else {
            allItems
        }

        return upcoming.take(20).mapNotNull { item ->
            val meta = item.metadata ?: return@mapNotNull null
            val durationSec = meta.duration ?: 0
            val durationStr = if (durationSec > 0) makeTimeString(durationSec * 1000L) else ""
            WidgetQueueItem(
                title = meta.title ?: item.mediaMetadata.title?.toString() ?: "",
                artist = meta.artists?.joinToString(", ") { it.name } ?: item.mediaMetadata.artist?.toString().orEmpty(),
                artPathOrUrl = meta.thumbnailUrl ?: item.mediaMetadata.artworkUri?.toString(),
                durationText = durationStr,
                mediaId = meta.id ?: item.mediaId,
            )
        }
    }

    private suspend fun cacheAlbumArt(uri: Uri): File? =
        withContext(Dispatchers.IO) {
            val dest = File(service.cacheDir, "widget_art_${Integer.toHexString(uri.toString().hashCode())}.jpg")

            if (uri.scheme == "content" || uri.scheme == "file") {
                return@withContext try {
                    service.contentResolver.openInputStream(uri)?.use { src ->
                        dest.outputStream().use { dst -> src.copyTo(dst) }
                    }
                    if (dest.exists() && dest.length() > 0) dest else null
                } catch (_: Exception) {
                    null
                }
            }

            if (uri.scheme == "https" || uri.scheme == "http") {
                return@withContext try {
                    val loader = service.applicationContext.imageLoader
                    val request =
                        ImageRequest
                            .Builder(service.applicationContext)
                            .data(uri.toString())
                            .size(512, 512)
                            .allowHardware(false)
                            .build()
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = result.image.toBitmap()
                        dest.outputStream().use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
                        }
                        if (dest.exists() && dest.length() > 0) dest else null
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }

            null
        }

    private suspend fun extractDominantColor(file: File): Int? =
        withContext(Dispatchers.Default) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
                val palette = Palette.from(bitmap).generate()
                palette.vibrantSwatch?.rgb
                    ?: palette.dominantSwatch?.rgb
                    ?: palette.mutedSwatch?.rgb
                    ?: palette.darkVibrantSwatch?.rgb
                    ?: palette.lightVibrantSwatch?.rgb
                    ?: palette.getDominantColor(android.graphics.Color.DKGRAY)
            } catch (_: Exception) {
                null
            }
        }

    private fun Player.playbackProgress(): Float =
        if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    private data class WidgetSnapshot(
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val isBuffering: Boolean,
        val isAvailable: Boolean,
        val isLiked: Boolean,
        val playbackPosition: Float,
        val artPath: String?,
        val dominantColor: Int?,
        val mediaId: String?,
        val duration: Int,
        val insights: WidgetInsightsSnapshot,
        val shortcuts: WidgetShortcutsSnapshot = WidgetShortcutsSnapshot.Empty,
    )

    private data class WidgetTarget(
        val widgetClass: Class<out GlanceAppWidget>,
        val widget: GlanceAppWidget,
        val requiresInsights: Boolean = false,
        val requiresShortcuts: Boolean = false,
    )

    private companion object {
        val playbackWidgets: List<WidgetTarget> =
            listOf(
                WidgetTarget(
                    widgetClass = moe.rgsekai.sekaitune.widget.PlayPauseWidget::class.java,
                    widget = moe.rgsekai.sekaitune.widget.PlayPauseWidget(),
                ),
                WidgetTarget(
                    widgetClass = moe.rgsekai.sekaitune.widget.NowPlayingCardWidget::class.java,
                    widget = moe.rgsekai.sekaitune.widget.NowPlayingCardWidget(),
                ),
                WidgetTarget(
                    widgetClass = moe.rgsekai.sekaitune.widget.NowPlayingLyricsWidget::class.java,
                    widget = moe.rgsekai.sekaitune.widget.NowPlayingLyricsWidget(),
                ),
                WidgetTarget(
                    widgetClass = moe.rgsekai.sekaitune.widget.YourLibraryShortcutsWidget::class.java,
                    widget = moe.rgsekai.sekaitune.widget.YourLibraryShortcutsWidget(),
                    requiresShortcuts = true,
                ),
            )
        val progressWidgets: List<WidgetTarget> =
            listOf(
                WidgetTarget(
                    widgetClass = moe.rgsekai.sekaitune.widget.NowPlayingCardWidget::class.java,
                    widget = moe.rgsekai.sekaitune.widget.NowPlayingCardWidget(),
                ),
                WidgetTarget(
                    widgetClass = moe.rgsekai.sekaitune.widget.NowPlayingLyricsWidget::class.java,
                    widget = moe.rgsekai.sekaitune.widget.NowPlayingLyricsWidget(),
                ),
                WidgetTarget(
                    widgetClass = moe.rgsekai.sekaitune.widget.YourLibraryShortcutsWidget::class.java,
                    widget = moe.rgsekai.sekaitune.widget.YourLibraryShortcutsWidget(),
                    requiresShortcuts = true,
                ),
            )
    }
}




