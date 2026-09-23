/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import moe.rgsekai.sekaitune.MainActivity
import moe.rgsekai.sekaitune.R
import java.io.File

internal data class WidgetPlaybackState(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val isAvailable: Boolean,
    val isLiked: Boolean,
    val playbackPosition: Float,
    val artPath: String?,
    val dominantColor: Int?,
)

internal fun Preferences.toWidgetPlaybackState(context: Context): WidgetPlaybackState =
    WidgetPlaybackState(
        title = this[MusicWidgetKeys.TRACK_TITLE] ?: context.getString(R.string.no_track_playing),
        artist = this[MusicWidgetKeys.TRACK_ARTIST].orEmpty(),
        isPlaying = this[MusicWidgetKeys.IS_PLAYING] ?: false,
        isBuffering = this[MusicWidgetKeys.IS_BUFFERING] ?: false,
        isAvailable = this[MusicWidgetKeys.IS_AVAILABLE] ?: false,
        isLiked = this[MusicWidgetKeys.IS_LIKED] ?: false,
        playbackPosition = (this[MusicWidgetKeys.PLAYBACK_POSITION] ?: 0f).coerceIn(0f, 1f),
        artPath = this[MusicWidgetKeys.ART_PATH],
        dominantColor = this[MusicWidgetKeys.DOMINANT_COLOR],
    )

internal data class WidgetPalette(
    val surface: ColorProvider,
    val onSurface: ColorProvider,
    val surfaceVariant: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val primary: ColorProvider,
    val onPrimary: ColorProvider,
    val primaryContainer: ColorProvider,
    val onPrimaryContainer: ColorProvider,
    val secondaryContainer: ColorProvider,
    val onSecondaryContainer: ColorProvider,
    val outline: ColorProvider,
    val progress: ColorProvider,
    val progressTrack: ColorProvider,
)

@Composable
internal fun rememberWidgetPalette(dominantColor: Int?): WidgetPalette =
    remember(dominantColor) {
        val seed = dominantColor?.let { Color(it) } ?: Color(0xFFB3181C)
        val scheme = dynamicColorScheme(
            seedColor = seed,
            isDark = true,
            style = PaletteStyle.TonalSpot,
        )

        val primary = scheme.primary
        val onPrimary = scheme.onPrimary
        val primaryContainer = scheme.primaryContainer
        val onPrimaryContainer = scheme.onPrimaryContainer
        val secondaryContainer = scheme.secondaryContainer
        val onSecondaryContainer = scheme.onSecondaryContainer
        val surface = scheme.surface
        val onSurface = scheme.onSurface
        val surfaceVariant = scheme.surfaceVariant
        val onSurfaceVariant = scheme.onSurfaceVariant
        val outline = scheme.outline

        WidgetPalette(
            surface = ColorProvider(surface),
            onSurface = ColorProvider(onSurface),
            surfaceVariant = ColorProvider(surfaceVariant),
            onSurfaceVariant = ColorProvider(onSurfaceVariant),
            primary = ColorProvider(primary),
            onPrimary = ColorProvider(onPrimary),
            primaryContainer = ColorProvider(primaryContainer),
            onPrimaryContainer = ColorProvider(onPrimaryContainer),
            secondaryContainer = ColorProvider(secondaryContainer),
            onSecondaryContainer = ColorProvider(onSecondaryContainer),
            outline = ColorProvider(outline),
            progress = ColorProvider(primary),
            progressTrack = ColorProvider(outline.copy(alpha = 0.25f)),
        )
    }

@Composable
internal fun WidgetArtwork(
    artPath: String?,
    context: Context,
    contentDescription: String,
    targetSize: Dp,
    cornerRadius: Dp,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    fallbackIconSize: Dp = targetSize * 0.45f,
) {
    val bitmap = artPath?.let { WidgetArtworkCache.decode(it, context, targetSize) }

    Box(
        modifier =
            modifier
                .background(palette.surfaceVariant)
                .cornerRadius(cornerRadius),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(cornerRadius),
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.music_note),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(palette.onSurfaceVariant),
                modifier = GlanceModifier.size(fallbackIconSize),
            )
        }
    }
}

@Composable
internal fun WidgetControlButton(
    modifier: GlanceModifier,
    action: Action,
    @DrawableRes icon: Int,
    contentDescription: String,
    backgroundColor: ColorProvider,
    contentColor: ColorProvider,
    cornerRadius: Dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier =
            modifier
                .background(backgroundColor)
                .cornerRadius(cornerRadius)
                .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(contentColor),
            modifier = GlanceModifier.size(iconSize),
        )
    }
}

internal fun openSekaiTuneAction(context: Context): Action =
    actionStartActivity<MainActivity>()

internal fun playPauseAction(): Action = actionRunCallback<PlayPauseAction>()

internal fun skipNextAction(): Action = actionRunCallback<SkipNextAction>()

internal fun skipPreviousAction(): Action = actionRunCallback<SkipPrevAction>()

internal fun likeToggleAction(): Action = actionRunCallback<LikeAction>()

private object WidgetArtworkCache {
    private const val CacheSizeBytes = 4 * 1024 * 1024

    private val cache =
        object : LruCache<String, Bitmap>(CacheSizeBytes) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.byteCount
        }

    fun decode(
        path: String,
        context: Context,
        targetSize: Dp,
    ): Bitmap? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null

        val targetPx =
            (targetSize.value * context.resources.displayMetrics.density)
                .toInt()
                .coerceAtLeast(64)
        val cacheKey = "${file.absolutePath}:${file.lastModified()}:$targetPx"
        cache.get(cacheKey)?.let { return it }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options =
            BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds, targetPx, targetPx)
            }
        return BitmapFactory.decodeFile(file.absolutePath, options)?.also {
            cache.put(cacheKey, it)
        }
    }
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    requestedWidth: Int,
    requestedHeight: Int,
): Int {
    var sampleSize = 1
    val width = options.outWidth
    val height = options.outHeight

    if (height > requestedHeight || width > requestedWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / sampleSize >= requestedHeight && halfWidth / sampleSize >= requestedWidth) {
            sampleSize *= 2
        }
    }

    return sampleSize.coerceAtLeast(1)
}
