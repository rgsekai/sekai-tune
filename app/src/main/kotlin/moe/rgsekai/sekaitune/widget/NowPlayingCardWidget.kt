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
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import moe.rgsekai.sekaitune.R

class NowPlayingCardWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            NowPlayingCardContent(context)
        }
    }
}

class NowPlayingCardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingCardWidget()
}

@Composable
private fun NowPlayingCardContent(context: Context) {
    val prefs = currentState<Preferences>()
    val state = prefs.toWidgetPlaybackState(context)
    val palette = rememberWidgetPalette(state.dominantColor)
    val size = LocalSize.current

    val isLarge = size.height >= 140.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(palette.surface),
    ) {
        if (isLarge) {
            NowPlayingCardLargeLayout(
                state = state,
                palette = palette,
                context = context,
                size = size,
            )
        } else {
            NowPlayingCardCompactLayout(
                state = state,
                palette = palette,
                context = context,
                size = size,
            )
        }
    }
}

@Composable
private fun NowPlayingCardCompactLayout(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    size: DpSize,
) {
    val artSize = (size.height - 18.dp).coerceIn(46.dp, 84.dp)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. Left Artwork
            WidgetArtwork(
                artPath = state.artPath,
                context = context,
                contentDescription = context.getString(R.string.album_cover_desc),
                targetSize = artSize,
                cornerRadius = 14.dp,
                palette = palette,
                modifier = GlanceModifier.size(artSize),
            )

            Spacer(GlanceModifier.width(12.dp))

            // 2. Middle Content (Metadata + Controls + Progress)
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            ) {
                Spacer(GlanceModifier.height(2.dp))

                // Title and Artist
                Column(
                    modifier = GlanceModifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.title,
                        style = TextStyle(
                            color = palette.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    if (state.artist.isNotBlank()) {
                        Spacer(GlanceModifier.height(1.dp))
                        Text(
                            text = state.artist,
                            style = TextStyle(
                                color = palette.onSurfaceVariant,
                                fontSize = 11.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                }

                Spacer(GlanceModifier.defaultWeight())

                // Controls row
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WidgetControlButton(
                        modifier = GlanceModifier.size(36.dp),
                        action = skipPreviousAction(),
                        icon = R.drawable.skip_previous,
                        contentDescription = context.getString(R.string.widget_previous),
                        backgroundColor = ColorProvider(Color.Transparent),
                        contentColor = palette.onSurface,
                        cornerRadius = 18.dp,
                        iconSize = 24.dp,
                    )

                    Spacer(GlanceModifier.width(16.dp))

                    WidgetControlButton(
                        modifier = GlanceModifier.size(36.dp),
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
                        contentColor = palette.onSurface,
                        cornerRadius = 18.dp,
                        iconSize = 28.dp,
                    )

                    Spacer(GlanceModifier.width(16.dp))

                    WidgetControlButton(
                        modifier = GlanceModifier.size(36.dp),
                        action = skipNextAction(),
                        icon = R.drawable.skip_next,
                        contentDescription = context.getString(R.string.next),
                        backgroundColor = ColorProvider(Color.Transparent),
                        contentColor = palette.onSurface,
                        cornerRadius = 18.dp,
                        iconSize = 24.dp,
                    )
                }

                // Progress Indicator
                if (state.isAvailable && state.playbackPosition > 0f) {
                    Spacer(GlanceModifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = state.playbackPosition,
                        color = palette.progress,
                        backgroundColor = palette.progressTrack,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .cornerRadius(2.dp),
                    )
                }

                Spacer(GlanceModifier.height(3.dp))
            }

            Spacer(GlanceModifier.width(8.dp))

            // 3. Right Like/Heart Toggle
            WidgetControlButton(
                modifier = GlanceModifier.size(40.dp),
                action = likeToggleAction(),
                icon = if (state.isLiked) R.drawable.favorite else R.drawable.favorite_border,
                contentDescription = context.getString(if (state.isLiked) R.string.liked else R.string.filter_liked),
                backgroundColor = ColorProvider(Color.Transparent),
                contentColor = if (state.isLiked) palette.primary else palette.onSurfaceVariant,
                cornerRadius = 20.dp,
                iconSize = 24.dp,
            )
        }
    }
}

@Composable
private fun NowPlayingCardLargeLayout(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    size: DpSize,
) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
    ) {
        // Full bleed background artwork
        WidgetArtwork(
            artPath = state.artPath,
            context = context,
            contentDescription = context.getString(R.string.album_cover_desc),
            targetSize = size.height,
            cornerRadius = 24.dp,
            palette = palette,
            modifier = GlanceModifier.fillMaxSize(),
        )

        // Scrim overlay for legibility
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(palette.surface.getColor(context).copy(alpha = 0.82f).let { ColorProvider(it) })
                .padding(16.dp),
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
            ) {
                // Top row: Metadata + Like button
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                    ) {
                        Text(
                            text = state.title,
                            style = TextStyle(
                                color = palette.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 2,
                        )
                        if (state.artist.isNotBlank()) {
                            Spacer(GlanceModifier.height(2.dp))
                            Text(
                                text = state.artist,
                                style = TextStyle(
                                    color = palette.onSurfaceVariant,
                                    fontSize = 13.sp,
                                ),
                                maxLines = 1,
                            )
                        }
                    }

                    Spacer(GlanceModifier.width(8.dp))

                    WidgetControlButton(
                        modifier = GlanceModifier.size(42.dp),
                        action = likeToggleAction(),
                        icon = if (state.isLiked) R.drawable.favorite else R.drawable.favorite_border,
                        contentDescription = context.getString(if (state.isLiked) R.string.liked else R.string.filter_liked),
                        backgroundColor = palette.secondaryContainer,
                        contentColor = if (state.isLiked) palette.primary else palette.onSecondaryContainer,
                        cornerRadius = 21.dp,
                        iconSize = 24.dp,
                    )
                }

                Spacer(GlanceModifier.defaultWeight())

                // Middle: Playback controls centered
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WidgetControlButton(
                        modifier = GlanceModifier.size(44.dp),
                        action = skipPreviousAction(),
                        icon = R.drawable.skip_previous,
                        contentDescription = context.getString(R.string.widget_previous),
                        backgroundColor = palette.secondaryContainer,
                        contentColor = palette.onSecondaryContainer,
                        cornerRadius = 22.dp,
                        iconSize = 22.dp,
                    )

                    Spacer(GlanceModifier.width(16.dp))

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
                        backgroundColor = palette.primaryContainer,
                        contentColor = palette.onPrimaryContainer,
                        cornerRadius = 27.dp,
                        iconSize = 28.dp,
                    )

                    Spacer(GlanceModifier.width(16.dp))

                    WidgetControlButton(
                        modifier = GlanceModifier.size(44.dp),
                        action = skipNextAction(),
                        icon = R.drawable.skip_next,
                        contentDescription = context.getString(R.string.next),
                        backgroundColor = palette.secondaryContainer,
                        contentColor = palette.onSecondaryContainer,
                        cornerRadius = 22.dp,
                        iconSize = 22.dp,
                    )
                }

                Spacer(GlanceModifier.defaultWeight())

                // Bottom: Progress bar
                if (state.isAvailable && state.playbackPosition > 0f) {
                    LinearProgressIndicator(
                        progress = state.playbackPosition,
                        color = palette.progress,
                        backgroundColor = palette.progressTrack,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .cornerRadius(2.dp),
                    )
                }
            }
        }
    }
}
