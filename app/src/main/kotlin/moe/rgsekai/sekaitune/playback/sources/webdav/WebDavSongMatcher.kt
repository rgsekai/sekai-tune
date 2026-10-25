/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources.webdav

import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.db.entities.WebDavSongEntity
import moe.rgsekai.sekaitune.playback.sources.MatchCandidate
import moe.rgsekai.sekaitune.playback.sources.MatchResult
import moe.rgsekai.sekaitune.playback.sources.MatchTarget
import moe.rgsekai.sekaitune.playback.sources.TrackMatcher
import timber.log.Timber
import kotlin.math.abs

object WebDavSongMatcher {
    private const val TAG = "WebDavSongMatcher"

    /**
     * Evaluates a candidate WebDavSongEntity against a MatchTarget with duration tolerance.
     * Rejects candidates where tagsRead is false or durationMs is null.
     */
    fun evaluateCandidate(
        target: MatchTarget,
        candidateEntity: WebDavSongEntity,
        toleranceSec: Int = 3,
    ): MatchResult {
        if (!candidateEntity.tagsRead) {
            return MatchResult(
                isMatch = false,
                score = 0,
                rejectReason = "Tags have not been read yet",
            )
        }
        val durMs = candidateEntity.durationMs
            ?: return MatchResult(
                isMatch = false,
                score = 0,
                rejectReason = "Missing duration",
            )
        val durSec = (durMs / 1000).toInt()

        val candidate = MatchCandidate(
            title = candidateEntity.title,
            artists = listOf(candidateEntity.artist),
            durationSec = durSec,
        )

        return TrackMatcher.match(target, candidate, toleranceSec)
    }

    /**
     * Finds the best matching WebDAV song from the database for the given MatchTarget.
     */
    suspend fun findMatch(
        target: MatchTarget,
        database: MusicDatabase,
        toleranceSec: Int = 3,
    ): WebDavSongEntity? {
        val targetDur = target.durationSec
        val candidates = if (targetDur != null && targetDur > 0) {
            val minMs = ((targetDur - toleranceSec).coerceAtLeast(0)) * 1000L
            val maxMs = (targetDur + toleranceSec) * 1000L
            database.webDavSongDao.getCandidatesByDuration(minMs, maxMs)
        } else {
            database.webDavSongDao.getCandidatesForMatching()
        }

        if (candidates.isEmpty()) {
            Timber.tag(TAG).d("No WebDAV candidates within duration tolerance for '%s'", target.title)
            return null
        }

        var bestMatch: WebDavSongEntity? = null
        var bestScore = -1
        var smallestDurationDiff = Int.MAX_VALUE

        for (candidateEntity in candidates) {
            val result = evaluateCandidate(target, candidateEntity, toleranceSec)
            if (result.isMatch) {
                val durSec = (candidateEntity.durationMs!! / 1000).toInt()
                val durDiff = targetDur?.let { abs(it - durSec) } ?: 0
                if (result.score > bestScore || (result.score == bestScore && durDiff < smallestDurationDiff)) {
                    bestMatch = candidateEntity
                    bestScore = result.score
                    smallestDurationDiff = durDiff
                }
            } else {
                Timber.tag(TAG).v("Rejected '%s' by '%s': %s", candidateEntity.title, candidateEntity.artist, result.rejectReason)
            }
        }

        if (bestMatch != null) {
            Timber.tag(TAG).i(
                "Matched target '%s' with WebDAV '%s' (score=%d, diff=%ds, path=%s)",
                target.title,
                bestMatch.title,
                bestScore,
                smallestDurationDiff,
                bestMatch.path,
            )
        }
        return bestMatch
    }
}
