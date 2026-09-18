/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas

import kotlinx.serialization.Serializable

@Serializable
enum class CanvasSource {
    BETTER_LYRICS,
    APPLE_MUSIC,
    TIDAL,
    SPOTIFY,
    ALL;

    fun accepts(provider: CanvasSource?): Boolean =
        provider != null && provider != ALL && (this == ALL || this == provider)

    companion object {
        fun fromPreference(value: String?): CanvasSource =
            if (value == "BOTH") ALL else entries.firstOrNull { it.name == value } ?: ALL
    }
}
