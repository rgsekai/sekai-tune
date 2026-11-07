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
    val isSessionInvalid: Boolean get() = code == INVALID_SESSION
    val isAuthFailed: Boolean get() = code == AUTH_FAILED
    val isTemporary: Boolean get() = code in TemporaryCodes

    companion object {
        const val SERVER_ERROR = -1
        const val AUTH_FAILED = 4
        const val INVALID_PARAMETERS = 6
        const val INVALID_RESOURCE = 7
        const val INVALID_SESSION = 9
        val TemporaryCodes = setOf(SERVER_ERROR, 8, 11, 16, 29)
    }
}

