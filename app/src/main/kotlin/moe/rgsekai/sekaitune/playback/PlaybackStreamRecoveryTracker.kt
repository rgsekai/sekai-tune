/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback

internal class PlaybackStreamRecoveryTracker {
    private var attemptedMediaId: String? = null
    private var retryCount = 0

    fun registerRetryAttempt(mediaId: String): Boolean {
        if (attemptedMediaId == mediaId) {
            retryCount++
            return retryCount <= 3
        }
        attemptedMediaId = mediaId
        retryCount = 1
        return true
    }

    fun onPlaybackRecovered(mediaId: String?) {
        if (mediaId != null && attemptedMediaId == mediaId) {
            attemptedMediaId = null
            retryCount = 0
        }
    }

    fun onMediaItemChanged(currentMediaId: String?) {
        if (attemptedMediaId != currentMediaId) {
            attemptedMediaId = null
            retryCount = 0
        }
    }
}




