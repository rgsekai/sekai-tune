/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.discord

import android.net.Uri
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.abs

data class PresenceActivity(
    val kind: PresenceKind,
    val name: String,
    val details: String?,
    val detailsUrl: String?,
    val state: String?,
    val largeImage: PresencePicture?,
    val largeText: String?,
    val smallImage: PresencePicture?,
    val smallText: String?,
    val startMs: Long?,
    val endMs: Long?,
    val buttons: List<Link>,
) {
    data class Link(val label: String, val url: String)

    fun looksLike(other: PresenceActivity?): Boolean {
        if (other == null) return false
        fun near(a: Long?, b: Long?) = (a == null && b == null) || (a != null && b != null && abs(a - b) < TimeSlackMs)
        return copy(startMs = null, endMs = null) == other.copy(startMs = null, endMs = null) &&
            near(startMs, other.startMs) && near(endMs, other.endMs)
    }

    fun toJson(applicationId: String, largeAsset: String?, smallAsset: String?): JsonObject = buildJsonObject {
        put("name", name)
        put("type", kind.discordType)
        put("application_id", applicationId)
        details?.let { put("details", it) }
        detailsUrl?.let { put("details_url", it) }
        state?.let { put("state", it) }
        if (startMs != null) {
            putJsonObject("timestamps") {
                put("start", startMs)
                endMs?.let { put("end", it) }
            }
        }
        if (largeAsset != null) {
            putJsonObject("assets") {
                put("large_image", largeAsset)
                largeText?.let { put("large_text", it) }
                if (smallAsset != null) {
                    put("small_image", smallAsset)
                    smallText?.let { put("small_text", it) }
                }
            }
        }
        if (buttons.isNotEmpty()) {
            put("buttons", buildJsonArray { buttons.forEach { add(JsonPrimitive(it.label)) } })
            putJsonObject("metadata") {
                put("button_urls", buildJsonArray { buttons.forEach { add(JsonPrimitive(it.url)) } })
            }
        }
    }

    companion object {
        private const val TimeSlackMs = 2_500L
        const val GithubRepoUrl = "https://github.com/rgsekai/sekai-tune"

        fun build(track: NowPlaying, settings: PresenceSettings, nowMs: Long): PresenceActivity {
            val (start, end) = timesOf(track, settings.kind, nowMs)
            return PresenceActivity(
                kind = settings.kind,
                name = PresenceText.line(settings.name, track) ?: PresenceText.AppName,
                details = PresenceText.line(settings.details, track),
                detailsUrl = PresenceButtonTarget.YOUTUBE_MUSIC.linkFor(track).takeIf { track.isYouTube },
                state = PresenceText.line(settings.state, track),
                largeImage = PresenceImages.pick(settings.largeImage, track, nowMs, settings.spin) ?: PresencePicture.Logo,
                largeText = PresenceText.line(settings.largeText, track),
                smallImage = PresenceImages.pick(settings.smallImage, track, nowMs, settings.spin),
                smallText = PresenceText.line(settings.smallText, track),
                startMs = start,
                endMs = end,
                buttons = listOf(settings.button1, settings.button2).mapNotNull { button ->
                    PresenceText.line(button.text, track, maxLength = 32)
                        ?.let { Link(label = it, url = button.target.linkFor(track)) }
                },
            )
        }

        private fun timesOf(track: NowPlaying, kind: PresenceKind, nowMs: Long): Pair<Long?, Long?> {
            if (!track.isPlaying || track.durationMs <= 0) return null to null
            val speed = track.speed.coerceAtLeast(0.1f)
            val start = nowMs - (track.positionAt(nowMs) / speed).toLong()
            val end = start + (track.durationMs / speed).toLong()
            return start to end.takeIf { kind == PresenceKind.LISTENING }
        }
    }
}

fun PresenceButtonTarget.linkFor(track: NowPlaying): String {
    val query = Uri.encode("${track.title} ${track.artist}".trim())
    return when (this) {
        PresenceButtonTarget.YOUTUBE_MUSIC ->
            if (track.isYouTube) "https://music.youtube.com/watch?v=${track.mediaId}"
            else "https://music.youtube.com/search?q=$query"
        PresenceButtonTarget.SPOTIFY -> "https://open.spotify.com/search/$query"
        PresenceButtonTarget.APPLE_MUSIC -> "https://music.apple.com/search?term=$query"
        PresenceButtonTarget.TIDAL -> "https://listen.tidal.com/search?q=$query"
        PresenceButtonTarget.SEKAI_TUNE -> PresenceActivity.GithubRepoUrl
    }
}

