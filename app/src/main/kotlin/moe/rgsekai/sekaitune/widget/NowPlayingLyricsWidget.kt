/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.lyrics.LyricsEntry
import moe.rgsekai.sekaitune.lyrics.LyricsUtils
import moe.rgsekai.sekaitune.utils.makeTimeString

class NowPlayingLyricsWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            NowPlayingLyricsContent(context)
        }
    }
}

class NowPlayingLyricsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingLyricsWidget()
}

@Composable
private fun NowPlayingLyricsContent(context: Context) {
    val prefs = currentState<Preferences>()
    val state = prefs.toWidgetPlaybackState(context)
    val palette = rememberWidgetPalette(state.dominantColor)
    val size = LocalSize.current

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(palette.surface)
            .clickable(openSekaiTuneAction(context)),
    ) {
        if (state.showLyrics) {
            FullLyricsLayout(context = context, state = state, palette = palette, size = size)
        } else {
            FullArtCoverLayout(context = context, state = state, palette = palette, size = size)
        }
    }
}

/**
 * Standard View (Image 2 Composition):
 * - Full-bleed album art background with dark gradient scrim.
 * - Bottom area:
 *   - Left: Song title (large bold) + Artist (medium subtitle)
 *   - Right: Floating action pill containing Lyrics speech-bubble button and Heart button (circular filled container for heart when liked)
 *   - Seek / Progress Bar (white/accent progress bar with current position dot/pill)
 *   - Position time row: Left "0:00" (or current time), Center "OPUS" / format pill, Right "3:33" (or total duration)
 *   - Transport Controls row: Previous | Play / Pause | Next (centered large white icons)
 */
@Composable
private fun FullArtCoverLayout(
    context: Context,
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    size: DpSize,
) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
    ) {
        // 1. Full-bleed background artwork
        WidgetArtwork(
            artPath = state.artPath,
            context = context,
            contentDescription = context.getString(R.string.album_cover_desc),
            targetSize = size.height.coerceAtLeast(size.width),
            cornerRadius = 28.dp,
            palette = palette,
            modifier = GlanceModifier.fillMaxSize(),
        )

        // 2. Scrim gradient overlay (smooth vertical gradient scrim matching in-app player)
        Image(
            provider = ImageProvider(R.drawable.widget_art_gradient_scrim),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp),
        )

        // 3. Foreground content
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
            ) {
                // Spacer pushes content to bottom half like in-app full player card
                Spacer(modifier = GlanceModifier.defaultWeight())

                // Metadata + Actions (Lyrics toggle & Heart)
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // Left: Title & Artist
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                    ) {
                        Text(
                            text = state.title.ifBlank { context.getString(R.string.no_track_playing) },
                            maxLines = 2,
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        if (state.artist.isNotBlank()) {
                            Spacer(modifier = GlanceModifier.height(3.dp))
                            Text(
                                text = state.artist,
                                maxLines = 1,
                                style = TextStyle(
                                    color = ColorProvider(Color(0xCCFFFFFF)),
                                    fontSize = 14.sp,
                                ),
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.width(10.dp))

                    // Right: Pill / Action Buttons (Lyrics + Heart)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Lyrics Toggle Button (Speech bubble with quotes inside rounded square)
                        WidgetControlButton(
                            modifier = GlanceModifier.size(42.dp),
                            action = lyricsToggleAction(),
                            icon = R.drawable.lyrics,
                            contentDescription = context.getString(R.string.widget_lyrics),
                            backgroundColor = ColorProvider(Color(0x40FFFFFF)),
                            contentColor = ColorProvider(Color.White),
                            cornerRadius = 21.dp,
                            iconSize = 22.dp,
                        )

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        // Heart Button (Red when liked, white border when unliked)
                        WidgetControlButton(
                            modifier = GlanceModifier.size(42.dp),
                            action = likeToggleAction(),
                            icon = if (state.isLiked) R.drawable.favorite else R.drawable.favorite_border,
                            contentDescription = context.getString(if (state.isLiked) R.string.liked else R.string.filter_liked),
                            backgroundColor = ColorProvider(Color(0x40FFFFFF)),
                            contentColor = if (state.isLiked) ColorProvider(Color(0xFFFF1744)) else ColorProvider(Color.White),
                            cornerRadius = 21.dp,
                            iconSize = 22.dp,
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(16.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = state.playbackPosition.coerceIn(0f, 1f),
                    color = ColorProvider(Color.White),
                    backgroundColor = ColorProvider(Color(0x4DFFFFFF)),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .cornerRadius(2.dp),
                )

                Spacer(modifier = GlanceModifier.height(6.dp))

                // Time and Format Row (0:00   [OPUS]   3:33)
                val currentMs = (state.playbackPosition * state.duration * 1000f).toLong()
                val totalMs = state.duration * 1000L
                val currentStr = if (state.duration > 0) makeTimeString(currentMs) else "0:00"
                val totalStr = if (state.duration > 0) makeTimeString(totalMs) else "0:00"

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = currentStr,
                        style = TextStyle(
                            color = ColorProvider(Color(0xAAFFFFFF)),
                            fontSize = 11.sp,
                        ),
                    )

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    // Center Audio Format Pill
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(10.dp)
                            .background(ColorProvider(Color(0x33FFFFFF)))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "OPUS",
                            style = TextStyle(
                                color = ColorProvider(Color(0xCCFFFFFF)),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Text(
                        text = totalStr,
                        style = TextStyle(
                            color = ColorProvider(Color(0xAAFFFFFF)),
                            fontSize = 11.sp,
                        ),
                    )
                }

                Spacer(modifier = GlanceModifier.height(16.dp))

                // Transport Controls: Previous | Play / Pause | Next
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Previous
                    WidgetControlButton(
                        modifier = GlanceModifier.size(46.dp),
                        action = skipPreviousAction(),
                        icon = R.drawable.skip_previous,
                        contentDescription = context.getString(R.string.widget_previous),
                        backgroundColor = ColorProvider(Color.Transparent),
                        contentColor = ColorProvider(Color.White),
                        cornerRadius = 23.dp,
                        iconSize = 30.dp,
                    )

                    Spacer(modifier = GlanceModifier.width(36.dp))

                    // Play / Pause
                    WidgetControlButton(
                        modifier = GlanceModifier.size(54.dp),
                        action = playPauseAction(),
                        icon = when {
                            state.isBuffering -> R.drawable.more_horiz
                            state.isPlaying -> R.drawable.pause
                            else -> R.drawable.play
                        },
                        contentDescription = context.getString(
                            when {
                                state.isBuffering -> R.string.loading
                                state.isPlaying -> R.string.widget_pause
                                else -> R.string.play
                            },
                        ),
                        backgroundColor = ColorProvider(Color.Transparent),
                        contentColor = ColorProvider(Color.White),
                        cornerRadius = 27.dp,
                        iconSize = 38.dp,
                    )

                    Spacer(modifier = GlanceModifier.width(36.dp))

                    // Next
                    WidgetControlButton(
                        modifier = GlanceModifier.size(46.dp),
                        action = skipNextAction(),
                        icon = R.drawable.skip_next,
                        contentDescription = context.getString(R.string.next),
                        backgroundColor = ColorProvider(Color.Transparent),
                        contentColor = ColorProvider(Color.White),
                        cornerRadius = 23.dp,
                        iconSize = 30.dp,
                    )
                }
            }
        }
    }
}

