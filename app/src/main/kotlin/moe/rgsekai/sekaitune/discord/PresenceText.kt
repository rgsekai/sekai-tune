/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.discord

object PresenceText {
    const val AppName = "Sekai Tune"

    val Placeholders = listOf(
        "{name}" to "Song name",
        "{artist}" to "Artist name",
        "{album}" to "Album",
        "{app}" to AppName,
        "{quality}" to "Format and bitrate, like OPUS · 160 kbps",
        "{apple}" to "The Apple logo (only Apple devices can show it)",
    )

    fun fill(template: String, track: NowPlaying): String = template
        .replace("{name}", track.title)
        .replace("{artist}", track.artist)
        .replace("{album}", track.album.orEmpty())
        .replace("{app}", AppName)
        .replace("{quality}", track.quality.orEmpty())
        .replace("{apple}", "")

    fun line(template: String, track: NowPlaying, maxLength: Int = 128): String? {
        val text = fill(template, track).trim().replace(Whitespace, " ")
        return when {
            text.isEmpty() -> null
            text.length == 1 -> text + Blank
            text.length > maxLength -> text.take(maxLength - 1).trimEnd() + "…"
            else -> text
        }
    }

    private val Whitespace = Regex("\\s+")
    private const val Blank = "⠀"
}

