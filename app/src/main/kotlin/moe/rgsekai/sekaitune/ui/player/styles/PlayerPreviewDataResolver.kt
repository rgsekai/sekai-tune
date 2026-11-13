package moe.rgsekai.sekaitune.ui.player.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.db.entities.FormatEntity
import moe.rgsekai.sekaitune.db.entities.containerLabel
import moe.rgsekai.sekaitune.db.entities.LyricsEntity
import moe.rgsekai.sekaitune.lyrics.LyricsEntry
import moe.rgsekai.sekaitune.lyrics.LyricsUtils

/**
 * Resolves preview data for Now Playing styles following the field-level real-vs-placeholder rules:
 *
 * 1. Title & Artist:
 *    - Real song loaded (playing OR paused) with non-blank title -> real title & artist.
 *    - Otherwise -> placeholder "Song title" & "Artist name" from strings.xml.
 *
 * 2. Artwork:
 *    - Real song loaded with non-null thumbnail URL -> real thumbnail URL (with Coil fallback).
 *    - Otherwise or on null/empty -> neutral vector placeholder (R.drawable.ic_player_placeholder_art).
 *
 * 3. Lyrics line:
 *    - Synced lyrics already cached in memory for the active song -> current synced line text.
 *    - Never triggers network or background lyrics fetch from the picker.
 *    - Otherwise -> "Lyrics appear here" placeholder.
 *
 * 4. Duration & Progress:
 *    - Fixed 40% progress on placeholder, or frozen snapshot of player progress if media loaded.
 *    - Previews are completely paused and do not tick timers.
 *
 * 5. Format chip:
 *    - Active format badge label if known; otherwise neutral "FLAC 24-bit".
 */
@Composable
fun rememberPlayerPreviewData(): PlayerPreviewData {
    val playerConnection = LocalPlayerConnection.current

    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(null) }
    val playbackState by playerConnection?.playbackState?.collectAsState() ?: remember { androidx.compose.runtime.mutableIntStateOf(Player.STATE_IDLE) }
    val isPlaying by playerConnection?.isPlaying?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(false) }
    val currentSong by playerConnection?.currentSong?.collectAsState(initial = null) ?: remember { androidx.compose.runtime.mutableStateOf(null) }
    val currentLyricsEntity by playerConnection?.currentLyrics?.collectAsState(initial = null) ?: remember { androidx.compose.runtime.mutableStateOf(null) }
    val currentFormat by playerConnection?.currentFormat?.collectAsState(initial = null) ?: remember { androidx.compose.runtime.mutableStateOf(null) }
    val qualityBadgeInfo by playerConnection?.qualityBadgeFlow?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(null) }

    // Live position polling while player is playing so progress bar and lyrics update in real time
    var currentPositionMs by remember(mediaMetadata?.id) {
        mutableLongStateOf(playerConnection?.player?.currentPosition ?: 0L)
    }

    androidx.compose.runtime.LaunchedEffect(mediaMetadata?.id, isPlaying, playbackState) {
        val player = playerConnection?.player ?: return@LaunchedEffect
        currentPositionMs = player.currentPosition.coerceAtLeast(0L)
        while (isActive && isPlaying) {
            currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            delay(250L)
        }
    }

    val placeholderTitle = stringResource(R.string.player_styles_placeholder_title)
    val placeholderArtist = stringResource(R.string.player_styles_placeholder_artist)
    val placeholderLyrics = stringResource(R.string.player_styles_placeholder_lyrics)
    val placeholderFormat = stringResource(R.string.player_styles_placeholder_format)

    return remember(
        mediaMetadata,
        playbackState,
        isPlaying,
        currentSong,
        currentLyricsEntity,
        currentFormat,
        qualityBadgeInfo,
        currentPositionMs,
        placeholderTitle,
        placeholderArtist,
        placeholderLyrics,
        placeholderFormat,
    ) {
        val currentMeta = mediaMetadata
        val hasRealSong = currentMeta != null &&
            !currentMeta.title.isNullOrBlank() &&
            playbackState != Player.STATE_IDLE

        val title = if (hasRealSong) currentMeta.title else placeholderTitle
        val artist = if (hasRealSong) {
            currentMeta.artists.joinToString { it.name }.ifBlank { placeholderArtist }
        } else {
            placeholderArtist
        }

        val artworkModel: Any = if (hasRealSong && !currentMeta.thumbnailUrl.isNullOrBlank()) {
            currentMeta.thumbnailUrl
        } else {
            PlayerPlaceholderDefaults.DEFAULT_ARTWORK_RES
        }

        // Lyric line extraction from in-memory cache updated with live position
        val lyricLine: String = if (hasRealSong && currentLyricsEntity != null && currentLyricsEntity?.id == currentMeta.id) {
            val rawLyrics = currentLyricsEntity?.lyrics
            if (rawLyrics != null &&
                rawLyrics != LyricsEntity.LYRICS_NOT_FOUND &&
                (LyricsUtils.isLineSyncedLrc(rawLyrics) || LyricsUtils.isTtml(rawLyrics))
            ) {
                val lines = when {
                    LyricsUtils.isTtml(rawLyrics) -> LyricsUtils.parseTtml(rawLyrics, currentMeta.duration)
                    else -> LyricsUtils.parseLyrics(rawLyrics)
                }
                val leadMs = if (LyricsUtils.isTtml(rawLyrics)) 0L else 300L
                val lineIndex = LyricsUtils.findCurrentLineIndex(lines, currentPositionMs, leadMs)
                if (lineIndex in lines.indices && lines[lineIndex] != LyricsEntry.HEAD_LYRICS_ENTRY) {
                    lines[lineIndex].text.trim().takeIf { it.isNotBlank() } ?: placeholderLyrics
                } else {
                    placeholderLyrics
                }
            } else {
                placeholderLyrics
            }
        } else {
            placeholderLyrics
        }

        val durationMs = if (hasRealSong && currentMeta.duration > 0) {
            currentMeta.duration * 1000L
        } else {
            PlayerPlaceholderDefaults.DEFAULT_DURATION_MS
        }

        val progressMs = if (hasRealSong) {
            if (currentPositionMs > 0L) currentPositionMs else (durationMs * PlayerPlaceholderDefaults.DEFAULT_PROGRESS_FRACTION).toLong()
        } else {
            PlayerPlaceholderDefaults.DEFAULT_PROGRESS_MS
        }

        val formatTag = when {
            qualityBadgeInfo != null && qualityBadgeInfo?.mediaId == currentMeta?.id && !qualityBadgeInfo?.label.isNullOrBlank() -> {
                qualityBadgeInfo?.label.orEmpty()
            }
            currentFormat != null -> {
                val codec = currentFormat?.codecs?.takeIf { it.isNotBlank() } ?: currentFormat?.containerLabel().orEmpty()
                codec.ifBlank { placeholderFormat }
            }
            else -> placeholderFormat
        }

        val isLiked = currentSong?.song?.liked == true

        PlayerPreviewData(
            title = title,
            artist = artist,
            artworkModel = artworkModel,
            durationMs = durationMs,
            progressMs = progressMs,
            lyricLine = lyricLine,
            formatTag = formatTag,
            isLiked = isLiked,
            isPlaying = isPlaying, // Tracks real playing/paused state
            accentColor = PlayerPlaceholderDefaults.DEFAULT_ACCENT,
            surfaceColor = PlayerPlaceholderDefaults.DEFAULT_SURFACE,
            onSurfaceColor = PlayerPlaceholderDefaults.DEFAULT_ON_SURFACE,
        )
    }
}

