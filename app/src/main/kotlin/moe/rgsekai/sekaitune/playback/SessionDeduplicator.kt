/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.playback

import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.playback.sources.TrackMatcher
import java.util.Collections

class SessionDeduplicator(
    private val maxCapacity: Int = 500
) {
    private val sessionHistory = Collections.synchronizedList(mutableListOf<MediaMetadata>())

    val size: Int
        get() = sessionHistory.size

    fun record(metadata: MediaMetadata) {
        val id = metadata.id.trim()
        if (id.isBlank()) return
        synchronized(sessionHistory) {
            val exists = sessionHistory.any {
                it.id.trim() == id ||
                    (metadata.matchedCatalogId?.isNotBlank() == true && it.matchedCatalogId?.trim() == metadata.matchedCatalogId.trim())
            }
            if (!exists) {
                sessionHistory.add(metadata)
                if (sessionHistory.size > maxCapacity) {
                    sessionHistory.removeAt(0)
                }
            }
        }
    }

    fun clear() {
        sessionHistory.clear()
    }

    fun getAll(): List<MediaMetadata> = synchronized(sessionHistory) { sessionHistory.toList() }

    fun isDuplicate(
        candidateId: String,
        candidateTitle: String?,
        candidateArtists: List<String>,
        candidateMatchedCatalogId: String? = null,
        knownIds: Set<String> = emptySet(),
        checkSessionHistory: Boolean = true
    ): Boolean {
        val trimmedCandidateId = candidateId.trim()
        if (trimmedCandidateId.isNotEmpty() && knownIds.contains(trimmedCandidateId)) {
            return true
        }
        val trimmedMatchedId = candidateMatchedCatalogId?.trim().orEmpty()
        if (trimmedMatchedId.isNotEmpty() && knownIds.contains(trimmedMatchedId)) {
            return true
        }

        if (checkSessionHistory) {
            val history = getAll()
            if (history.any {
                    it.id.trim() == trimmedCandidateId ||
                        (trimmedMatchedId.isNotEmpty() && it.matchedCatalogId?.trim() == trimmedMatchedId)
                }
            ) {
                return true
            }

            val candNormTitle = TrackMatcher.normalizeTitle(candidateTitle.orEmpty())
            if (candNormTitle.isNotBlank()) {
                val candArtistWords = TrackMatcher.tokenizeArtistWords(candidateArtists)
                if (candArtistWords.isNotEmpty()) {
                    for (item in history) {
                        val itemNormTitle = TrackMatcher.normalizeTitle(item.title)
                        if (itemNormTitle.isNotBlank() && itemNormTitle == candNormTitle) {
                            val itemArtistWords = TrackMatcher.tokenizeArtistWords(item.artists.map { it.name })
                            if (itemArtistWords.isNotEmpty() && itemArtistWords.intersect(candArtistWords).isNotEmpty()) {
                                return true
                            }
                        }
                    }
                }
            }
        }

        return false
    }
}
