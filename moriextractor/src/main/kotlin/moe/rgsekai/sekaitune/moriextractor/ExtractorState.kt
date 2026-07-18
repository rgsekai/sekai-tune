/*
 * Sekai Tune (2026)
 * (c) Sekai Tune — github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.moriextractor

sealed interface ExtractorState {
    data object Idle : ExtractorState

    data object Processing : ExtractorState

    data class Success(
        val audioUrl: String,
        val title: String?,
        val thumbnail: String?,
    ) : ExtractorState

    data class Error(
        val message: String,
    ) : ExtractorState
}



