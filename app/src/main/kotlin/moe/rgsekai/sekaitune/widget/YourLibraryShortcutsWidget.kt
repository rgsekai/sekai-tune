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
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import moe.rgsekai.sekaitune.R

class YourLibraryShortcutsWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            YourLibraryShortcutsContent(context)
        }
    }
}

class YourLibraryShortcutsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = YourLibraryShortcutsWidget()
}

@Composable
private fun YourLibraryShortcutsContent(context: Context) {
    val prefs = currentState<Preferences>()
    val playbackState = prefs.toWidgetPlaybackState(context)
    val shortcutsSnapshot = prefs.toWidgetShortcutsSnapshot()
    val palette = rememberWidgetPalette(playbackState.dominantColor)
    val size = LocalSize.current

    val showFirstRow = size.height >= 170.dp
    val showSecondRow = size.height >= 300.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(palette.surface)
            .padding(14.dp),
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. Now Playing Header (Artwork, Title+Artist, Progress with Timestamps, Transport Controls)
            ShortcutsNowPlayingHeader(
                state = playbackState,
                palette = palette,
                context = context,
            )

            // 2. Row 1 of Shortcut Tiles (Slots 1-4, shown when user resizes widget height >= 170dp)
            if (showFirstRow) {
                Spacer(modifier = GlanceModifier.height(14.dp))
                ShortcutTilesRow(
                    items = listOf(
                        shortcutsSnapshot.shortcut1,
                        shortcutsSnapshot.shortcut2,
                        shortcutsSnapshot.shortcut3,
                        shortcutsSnapshot.shortcut4,
                    ),
                    palette = palette,
                    context = context,
                )
            }

            // 3. Row 2 of Shortcut Tiles (Slots 5-8, shown when widget height increases further >= 260dp)
            if (showSecondRow) {
                Spacer(modifier = GlanceModifier.height(10.dp))
                ShortcutTilesRow(
                    items = listOf(
                        shortcutsSnapshot.shortcut5,
                        shortcutsSnapshot.shortcut6,
                        shortcutsSnapshot.shortcut7,
                        shortcutsSnapshot.shortcut8,
                    ),
                    palette = palette,
                    context = context,
                )
            }
        }
    }
}

