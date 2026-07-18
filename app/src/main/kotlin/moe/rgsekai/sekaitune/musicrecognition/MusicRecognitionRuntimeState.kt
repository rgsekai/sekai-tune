/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.musicrecognition

internal enum class BackgroundRecognitionState {
    Idle,
    Listening,
    Processing,
}

internal object MusicRecognitionRuntimeState {
    @Volatile
    var state: BackgroundRecognitionState = BackgroundRecognitionState.Idle
        private set

    fun update(state: BackgroundRecognitionState) {
        this.state = state
    }
}




