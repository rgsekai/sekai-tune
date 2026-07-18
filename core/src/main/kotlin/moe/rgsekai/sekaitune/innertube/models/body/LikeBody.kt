/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.innertube.models.body

import kotlinx.serialization.Serializable
import moe.rgsekai.sekaitune.innertube.models.Context

@Serializable
data class LikeBody(
    val context: Context,
    val target: Target,
) {
    @Serializable
    sealed class Target {
        @Serializable
        data class VideoTarget(
            val videoId: String,
        ) : Target()

        @Serializable
        data class PlaylistTarget(
            val playlistId: String,
        ) : Target()
    }
}




