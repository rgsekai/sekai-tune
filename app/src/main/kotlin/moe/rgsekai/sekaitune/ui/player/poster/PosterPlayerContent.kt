package moe.rgsekai.sekaitune.ui.player.poster

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.SyncLikesToSpotifyKey
import moe.rgsekai.sekaitune.lyrics.LyricsEntry
import moe.rgsekai.sekaitune.playback.PlayerConnection
import moe.rgsekai.sekaitune.ui.player.flagship.AmbientBackdrop
import moe.rgsekai.sekaitune.ui.player.flagship.FollowPicture
import moe.rgsekai.sekaitune.ui.player.flagship.rememberAmbientClock
import moe.rgsekai.sekaitune.ui.player.flagship.rememberAmbientColors
import moe.rgsekai.sekaitune.ui.player.styles.PlayerPreviewData
import moe.rgsekai.sekaitune.utils.rememberPreference

/**
 * Poster Player UI layout:
 * - Ambient backdrop softly drifting with artwork tones.
 * - Huge circle crop artwork swung from the top-left corner.
 * - Circular seek bar arc / ring tracking progress with timestamps ("0:00", "3:17") and touch scrubbing.
 * - Bold expressive synced lyrics / title with word-by-word glow and smooth transitions.
 * - Vertical 4-pill action stack on the bottom right (Like, Prev, Play/Pause, Next).
 */
