/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas.models

import java.util.Locale
import kotlin.math.abs

object CanvasArtworkIdentity {
    fun matches(
        title1: String,
        artists1: List<String>,
        durationMs1: Long?,
        title2: String,
        artists2: List<String>,
        durationMs2: Long?,
    ): Boolean {
        val normTitle1 = normalizeTitle(title1)
        val normTitle2 = normalizeTitle(title2)
        if (normTitle1.isEmpty() || normTitle2.isEmpty()) return false

        val titleMatches = normTitle1 == normTitle2 ||
            normTitle1.contains(normTitle2) ||
            normTitle2.contains(normTitle1)

        if (!titleMatches) return false

        val normArtists1 = artists1.map { normalize(it) }.filter { it.isNotEmpty() }
        val normArtists2 = artists2.map { normalize(it) }.filter { it.isNotEmpty() }

        val artistMatches = normArtists1.isEmpty() || normArtists2.isEmpty() ||
            normArtists1.any { a1 -> normArtists2.any { a2 -> a1 == a2 || a1.contains(a2) || a2.contains(a1) } }

        if (!artistMatches) return false

        if (durationMs1 != null && durationMs2 != null && durationMs1 > 0L && durationMs2 > 0L) {
            if (abs(durationMs1 - durationMs2) > 4000L) {
                return false
            }
        }

        return true
    }

    fun normalizeTitle(title: String): String {
        return title
            .lowercase(Locale.ROOT)
            .replace(Regex("""\(feat\..*?\)|\(ft\..*?\)|\(with.*?\)"""), "")
            .replace(Regex("""\[.*?\]"""), "")
            .replace(Regex("""\s*-\s*(single|ep|remix|edit|version)"""), "")
            .replace(Regex("""[^a-z0-9\p{L}]+"""), " ")
            .trim()
    }

    fun normalize(text: String): String {
        return text
            .lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9\p{L}]+"""), " ")
            .trim()
    }
}
