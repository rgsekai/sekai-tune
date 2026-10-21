/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources

import moe.rgsekai.sekaitune.lyrics.LyricsUtils
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Target track metadata requested for playback or search.
 */
data class MatchTarget(
    val title: String,
    val artists: List<String> = emptyList(),
    val durationSec: Int? = null,
)

/**
 * Candidate track retrieved from an external source.
 */
data class MatchCandidate(
    val title: String,
    val artists: List<String> = emptyList(),
    val durationSec: Int? = null,
)

/**
 * Evaluation result indicating whether the candidate matches the target track.
 */
data class MatchResult(
    val isMatch: Boolean,
    val score: Int,
    val rejectReason: String? = null,
    val details: String? = null,
)

/**
 * Rule-based track matcher evaluating candidates against target tracks.
 * Handles title/artist normalization, version marker consistency, whole-word artist overlap,
 * Japanese Kana-Romaji transliteration bridging, and configurable duration tolerance.
 */
object TrackMatcher {

    private val PACKAGING_WORDS = listOf(
        "official audio", "official video", "official music video", "official mv",
        "music video", "lyric video", "lyrics video", "lyrics",
        "remastered", "remaster", "album version", "original mix",
        "full version", "full ver", "audio", "video", "mv", "pv",
        "visualizer", "hd", "hq", "4k", "1080p", "explicit", "clean",
        "version", "ver", "edition", "track",
    )

    private val VERSION_MARKERS = listOf(
        "remix", "live", "instrumental", "acoustic", "sped up", "slowed",
        "reverb", "edit", "karaoke", "cover", "extended", "radio edit",
        "orchestral", "piano version", "unplugged", "demo",
    )

    private val ARTIST_SEPARATORS = Regex("""[,;&/\\|]|(?i)\b(feat\.?|ft\.?|with|vs\.?|and|&)\b""")

    /**
     * Matches a candidate track against the target track with the specified duration tolerance.
     */
    fun match(
        target: MatchTarget,
        candidate: MatchCandidate,
        toleranceSec: Int = 3,
    ): MatchResult {
        // 1. Check version markers
        val targetVersions = detectVersionMarkers(target.title)
        val candidateVersions = detectVersionMarkers(candidate.title)
        if (targetVersions != candidateVersions) {
            val targetStr = if (targetVersions.isEmpty()) "none" else targetVersions.joinToString(",")
            val candidateStr = if (candidateVersions.isEmpty()) "none" else candidateVersions.joinToString(",")
            return MatchResult(
                isMatch = false,
                score = 0,
                rejectReason = "Version marker mismatch (target: $targetStr, candidate: $candidateStr)",
            )
        }

        // 2. Check artist match (whole-word overlap, not substring)
        val targetArtistWords = tokenizeArtistWords(target.artists)
        val candidateArtistWords = tokenizeArtistWords(candidate.artists)

        if (targetArtistWords.isNotEmpty() && candidateArtistWords.isNotEmpty()) {
            val commonArtists = targetArtistWords.intersect(candidateArtistWords)
            if (commonArtists.isEmpty()) {
                return MatchResult(
                    isMatch = false,
                    score = 0,
                    rejectReason = "Artist mismatch: no whole-word artist overlap (${target.artists} vs ${candidate.artists})",
                )
            }
        }

        // 3. Check duration tolerance
        var durationBonus = 0
        if (target.durationSec != null && target.durationSec > 0 &&
            candidate.durationSec != null && candidate.durationSec > 0
        ) {
            val delta = abs(target.durationSec - candidate.durationSec)
            if (delta > toleranceSec) {
                return MatchResult(
                    isMatch = false,
                    score = 0,
                    rejectReason = "Duration delta ${delta}s exceeds tolerance of ${toleranceSec}s (target: ${target.durationSec}s, candidate: ${candidate.durationSec}s)",
                )
            }
            durationBonus = (toleranceSec - delta + 1) * 5
        }

        // 4. Check title similarity
        val normTargetTitle = normalizeTitle(target.title)
        val normCandidateTitle = normalizeTitle(candidate.title)

        var titleScore = 0
        when {
            normTargetTitle == normCandidateTitle -> {
                titleScore = 50
            }
            isJapaneseKanaOrRomajiMatch(target.title, candidate.title) -> {
                titleScore = 45
            }
            else -> {
                val similarity = computeTitleWordSimilarity(normTargetTitle, normCandidateTitle)
                if (similarity < 0.6f) {
                    return MatchResult(
                        isMatch = false,
                        score = 0,
                        rejectReason = "Title mismatch: similarity ${(similarity * 100).toInt()}% below 60% threshold",
                    )
                }
                titleScore = (similarity * 40).toInt()
            }
        }

        // 5. Total score calculation
        val artistBonus = if (targetArtistWords.isNotEmpty() && candidateArtistWords.isNotEmpty()) 30 else 15
        val versionBonus = if (targetVersions.isNotEmpty()) 10 else 0
        val totalScore = titleScore + artistBonus + versionBonus + durationBonus

        return MatchResult(
            isMatch = totalScore >= 50,
            score = totalScore,
            details = "Title: $titleScore, Artist: $artistBonus, DurationBonus: $durationBonus",
        )
    }

