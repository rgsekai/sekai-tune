package moe.rgsekai.sekaitune.ui.player.styles

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import moe.rgsekai.sekaitune.R

/**
 * Available Now Playing visual styles in Sekai Tune.
 * Reuses the existing "playerDesignStyle" preference key.
 *
 * - IMMERSIVE: Full-bleed background with adaptive typography, lyrics, and modern controls.
 */
enum class PlayerStyle(
    val id: String,
    @StringRes val displayNameRes: Int,
    val hasOptions: Boolean,
) {
    IMMERSIVE(
        id = "IMMERSIVE",
        displayNameRes = R.string.player_style_immersive,
        hasOptions = true,
    ),
    FLAGSHIP(
        id = "FLAGSHIP",
        displayNameRes = R.string.player_style_flagship,
        hasOptions = false,
    ),
    POSTER(
        id = "POSTER",
        displayNameRes = R.string.player_style_poster,
        hasOptions = false,
    );

    companion object {
        val DEFAULT = IMMERSIVE

        // Columns for the style selector: column 0 has [IMMERSIVE, FLAGSHIP], column 1 has [POSTER]
        val selectorColumns: List<List<PlayerStyle>> = listOf(
            listOf(IMMERSIVE, FLAGSHIP),
            listOf(POSTER),
        )

        fun fromPreference(raw: String?): PlayerStyle = when (raw) {
            FLAGSHIP.id -> FLAGSHIP
            POSTER.id -> POSTER
            else -> IMMERSIVE
        }
    }
}

/**
 * Universal preview data model consumed by non-interactive player preview cards.
 * Decoupled from PlayerConnection and ExoPlayer state.
 */
@Immutable
data class PlayerPreviewData(
    val title: String,
    val artist: String,
    val artworkModel: Any,
    val durationMs: Long,
    val progressMs: Long,
    val lyricLine: String,
    val formatTag: String,
    val isLiked: Boolean = false,
    val isPlaying: Boolean = false,
    val accentColor: Color = Color(0xFF6B7280),
    val surfaceColor: Color = Color(0xFF1E1E22),
    val onSurfaceColor: Color = Color(0xFFE5E7EB),
)

object PlayerPlaceholderDefaults {
    const val DEFAULT_PROGRESS_FRACTION = 0.40f
    const val DEFAULT_DURATION_MS = 214_000L // 3m 34s
    val DEFAULT_PROGRESS_MS = (DEFAULT_DURATION_MS * DEFAULT_PROGRESS_FRACTION).toLong() // 1m 25s

    @DrawableRes
    val DEFAULT_ARTWORK_RES = R.drawable.ic_player_placeholder_art

    val DEFAULT_ACCENT = Color(0xFF64748B)
    val DEFAULT_SURFACE = Color(0xFF18181B)
    val DEFAULT_ON_SURFACE = Color(0xFFF4F4F5)
}

