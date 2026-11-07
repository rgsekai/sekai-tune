/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.scrobble

import kotlinx.serialization.Serializable

@Serializable
data class ScrobbleItem(
    val artist: String,
    val title: String,
    val album: String? = null,
    val durationSeconds: Int? = null,
    val timestamp: Long = System.currentTimeMillis() / 1000,
)

data class ScrobbleSession(
    val username: String,
    val sessionKey: String,
)

class ScrobblerApiException(
    val code: Int,
    message: String,
) : Exception(message) {
    val isSessionInvalid: Boolean get() = code == 9
    val isAuthFailed: Boolean get() = code == 4
    val isTemporary: Boolean get() = code in setOf(-1, 8, 11, 16, 29)
}

