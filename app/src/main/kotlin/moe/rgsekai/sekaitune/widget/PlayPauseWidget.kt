/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.compose.ui.graphics.Color
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.unit.ColorProvider
import moe.rgsekai.sekaitune.R

class PlayPauseWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            PlayPauseWidgetContent(context)
        }
    }
}

class PlayPauseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlayPauseWidget()
}

@Composable
private fun PlayPauseWidgetContent(context: Context) {
    val prefs = currentState<Preferences>()
    val isPlaying = prefs[MusicWidgetKeys.IS_PLAYING] ?: false
    val isBuffering = prefs[MusicWidgetKeys.IS_BUFFERING] ?: false
    val size = LocalSize.current

    val minDimension = min(size.width, size.height)
    val buttonSize = (minDimension - 8.dp).coerceAtLeast(40.dp)
    val cornerRadius = buttonSize / 2
    val iconSize = (buttonSize * 0.48f).coerceAtLeast(20.dp)

    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Liquid Glass Outer Container
        Box(
            modifier = GlanceModifier
                .size(buttonSize)
                .cornerRadius(cornerRadius)
                .background(ImageProvider(R.drawable.widget_liquid_glass_circle))
                .clickable(actionRunCallback<PlayPauseAction>()),
            contentAlignment = Alignment.Center,
        ) {
            // High-contrast clean icon for liquid glass
            Image(
                provider = ImageProvider(
                    when {
                        isBuffering -> R.drawable.more_horiz
                        isPlaying -> R.drawable.pause
                        else -> R.drawable.play
                    }
                ),
                contentDescription = context.getString(
                    when {
                        isBuffering -> R.string.loading
                        isPlaying -> R.string.widget_pause
                        else -> R.string.play
                    },
                ),
                colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(Color.White)),
                modifier = GlanceModifier.size(iconSize),
            )
        }
    }
}
