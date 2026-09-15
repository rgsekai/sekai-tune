/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.buddy

data class BuddyRequest(
    val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val fromDisplayName: String = "",
    val toDisplayName: String = "",
    val status: String = "pending",
    val createdAt: Long = 0L,
)

data class Buddy(
    val uid: String = "",
    val displayName: String = "",
    val addedAt: Long = 0L,
)

data class TogetherSessionSummary(
    val sessionId: String = "",
    val code: String = "",
    val hostId: String = "",
    val hostDisplayName: String = "",
    val createdAt: Long = 0L,
)
