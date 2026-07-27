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
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.extensions.SilentHandler
import moe.rgsekai.sekaitune.utils.reportException
import moe.rgsekai.sekaitune.widget.AlbumArtWidget
import moe.rgsekai.sekaitune.widget.ListeningInsightsWidget
import moe.rgsekai.sekaitune.widget.LoadWidgetInsightsUseCase
import moe.rgsekai.sekaitune.widget.MusicWidget
import moe.rgsekai.sekaitune.widget.MusicWidgetKeys
import moe.rgsekai.sekaitune.widget.NowPlayingCardWidget
import moe.rgsekai.sekaitune.widget.PlaybackCapsuleWidget
import moe.rgsekai.sekaitune.widget.PlaybackCommandWidget
import moe.rgsekai.sekaitune.widget.PlaybackDeckWidget
import moe.rgsekai.sekaitune.widget.PlaybackSpotlightWidget
import moe.rgsekai.sekaitune.widget.WidgetInsightsSnapshot
import moe.rgsekai.sekaitune.widget.toWidgetPreferenceValue
import java.io.File

internal class MusicServiceWidgetUpdater(
    private val service: MusicService,
    private val player: Player,
    private val scope: CoroutineScope,
    private val loadWidgetInsights: LoadWidgetInsightsUseCase,
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
                playbackPosition = player.playbackProgress(),
                artPath = artFile?.absolutePath,
                dominantColor = dominantColor,
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
            if (target.requiresInsights) {
                snapshot.copy(insights = loadInsightsSnapshot())
            } else {
                snapshot
            }

        ids.forEach { id ->
            updateAppWidgetState(service, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply {
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
        this[MusicWidgetKeys.PLAYBACK_POSITION] = snapshot.playbackPosition

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
                palette.getDarkVibrantColor(
                    palette.getDominantColor(android.graphics.Color.DKGRAY),
                )
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
        val playbackPosition: Float,
        val artPath: String?,
        val dominantColor: Int?,
        val insights: WidgetInsightsSnapshot,
    )

    private data class WidgetTarget(
        val widgetClass: Class<out GlanceAppWidget>,
        val widget: GlanceAppWidget,
        val requiresInsights: Boolean = false,
    )

    private companion object {
        val playbackWidgets =
            listOf(
                WidgetTarget(MusicWidget::class.java, MusicWidget()),
                WidgetTarget(NowPlayingCardWidget::class.java, NowPlayingCardWidget()),
                WidgetTarget(PlaybackDeckWidget::class.java, PlaybackDeckWidget()),
                WidgetTarget(AlbumArtWidget::class.java, AlbumArtWidget()),
                WidgetTarget(PlaybackCapsuleWidget::class.java, PlaybackCapsuleWidget()),
                WidgetTarget(PlaybackSpotlightWidget::class.java, PlaybackSpotlightWidget()),
                WidgetTarget(PlaybackCommandWidget::class.java, PlaybackCommandWidget()),
                WidgetTarget(
                    widgetClass = ListeningInsightsWidget::class.java,
                    widget = ListeningInsightsWidget(),
                    requiresInsights = true,
                ),
            )

        val progressWidgets =
            listOf(
                WidgetTarget(MusicWidget::class.java, MusicWidget()),
                WidgetTarget(NowPlayingCardWidget::class.java, NowPlayingCardWidget()),
                WidgetTarget(PlaybackDeckWidget::class.java, PlaybackDeckWidget()),
                WidgetTarget(PlaybackCapsuleWidget::class.java, PlaybackCapsuleWidget()),
                WidgetTarget(PlaybackSpotlightWidget::class.java, PlaybackSpotlightWidget()),
                WidgetTarget(PlaybackCommandWidget::class.java, PlaybackCommandWidget()),
            )
    }
}