    /**
     * Detects version markers in track title.
     */
    fun detectVersionMarkers(title: String): Set<String> {
        val normalized = normalizeString(title)
        val found = mutableSetOf<String>()
        for (marker in VERSION_MARKERS) {
            val regex = Regex("""\b${Regex.escape(marker)}\b""", RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(normalized)) {
                found.add(marker)
            }
        }
        return found
    }

    /**
     * Normalizes title by lowercasing, stripping punctuation, version markers, and packaging words.
     */
    fun normalizeTitle(title: String): String {
        var text = normalizeString(title)

        // Remove packaging words
        for (pkg in PACKAGING_WORDS) {
            text = text.replace(Regex("""\b${Regex.escape(pkg)}\b""", RegexOption.IGNORE_CASE), " ")
        }

        // Remove version markers for base title comparison
        for (v in VERSION_MARKERS) {
            text = text.replace(Regex("""\b${Regex.escape(v)}\b""", RegexOption.IGNORE_CASE), " ")
        }

        // Strip non-alphanumeric punctuation (while keeping Japanese and unicode characters)
        text = text.replace(Regex("""[\[\]\(\)\{\}\-_.,:;!?'"~`+*/\\#@$%^&=|<>]"""), " ")
        return text.replace(Regex("""\s+"""), " ").trim()
    }

    /**
     * Tokenizes artist names into whole words, splitting on standard collaboration separators.
     */
    fun tokenizeArtistWords(artists: List<String>): Set<String> {
        val words = mutableSetOf<String>()
        for (artist in artists) {
            val parts = artist.split(ARTIST_SEPARATORS)
            for (part in parts) {
                val cleaned = normalizeString(part)
                    .replace(Regex("""[\[\]\(\)\{\}\-_.,:;!?'"~`+*/\\#@$%^&=|<>]"""), " ")
                for (token in cleaned.split(Regex("""\s+"""))) {
                    val trimmed = token.trim()
                    if (trimmed.length >= 2) {
                        words.add(trimmed)
                    }
                }
            }
        }
        return words
    }

    /**
     * Checks if Japanese titles match across Kana (Hiragana/Katakana) and Romaji.
     */
    fun isJapaneseKanaOrRomajiMatch(title1: String, title2: String): Boolean {
        val romaji1 = toRomajiKey(title1)
        val romaji2 = toRomajiKey(title2)
        return romaji1.isNotBlank() && romaji2.isNotBlank() && romaji1 == romaji2
    }

    /**
     * Converts a string containing Japanese characters to a normalized alphanumeric Romaji key.
     */
    private fun toRomajiKey(text: String): String {
        val norm = normalizeTitle(text)
        val katakana = hiraganaToKatakana(norm)
        val romaji = LyricsUtils.katakanaToRomaji(katakana)
        return romaji.lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9]"""), "")
    }

    /**
     * Converts Hiragana characters to Katakana.
     */
    private fun hiraganaToKatakana(text: String): String {
        return buildString(text.length) {
            for (ch in text) {
                if (ch in '\u3041'..'\u3096') {
                    append((ch.code + 0x60).toChar())
                } else {
                    append(ch)
                }
            }
        }
    }

    private fun normalizeString(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        return nfkc.lowercase(Locale.ROOT)
    }

    /**
     * Computes word-based Jaccard similarity between two normalized titles.
     */
    private fun computeTitleWordSimilarity(title1: String, title2: String): Float {
        val words1 = title1.split(Regex("""\s+""")).filter { it.isNotBlank() }.toSet()
        val words2 = title2.split(Regex("""\s+""")).filter { it.isNotBlank() }.toSet()
        if (words1.isEmpty() && words2.isEmpty()) return 1.0f
        if (words1.isEmpty() || words2.isEmpty()) return 0.0f

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        val jaccard = intersection.toFloat() / union.toFloat()
        val containment = intersection.toFloat() / minOf(words1.size, words2.size).toFloat()
        return maxOf(jaccard, containment * 0.85f)
    }
}