@Composable
private fun ShortcutsNowPlayingHeader(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
) {
    Column(
        modifier = GlanceModifier.fillMaxWidth(),
    ) {
        // 1. Top Section: Album Artwork on Left, Title + Artist + Progress Bar Timeline on Right
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Album Art (Untouched size 84dp, cornerRadius 14dp)
            WidgetArtwork(
                artPath = state.artPath,
                context = context,
                contentDescription = context.getString(R.string.album_cover_desc),
                targetSize = 84.dp,
                cornerRadius = 14.dp,
                palette = palette,
                modifier = GlanceModifier.size(84.dp),
            )

            Spacer(modifier = GlanceModifier.width(14.dp))

            // Right Column: Title & Artist (moved up slightly) + Progress Timeline below texts
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.title,
                    maxLines = 1,
                    style = TextStyle(
                        color = palette.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                if (state.artist.isNotBlank()) {
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = state.artist,
                        maxLines = 1,
                        style = TextStyle(
                            color = palette.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                    )
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // Progress Bar right below texts on the right side of album art
                LinearProgressIndicator(
                    progress = state.playbackPosition.coerceIn(0f, 1f),
                    color = palette.progress,
                    backgroundColor = palette.progressTrack,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .cornerRadius(2.dp),
                )

                Spacer(modifier = GlanceModifier.height(2.dp))

                // Timestamps: Elapsed (Left) & Total Duration (Right)
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val durationSec = state.duration
                    val elapsedSec = (durationSec * state.playbackPosition).toInt().coerceAtLeast(0)
                    val elapsedText = formatWidgetTime(elapsedSec)
                    val durationText = if (durationSec > 0) formatWidgetTime(durationSec) else "--:--"

                    Text(
                        text = elapsedText,
                        style = TextStyle(
                            color = palette.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                    )

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Text(
                        text = durationText,
                        style = TextStyle(
                            color = palette.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(12.dp))

        // 2. Full-Width Transport Controls Row Below (Previous, Play/Pause, Next, Like)
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Previous Button (enlarged & shifted right)
            WidgetControlButton(
                modifier = GlanceModifier.size(40.dp),
                action = skipPreviousAction(),
                icon = R.drawable.widget_nav_previous,
                contentDescription = context.getString(R.string.widget_previous),
                backgroundColor = ColorProvider(Color.Transparent),
                contentColor = palette.onSurface,
                cornerRadius = 20.dp,
                iconSize = 32.dp,
            )

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Play / Pause Button (enlarged & shifted right)
            WidgetControlButton(
                modifier = GlanceModifier.size(44.dp),
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
                cornerRadius = 22.dp,
                iconSize = 36.dp,
            )

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Next Button (enlarged & shifted right)
            WidgetControlButton(
                modifier = GlanceModifier.size(40.dp),
                action = skipNextAction(),
                icon = R.drawable.widget_nav_next,
                contentDescription = context.getString(R.string.next),
                backgroundColor = ColorProvider(Color.Transparent),
                contentColor = palette.onSurface,
                cornerRadius = 20.dp,
                iconSize = 32.dp,
            )

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Like / Heart Button (Vibrant Red when liked)
            WidgetControlButton(
                modifier = GlanceModifier.size(38.dp),
                action = likeToggleAction(),
                icon = if (state.isLiked) R.drawable.favorite else R.drawable.favorite_border,
                contentDescription = context.getString(if (state.isLiked) R.string.liked else R.string.liked_songs),
                backgroundColor = ColorProvider(Color.Transparent),
                contentColor = if (state.isLiked) ColorProvider(Color(0xFFFF3B30)) else palette.onSurface,
                cornerRadius = 19.dp,
                iconSize = 30.dp,
            )
        }
    }
}

@Composable
private fun ShortcutTilesRow(
    items: List<WidgetShortcutItem>,
    palette: WidgetPalette,
    context: Context,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Spacer(modifier = GlanceModifier.width(8.dp))
            }
            ShortcutTile(
                item = item,
                palette = palette,
                context = context,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@Composable
private fun ShortcutTile(
    item: WidgetShortcutItem,
    palette: WidgetPalette,
    context: Context,
    modifier: GlanceModifier = GlanceModifier,
) {
    val clickModifier = when (item.type) {
        WidgetShortcutType.LIKED_SONGS -> GlanceModifier.clickable(actionRunCallback<PlayShortcutLikedSongsAction>())
        WidgetShortcutType.PLAYLIST -> GlanceModifier.clickable(
            actionRunCallback<PlayShortcutPlaylistAction>(
                actionParametersOf(PlayShortcutPlaylistAction.KEY_TARGET_ID to item.targetId),
            ),
        )
        WidgetShortcutType.SONG -> GlanceModifier.clickable(
            actionRunCallback<PlayShortcutSongAction>(
                actionParametersOf(PlayShortcutSongAction.KEY_TARGET_ID to item.targetId),
            ),
        )
        WidgetShortcutType.EMPTY -> GlanceModifier
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .then(clickModifier)
            .padding(horizontal = 2.dp),
    ) {
        // Square Cover Thumbnail
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(78.dp)
                .cornerRadius(10.dp)
                .background(ColorProvider(Color(0x33000000))),
            contentAlignment = Alignment.Center,
        ) {
            when {
                item.type == WidgetShortcutType.LIKED_SONGS -> {
                    Image(
                        provider = ImageProvider(R.drawable.widget_liked_songs_tile),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier.fillMaxSize().cornerRadius(10.dp),
                    )
                }
                item.artPathOrUrl != null -> {
                    WidgetArtwork(
                        artPath = item.artPathOrUrl,
                        context = context,
                        contentDescription = item.title,
                        targetSize = 78.dp,
                        cornerRadius = 10.dp,
                        palette = palette,
                        modifier = GlanceModifier.fillMaxSize(),
                    )
                }
                item.type == WidgetShortcutType.PLAYLIST -> {
                    Image(
                        provider = ImageProvider(R.drawable.queue_music),
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        modifier = GlanceModifier.size(28.dp),
                    )
                }
                item.type == WidgetShortcutType.SONG -> {
                    Image(
                        provider = ImageProvider(R.drawable.music_note),
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        modifier = GlanceModifier.size(28.dp),
                    )
                }
                else -> {
                    Image(
                        provider = ImageProvider(R.drawable.library_music),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = GlanceModifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(5.dp))

        // Title Text
        Text(
            text = item.title.ifBlank { "—" },
            maxLines = 1,
            style = TextStyle(
                color = palette.onSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

private fun formatWidgetTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