@Composable
fun PosterPlayerContent(
    data: PlayerPreviewData,
    onLikeClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSeek: ((Long) -> Unit)? = null,
    playerConnection: PlayerConnection? = null,
    lyricsSyncOffset: Int = 0,
    onOpenLyrics: (() -> Unit)? = null,
    onOpenQueue: (() -> Unit)? = null,
    isPlayerExpanded: Boolean = true,
) {
    val foreground = Color.White
    val syncLikesToSpotify by rememberPreference(SyncLikesToSpotifyKey, false)

    val artworkUrl = (data.artworkModel as? String) ?: data.artworkModel?.toString()
    val ambientColors = rememberAmbientColors()
    FollowPicture(ambientColors, artworkUrl)
    val ambientClock = rememberAmbientClock(running = data.isPlaying && isPlayerExpanded)

    // Observe real-time lyrics from playerConnection if connected
    val currentLyricsEntity by playerConnection?.currentLyrics?.collectAsStateWithLifecycle(initialValue = null)
        ?: remember { mutableStateOf(null) }
    val currentTrackId = playerConnection?.mediaMetadata?.value?.id

    val lyricsText = remember(currentLyricsEntity, currentTrackId) {
        currentLyricsEntity?.takeIf { currentTrackId == null || it.id == currentTrackId }?.lyrics
    }

    val parsedLyrics = remember(lyricsText, data.durationMs) {
        if (!lyricsText.isNullOrBlank() && lyricsText != moe.rgsekai.sekaitune.db.entities.LyricsEntity.LYRICS_NOT_FOUND) {
            val parsed = when {
                moe.rgsekai.sekaitune.lyrics.LyricsUtils.isTtml(lyricsText) ->
                    moe.rgsekai.sekaitune.lyrics.LyricsUtils.parseTtml(lyricsText, (data.durationMs / 1000L).toInt())
                moe.rgsekai.sekaitune.lyrics.LyricsUtils.isLineSyncedLrc(lyricsText) ->
                    moe.rgsekai.sekaitune.lyrics.LyricsUtils.parseLyrics(lyricsText)
                else -> emptyList()
            }
            parsed.filter { !it.isBackground && it.text.isNotBlank() }
        } else {
            emptyList()
        }
    }

    val effectiveLines = remember(parsedLyrics, data.lyricLine) {
        if (parsedLyrics.isNotEmpty()) parsedLyrics
        else if (data.lyricLine.isNotBlank()) listOf(LyricsEntry(0L, data.lyricLine))
        else emptyList()
    }

    val positionMsProvider: () -> Long = remember(playerConnection, data.progressMs, lyricsSyncOffset) {
        { (playerConnection?.player?.currentPosition ?: data.progressMs) + lyricsSyncOffset.toLong() }
    }
    val seekPositionMsProvider: () -> Long = remember(playerConnection, data.progressMs) {
        { playerConnection?.player?.currentPosition ?: data.progressMs }
    }
    val livePosition = rememberLivePosition(
        positionMsProvider = positionMsProvider,
        isPlaying = data.isPlaying,
    )

    val ringState = rememberCoverRingState()

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val w = maxWidth
        val h = maxHeight
        val density = LocalDensity.current
        val wPx = with(density) { w.toPx() }

        // Center and radius of circle cover swung in from top-left
        val centerOffset = Offset(0.13f * wPx, 0.06f * wPx)
        val radiusPx = 1.14f * wPx

        val shape = remember(centerOffset, radiusPx) {
            GenericShape { _, _ ->
                addOval(
                    Rect(
                        centerOffset.x - radiusPx,
                        centerOffset.y - radiusPx,
                        centerOffset.x + radiusPx,
                        centerOffset.y + radiusPx,
                    ),
                )
            }
        }

        // Layer 0: Ambient Backdrop drifting softly
        AmbientBackdrop(
            colors = ambientColors,
            modifier = Modifier.fillMaxSize(),
            time = ambientClock,
        )

        // Layer 0.5: Soft dark gradient overlay for optimal typography contrast and depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.42f to Color.Black.copy(alpha = 0.28f),
                        1.0f to Color.Black.copy(alpha = 0.82f),
                    ),
                ),
        )

        // Layer 1: Circle Cover Artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(w * 1.22f)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.9f to Color.Black.copy(alpha = 0.28f),
                            1f to Color.Transparent,
                            center = centerOffset + Offset(0f, 10.dp.toPx()),
                            radius = radiusPx * 1.06f,
                        ),
                        radius = radiusPx * 1.06f,
                        center = centerOffset + Offset(0f, 10.dp.toPx()),
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .drawWithContent {
                        drawContent()
                        drawCircle(
                            brush = Brush.radialGradient(
                                0.82f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.32f),
                                center = centerOffset,
                                radius = radiusPx,
                            ),
                            radius = radiusPx,
                            center = centerOffset,
                        )
                    },
            ) {
                AsyncImage(
                    model = data.artworkModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Layer 2: Cover Rim Interactive Progress Arc with Timestamps
        CoverRing(
            state = ringState,
            circle = RingCircle(centerOffset, radiusPx),
            durationMs = data.durationMs,
            positionMsProvider = seekPositionMsProvider,
            onSeek = { ms ->
                onSeek?.invoke(ms) ?: playerConnection?.player?.seekTo(ms)
            },
            isPlaying = data.isPlaying,
            trackId = currentTrackId,
            interactive = isPlayerExpanded,
            modifier = Modifier.fillMaxSize(),
        )

        // Layer 3: Top Menu Option Button (3-dot Menu)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 20.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onMenuClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = "Options",
                tint = foreground,
                modifier = Modifier.size(20.dp),
            )
        }

        // Layer 4: Bold Expressive Synced Lyrics / Title
        val wordsTop = (w * 1.22f).coerceAtMost(h * 0.58f)
        val pillsWidth = 54.dp
        val bottomMargin = 42.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = wordsTop + 8.dp,
                    start = 24.dp,
                    end = 22.dp + pillsWidth + 16.dp,
                    bottom = bottomMargin,
                ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PosterText(
                    lines = effectiveLines,
                    position = livePosition,
                    title = data.title,
                    artist = data.artist,
                    color = foreground,
                    baseSize = 40.sp,
                    gap = 10.dp,
                    push = { Offset(0f, ringState.expand.value * with(density) { 30.dp.toPx() }) },
                )
            }
        }

        // Layer 5: Vertical 4-Pill Action Stack on the Bottom Right
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = bottomMargin),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val posterLikeIcon =
                if (syncLikesToSpotify) {
                    if (data.isLiked) R.drawable.check else R.drawable.add
                } else {
                    if (data.isLiked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill
                }

            // Heart / Like Pill
            PosterPillButton(
                icon = posterLikeIcon,
                onClick = onLikeClick,
                iconSize = 20.dp,
            )

            // Skip Previous Pill
            PosterPillButton(
                icon = R.drawable.flagship_skip_previous,
                onClick = onPreviousClick,
                iconSize = 22.dp,
            )

            // Play / Pause Pill
            PosterPillButton(
                icon = if (data.isPlaying) R.drawable.flagship_pause else R.drawable.flagship_play,
                onClick = onPlayPauseClick,
                iconSize = 22.dp,
            )

            // Skip Next Pill
            PosterPillButton(
                icon = R.drawable.flagship_skip_next,
                onClick = onNextClick,
                iconSize = 22.dp,
            )
        }
    }
}

@Composable
private fun PosterPillButton(
    icon: Int,
    onClick: () -> Unit,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .size(width = 54.dp, height = 40.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}
