package moe.rgsekai.sekaitune.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.db.entities.ArtistEntity
import moe.rgsekai.sekaitune.db.entities.SongEntity
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.spotify.SpotifyMapper
import timber.log.Timber
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

sealed class CatalogMatchResult {
    data class Success(
        val catalogId: String,
        val isHighConfidence: Boolean,
        val matchedTitle: String? = null,
        val matchedArtist: String? = null,
    ) : CatalogMatchResult()

    data object NoMatch : CatalogMatchResult()
}

@Singleton
class ResolveUploadedCatalogMatchUseCase
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) {
        companion object {
            private const val HIGH_CONFIDENCE_THRESHOLD = 0.70
            private const val MEDIUM_CONFIDENCE_THRESHOLD = 0.40
            private const val NO_MATCH_COOLDOWN_HOURS = 6L
        }

        suspend operator fun invoke(
            song: SongEntity,
            artists: List<ArtistEntity> = emptyList(),
            forceRefresh: Boolean = false,
        ): CatalogMatchResult =
            withContext(Dispatchers.IO) {
                // If this is NOT an uploaded song, it's already a valid catalog/local track
                if (!song.isUploaded) {
                    return@withContext CatalogMatchResult.Success(
                        catalogId = song.id,
                        isHighConfidence = true,
                        matchedTitle = song.title,
                        matchedArtist = artists.firstOrNull()?.name,
                    )
                }

                // 1. Check persistent cache in SongEntity
                if (!forceRefresh && !song.matchedCatalogId.isNullOrBlank()) {
                    android.util.Log.e("MULTIDOWNLOAD_TRACKER", "Resolver CACHE HIT for songId=${song.id} ('${song.title}') -> matchedCatalogId=${song.matchedCatalogId}")
                    Timber.d("ResolveUploadedCatalogMatch: Cache hit for '${song.title}' -> ${song.matchedCatalogId}")
                    return@withContext CatalogMatchResult.Success(
                        catalogId = song.matchedCatalogId,
                        isHighConfidence = true,
                        matchedTitle = song.title,
                        matchedArtist = artists.firstOrNull()?.name,
                    )
                }

                // 2. Check no-match cooldown (Clarification #3)
                if (!forceRefresh && song.matchAttemptedAt != null && song.matchedCatalogId == null) {
                    val timeSinceLastAttempt = Duration.between(song.matchAttemptedAt, LocalDateTime.now()).abs()
                    if (timeSinceLastAttempt.toHours() < NO_MATCH_COOLDOWN_HOURS) {
                        Timber.d("ResolveUploadedCatalogMatch: Skipping search for '${song.title}' due to recent negative attempt (${timeSinceLastAttempt.toMinutes()}m ago)")
                        return@withContext CatalogMatchResult.NoMatch
                    }
                }

                // 3. Perform search on YouTube Music public catalog
                val artistName = artists.joinToString(" ") { it.name }.trim()
                val query = if (artistName.isBlank()) song.title else "$artistName ${song.title}"
                Timber.d("ResolveUploadedCatalogMatch: Searching YTM public catalog for '$query'")

                val searchResult =
                    YouTube.search(
                        query = query,
                        filter = YouTube.SearchFilter.FILTER_SONG,
                    ).getOrNull()

                val candidates =
                    searchResult?.items
                        ?.filterIsInstance<SongItem>()
                        ?.distinctBy { it.id }
                        .orEmpty()

                if (candidates.isEmpty()) {
                    Timber.w("ResolveUploadedCatalogMatch: No candidates found for query '$query'")
                    persistAttempt(song, matchedId = null)
                    return@withContext CatalogMatchResult.NoMatch
                }

                // 4. Score candidates using SpotifyMapper's Bigram Dice-coefficient logic
                val precomputed =
                    SpotifyMapper.precompute(
                        title = song.title,
                        artist = artistName,
                        durationMs = if (song.duration > 0) song.duration * 1000 else 0,
                    )

                val scoredCandidates =
                    candidates.map { candidate ->
                        val candidateArtist = candidate.artists.joinToString(" ") { it.name }
                        val score =
                            SpotifyMapper.matchScorePrecomputed(
                                precomputed = precomputed,
                                candidateTitle = candidate.title,
                                candidateArtist = candidateArtist,
                                candidateDurationSec = candidate.duration,
                            )
                        candidate to score
                    }

                val bestMatch = scoredCandidates.maxByOrNull { it.second }
                if (bestMatch == null) {
                    persistAttempt(song, matchedId = null)
                    return@withContext CatalogMatchResult.NoMatch
                }

                val (bestItem, bestScore) = bestMatch
                android.util.Log.e("MULTIDOWNLOAD_TRACKER", "Resolver SEARCH for '${song.title}': Best candidate '${bestItem.title}' by '${bestItem.artists.joinToString { it.name }}' (id=${bestItem.id}) with score=$bestScore")
                Timber.d("ResolveUploadedCatalogMatch: Best candidate for '${song.title}' is '${bestItem.title}' by '${bestItem.artists.joinToString { it.name }}' with score $bestScore")

                when {
                    bestScore >= HIGH_CONFIDENCE_THRESHOLD -> {
                        persistAttempt(song, matchedId = bestItem.id)
                        android.util.Log.e("MULTIDOWNLOAD_TRACKER", "Resolver HIGH CONFIDENCE match for songId=${song.id} ('${song.title}') -> matchedCatalogId=${bestItem.id}")
                        CatalogMatchResult.Success(
                            catalogId = bestItem.id,
                            isHighConfidence = true,
                            matchedTitle = bestItem.title,
                            matchedArtist = bestItem.artists.firstOrNull()?.name,
                        )
                    }

                    bestScore >= MEDIUM_CONFIDENCE_THRESHOLD -> {
                        persistAttempt(song, matchedId = bestItem.id)
                        android.util.Log.e("MULTIDOWNLOAD_TRACKER", "Resolver MEDIUM CONFIDENCE match for songId=${song.id} ('${song.title}') -> matchedCatalogId=${bestItem.id}")
                        CatalogMatchResult.Success(
                            catalogId = bestItem.id,
                            isHighConfidence = false,
                            matchedTitle = bestItem.title,
                            matchedArtist = bestItem.artists.firstOrNull()?.name,
                        )
                    }

                    else -> {
                        android.util.Log.e("MULTIDOWNLOAD_TRACKER", "Resolver LOW CONFIDENCE ($bestScore < $MEDIUM_CONFIDENCE_THRESHOLD) for songId=${song.id} ('${song.title}') -> NO MATCH")
                        Timber.w("ResolveUploadedCatalogMatch: Score $bestScore below threshold $MEDIUM_CONFIDENCE_THRESHOLD for '${song.title}'")
                        persistAttempt(song, matchedId = null)
                        CatalogMatchResult.NoMatch
                    }
                }
            }

        suspend fun invalidateMatch(songId: String) =
            withContext(Dispatchers.IO) {
                database.song(songId).firstOrNull()?.let { dbSong ->
                    database.update(
                        dbSong.song.copy(
                            matchedCatalogId = null,
                            matchAttemptedAt = null,
                        ),
                    )
                }
            }

        private fun persistAttempt(song: SongEntity, matchedId: String?) {
            try {
                database.update(
                    song.copy(
                        matchedCatalogId = matchedId,
                        matchAttemptedAt = LocalDateTime.now(),
                    ),
                )
            } catch (e: Exception) {
                Timber.w(e, "ResolveUploadedCatalogMatch: Failed to update matchedCatalogId in DB for ${song.id}")
            }
        }
    }