/**
 * Lyrics Active View (Image 1 Composition):
 * - Dark blue / dominant color atmospheric tinted background.
 * - Center / Upper: Full scrollable synced lyrics with active line in bold bright white, surrounding lines dimmed.
 * - Floating right above progress bar: Lyrics active toggle button + Heart button.
 * - Bottom: Progress bar + Time + Transport controls (Prev | Play/Pause | Next).
 */
@Composable
private fun FullLyricsLayout(
    context: Context,
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    size: DpSize,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(palette.surface)
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
        ) {
            // Top/Middle: Synced Lyrics Area
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.lyricsStatus == "LOADING" -> {
                        Text(
                            text = context.getString(R.string.widget_loading_lyrics),
                            style = TextStyle(
                                color = ColorProvider(Color(0x99FFFFFF)),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                    state.lyricsStatus == "NO_TRACK" || state.title == context.getString(R.string.no_track_playing) -> {
                        Text(
                            text = context.getString(R.string.widget_no_song_playing),
                            style = TextStyle(
                                color = ColorProvider(Color(0x99FFFFFF)),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                    state.lyricsStatus == "NOT_FOUND" || state.lyricsText.isNullOrBlank() -> {
                        Text(
                            text = context.getString(R.string.widget_no_lyrics_found),
                            style = TextStyle(
                                color = ColorProvider(Color(0x99FFFFFF)),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                    else -> {
                        val parsedEntries = parseLyricsFlexible(state.lyricsText)
                        if (parsedEntries.isEmpty()) {
                            val cleanText = LyricsUtils.displayLyricsText(state.lyricsText)
                            Text(
                                text = cleanText.ifBlank { state.lyricsText },
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Start,
                                ),
                            )
                        } else {
                            val positionMs = (state.playbackPosition * state.duration * 1000f).toLong()
                            val currentIdx = LyricsUtils.findCurrentLineIndex(parsedEntries, positionMs)

                            LazyColumn(
                                modifier = GlanceModifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                itemsIndexed(parsedEntries) { index, entry ->
                                    val isCurrent = index == currentIdx
                                    Box(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = entry.text.ifBlank { "♪" },
                                            style = TextStyle(
                                                color = if (isCurrent) ColorProvider(Color.White) else ColorProvider(Color(0x55FFFFFF)),
                                                fontSize = if (isCurrent) 20.sp else 15.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating Action Buttons (Lyrics Active + Heart) aligned to the right above progress bar
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Active Lyrics Toggle Button
                WidgetControlButton(
                    modifier = GlanceModifier.size(40.dp),
                    action = lyricsToggleAction(),
                    icon = R.drawable.lyrics,
                    contentDescription = context.getString(R.string.widget_lyrics),
                    backgroundColor = ColorProvider(Color(0x40FFFFFF)),
                    contentColor = ColorProvider(Color.White),
                    cornerRadius = 20.dp,
                    iconSize = 22.dp,
                )

                Spacer(modifier = GlanceModifier.width(8.dp))

                // Heart Button
                WidgetControlButton(
                    modifier = GlanceModifier.size(40.dp),
                    action = likeToggleAction(),
                    icon = if (state.isLiked) R.drawable.favorite else R.drawable.favorite_border,
                    contentDescription = context.getString(if (state.isLiked) R.string.liked else R.string.filter_liked),
                    backgroundColor = ColorProvider(Color(0x40FFFFFF)),
                    contentColor = if (state.isLiked) ColorProvider(Color(0xFFFF1744)) else ColorProvider(Color.White),
                    cornerRadius = 20.dp,
                    iconSize = 22.dp,
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = state.playbackPosition.coerceIn(0f, 1f),
                color = ColorProvider(Color.White),
                backgroundColor = ColorProvider(Color(0x4DFFFFFF)),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .cornerRadius(2.dp),
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Time Row (0:00 on left)
            val currentMs = (state.playbackPosition * state.duration * 1000f).toLong()
            val currentStr = if (state.duration > 0) makeTimeString(currentMs) else "0:00"

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentStr,
                    style = TextStyle(
                        color = ColorProvider(Color(0xAAFFFFFF)),
                        fontSize = 11.sp,
                    ),
                )
            }

            Spacer(modifier = GlanceModifier.height(14.dp))

            // Transport Controls: Previous | Play / Pause | Next
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Previous
                WidgetControlButton(
                    modifier = GlanceModifier.size(46.dp),
                    action = skipPreviousAction(),
                    icon = R.drawable.skip_previous,
                    contentDescription = context.getString(R.string.widget_previous),
                    backgroundColor = ColorProvider(Color.Transparent),
                    contentColor = ColorProvider(Color.White),
                    cornerRadius = 23.dp,
                    iconSize = 30.dp,
                )

                Spacer(modifier = GlanceModifier.width(36.dp))

                // Play / Pause Toggle
                WidgetControlButton(
                    modifier = GlanceModifier.size(54.dp),
                    action = playPauseAction(),
                    icon = when {
                        state.isBuffering -> R.drawable.more_horiz
                        state.isPlaying -> R.drawable.pause
                        else -> R.drawable.play
                    },
                    contentDescription = context.getString(
                        when {
                            state.isBuffering -> R.string.loading
                            state.isPlaying -> R.string.widget_pause
                            else -> R.string.play
                        },
                    ),
                    backgroundColor = ColorProvider(Color.Transparent),
                    contentColor = ColorProvider(Color.White),
                    cornerRadius = 27.dp,
                    iconSize = 38.dp,
                )

                Spacer(modifier = GlanceModifier.width(36.dp))

                // Next
                WidgetControlButton(
                    modifier = GlanceModifier.size(46.dp),
                    action = skipNextAction(),
                    icon = R.drawable.skip_next,
                    contentDescription = context.getString(R.string.next),
                    backgroundColor = ColorProvider(Color.Transparent),
                    contentColor = ColorProvider(Color.White),
                    cornerRadius = 23.dp,
                    iconSize = 30.dp,
                )
            }
        }
    }
}

/**
 * Parses both TTML XML lyrics and LRC / synced string formats into structured [LyricsEntry] list.
 */
private fun parseLyricsFlexible(lyrics: String?): List<LyricsEntry> {
    if (lyrics.isNullOrBlank()) return emptyList()
    val normalized = LyricsUtils.normalizeLyricsText(lyrics)

    return when {
        LyricsUtils.isTtml(normalized) -> runCatching { LyricsUtils.parseTtml(normalized) }.getOrElse { emptyList() }
        LyricsUtils.isLineSyncedLrc(normalized) -> runCatching { LyricsUtils.parseLyrics(normalized) }.getOrElse { emptyList() }
        else -> emptyList()
    }
}
