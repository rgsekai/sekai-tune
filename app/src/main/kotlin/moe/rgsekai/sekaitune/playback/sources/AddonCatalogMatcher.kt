/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.playback.CatalogMatchResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonCatalogMatcher @Inject constructor() {
    suspend fun findCatalogMatch(
        title: String,
        artists: List<String> = emptyList(),
        durationSec: Int? = null,
    ): CatalogMatchResult = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext CatalogMatchResult.NoMatch

        val target = MatchTarget(
            title = title,
            artists = artists,
            durationSec = durationSec,
        )

        // 1. Initial targeted search within ~3s budget
        val match = withTimeoutOrNull(MATCH_BUDGET_MS) {
            searchAndScoreCandidates(
                query = TrackMatcher.buildAddonSearchQuery(title, artists),
                target = target,
                toleranceSec = STRICT_TOLERANCE_SEC,
            )
        }

        if (match != null) {
            Timber.tag(TAG).i(
                "Catalog match found for '%s' by %s -> %s (confidence=high)",
                title,
                artists.joinToString(),
                match.catalogId,
            )
            return@withContext match
        }

        // 2. Retry once with looser query & duration tolerance
        Timber.tag(TAG).d("Targeted search failed for '%s', retrying with looser criteria", title)
        val looserMatch = withTimeoutOrNull(LOOSE_RETRY_BUDGET_MS) {
            val looseQuery = TrackMatcher.cleanTitleForSearch(title).ifBlank { title }
            searchAndScoreCandidates(
                query = looseQuery,
                target = target,
                toleranceSec = LOOSE_TOLERANCE_SEC,
            )
        }

        if (looserMatch != null) {
            Timber.tag(TAG).i(
                "Loose catalog match found for '%s' by %s -> %s",
                title,
                artists.joinToString(),
                looserMatch.catalogId,
            )
            return@withContext looserMatch
        }

        Timber.tag(TAG).w(
            "No catalog match found for addon track '%s' by %s (duration=%ss)",
            title,
            artists.joinToString(),
            durationSec ?: 0,
        )
        CatalogMatchResult.NoMatch
    }

    private suspend fun searchAndScoreCandidates(
        query: String,
        target: MatchTarget,
        toleranceSec: Int,
    ): CatalogMatchResult.Success? {
        if (query.isBlank()) return null
        val searchResponse = YouTube.search(
            query = query,
            filter = YouTube.SearchFilter.FILTER_SONG,
        ).getOrNull() ?: return null

        val candidates = searchResponse.items
            .filterIsInstance<SongItem>()
            .distinctBy { it.id }

        if (candidates.isEmpty()) return null

        var bestCandidate: SongItem? = null
        var bestScore = -1

        for (candidateItem in candidates) {
            val candidate = MatchCandidate(
                title = candidateItem.title,
                artists = candidateItem.artists.map { it.name },
                durationSec = candidateItem.duration,
            )

            val result = TrackMatcher.match(
                target = target,
                candidate = candidate,
                toleranceSec = toleranceSec,
            )

            if (result.isMatch && result.score > bestScore) {
                bestScore = result.score
                bestCandidate = candidateItem
            }
        }

        return bestCandidate?.let { item ->
            CatalogMatchResult.Success(
                catalogId = item.id,
                isHighConfidence = bestScore >= HIGH_CONFIDENCE_THRESHOLD,
                matchedTitle = item.title,
                matchedArtist = item.artists.firstOrNull()?.name,
            )
        }
    }

    companion object {
        private const val TAG = "AddonCatalogMatcher"
        private const val MATCH_BUDGET_MS = 3000L
        private const val LOOSE_RETRY_BUDGET_MS = 2500L
        private const val STRICT_TOLERANCE_SEC = 3
        private const val LOOSE_TOLERANCE_SEC = 10
        private const val HIGH_CONFIDENCE_THRESHOLD = 50
    }
}
