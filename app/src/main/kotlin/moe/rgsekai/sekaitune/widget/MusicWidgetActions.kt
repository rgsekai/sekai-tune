/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.db.entities.LyricsEntity
import moe.rgsekai.sekaitune.di.LyricsHelperEntryPoint
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.playback.MusicService

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        sendWidgetAction(context, ACTION_PLAY_PAUSE)
    }
}

class SkipNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        sendWidgetAction(context, ACTION_SKIP_NEXT)
    }
}

class SkipPrevAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        sendWidgetAction(context, ACTION_SKIP_PREV)
    }
}

class LikeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        toggleWidgetLike(context)
    }
}

class LyricsToggleAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        toggleLyrics(context, glanceId)
    }
}

class PlayShortcutLikedSongsAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        sendWidgetAction(context, ACTION_PLAY_LIKED_SONGS)
    }
}

class PlayShortcutPlaylistAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val playlistId = parameters[KEY_TARGET_ID] ?: return
        sendWidgetAction(context, ACTION_PLAY_PLAYLIST, EXTRA_PLAYLIST_ID to playlistId)
    }

    companion object {
        val KEY_TARGET_ID = ActionParameters.Key<String>("target_id")
    }
}

class PlayShortcutSongAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val songId = parameters[KEY_TARGET_ID] ?: return
        sendWidgetAction(context, ACTION_PLAY_SONG, EXTRA_SONG_ID to songId)
    }

    companion object {
        val KEY_TARGET_ID = ActionParameters.Key<String>("target_id")
    }
}

private const val ACTION_PLAY_PAUSE = "moe.rgsekai.sekaitune.WIDGET_PLAY_PAUSE"
private const val ACTION_SKIP_NEXT = "moe.rgsekai.sekaitune.WIDGET_SKIP_NEXT"
private const val ACTION_SKIP_PREV = "moe.rgsekai.sekaitune.WIDGET_SKIP_PREV"
const val ACTION_TOGGLE_LIKE = "moe.rgsekai.sekaitune.WIDGET_TOGGLE_LIKE"
const val ACTION_PLAY_LIKED_SONGS = "moe.rgsekai.sekaitune.WIDGET_PLAY_LIKED_SONGS"
const val ACTION_PLAY_PLAYLIST = "moe.rgsekai.sekaitune.WIDGET_PLAY_PLAYLIST"
const val ACTION_PLAY_SONG = "moe.rgsekai.sekaitune.WIDGET_PLAY_SONG"
const val EXTRA_PLAYLIST_ID = "moe.rgsekai.sekaitune.EXTRA_PLAYLIST_ID"
const val EXTRA_SONG_ID = "moe.rgsekai.sekaitune.EXTRA_SONG_ID"
private const val TAG = "MusicWidgetActions"

private fun toggleWidgetLike(context: Context) {
    sendWidgetAction(context, ACTION_TOGGLE_LIKE)
}

private suspend fun toggleLyrics(
    context: Context,
    glanceId: GlanceId,
) {
    var shouldFetch = false
    var currentTitle = ""
    var currentArtist = ""
    var currentMediaId: String? = null
    var currentDuration = 0

    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
        val currentShow = prefs[MusicWidgetKeys.SHOW_LYRICS] ?: false
        val newShow = !currentShow
        currentTitle = prefs[MusicWidgetKeys.TRACK_TITLE].orEmpty()
        currentArtist = prefs[MusicWidgetKeys.TRACK_ARTIST].orEmpty()
        currentMediaId = prefs[MusicWidgetKeys.TRACK_MEDIA_ID]
        currentDuration = prefs[MusicWidgetKeys.TRACK_DURATION] ?: 0
        val existingLyrics = prefs[MusicWidgetKeys.LYRICS_TEXT]
        val existingStatus = prefs[MusicWidgetKeys.LYRICS_STATUS]

        prefs.toMutablePreferences().apply {
            this[MusicWidgetKeys.SHOW_LYRICS] = newShow
            if (newShow && (existingLyrics.isNullOrBlank() || existingStatus == "LOADING")) {
                if (currentTitle.isBlank() || currentTitle == context.getString(R.string.no_track_playing)) {
                    this[MusicWidgetKeys.LYRICS_STATUS] = "NO_TRACK"
                } else {
                    this[MusicWidgetKeys.LYRICS_STATUS] = "LOADING"
                    shouldFetch = true
                }
            }
        }
    }
    NowPlayingLyricsWidget().update(context, glanceId)

    if (shouldFetch) {
        CoroutineScope(Dispatchers.IO).launch {
            fetchLyricsForWidget(
                context = context,
                glanceId = glanceId,
                mediaId = currentMediaId,
                title = currentTitle,
                artist = currentArtist,
                duration = currentDuration,
            )
        }
    }
}

private suspend fun fetchLyricsForWidget(
    context: Context,
    glanceId: GlanceId,
    mediaId: String?,
    title: String,
    artist: String,
    duration: Int,
) {
    try {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            LyricsHelperEntryPoint::class.java,
        )
        val database = entryPoint.database()
        val lyricsHelper = entryPoint.lyricsHelper()

        var fetchedLyrics: String? = null

        // 1. Try local database if mediaId exists
        if (!mediaId.isNullOrBlank()) {
            val dbLyrics = withContext(Dispatchers.IO) {
                database.getLyricsById(mediaId)
            }
            if (dbLyrics != null && dbLyrics.lyrics != LyricsEntity.LYRICS_NOT_FOUND) {
                fetchedLyrics = dbLyrics.lyrics
            }
        }

        // 2. If not found in DB, fetch live via LyricsHelper
        if (fetchedLyrics == null) {
            val mediaMetadata = MediaMetadata(
                id = mediaId.orEmpty(),
                title = title,
                artists = if (artist.isNotBlank()) {
                    listOf(MediaMetadata.Artist(id = null, name = artist))
                } else {
                    emptyList()
                },
                duration = duration,
            )
            val result = lyricsHelper.getLyrics(mediaMetadata)
            if (result != LyricsEntity.LYRICS_NOT_FOUND && result.isNotBlank()) {
                fetchedLyrics = result
                // Save to database if mediaId exists
                if (!mediaId.isNullOrBlank()) {
                    database.query {
                        insertLyricsIfAbsent(
                            id = mediaId,
                            lyrics = result,
                        )
                    }
                }
            }
        }

        val finalStatus = if (!fetchedLyrics.isNullOrBlank()) "READY" else "NOT_FOUND"
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[MusicWidgetKeys.LYRICS_STATUS] = finalStatus
                if (fetchedLyrics != null) {
                    this[MusicWidgetKeys.LYRICS_TEXT] = fetchedLyrics
                } else {
                    remove(MusicWidgetKeys.LYRICS_TEXT)
                }
            }
        }
        NowPlayingLyricsWidget().update(context, glanceId)
    } catch (e: Exception) {
        Log.e(TAG, "Error fetching lyrics for widget", e)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[MusicWidgetKeys.LYRICS_STATUS] = "NOT_FOUND"
            }
        }
        NowPlayingLyricsWidget().update(context, glanceId)
    }
}

private fun sendWidgetAction(
    context: Context,
    action: String,
    extra: Pair<String, String>? = null,
) {
    val intent = Intent(action).setClass(context, MusicService::class.java).apply {
        if (extra != null) {
            putExtra(extra.first, extra.second)
        }
    }
    runCatching {
        ContextCompat.startForegroundService(context, intent)
    }.onFailure { error ->
        Log.e(TAG, "Failed to send widget action: $action", error)
    }
}
