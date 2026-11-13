/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.lyrics.translation

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.constants.TranslateLyricsKey
import moe.rgsekai.sekaitune.constants.TranslatorTargetLangKey
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.db.entities.LyricsEntity
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.utils.dataStore
import timber.log.Timber

/**
 * Coordinates session-wide background translation as the queue advances.
 * Ensures only one translation runs at a time and cancels previous jobs on song skips.
 */
object SessionTranslationCoordinator {

    private var activeJob: Job? = null
    private var lastSongId: String? = null

    fun onSongChanged(
        context: Context,
        scope: CoroutineScope,
        database: MusicDatabase,
        mediaMetadata: MediaMetadata?,
    ) {
        if (mediaMetadata == null || mediaMetadata.id.isBlank()) return
        if (mediaMetadata.id == lastSongId) return
        lastSongId = mediaMetadata.id

        activeJob?.cancel()

        activeJob = scope.launch(Dispatchers.IO) {
            try {
                val preferences = context.dataStore.data.first()
                val isSessionTranslationEnabled = preferences[TranslateLyricsKey] ?: false
                if (!isSessionTranslationEnabled) return@launch

                val targetLanguage = preferences[TranslatorTargetLangKey]?.takeIf { it.isNotBlank() } ?: "en"

                val songId = mediaMetadata.id
                val existingLyrics = database.lyrics(songId).first()
                val rawLyrics = existingLyrics?.lyrics
                if (rawLyrics.isNullOrBlank() || rawLyrics == LyricsEntity.LYRICS_NOT_FOUND) return@launch

                // If already translated, no action needed
                if (existingLyrics.source == LyricsEntity.Source.AI_TRANSLATION.value) return@launch

                // Check cache first
                val cached = LyricsTranslationCache.get(songId, targetLanguage)
                if (cached != null) {
                    database.query {
                        upsert(
                            existingLyrics.copy(
                                lyrics = cached,
                                source = LyricsEntity.Source.AI_TRANSLATION.value,
                            )
                        )
                    }
                    return@launch
                }

                // Try official captions first
                val captionTranslation = YouTubeCaptionTranslation.translateWithCaptions(
                    videoId = songId,
                    rawLyrics = rawLyrics,
                    targetLanguageCode = targetLanguage,
                )

                val finalTranslation = captionTranslation ?: GoogleTranslateEngine.translate(
                    lines = moe.rgsekai.sekaitune.ai.AiLyricsDocumentParser.parse(rawLyrics).segments.map { it.text },
                    targetLanguageCode = targetLanguage,
                )?.let { translatedLines ->
                    val document = moe.rgsekai.sekaitune.ai.AiLyricsDocumentParser.parse(rawLyrics)
                    val map = mutableMapOf<Int, String>()
                    document.segments.forEachIndexed { idx, segment ->
                        if (idx < translatedLines.size) {
                            map[segment.id] = translatedLines[idx]
                        }
                    }
                    document.rebuild(map)
                }

                if (!finalTranslation.isNullOrBlank()) {
                    LyricsTranslationCache.put(songId, targetLanguage, finalTranslation)
                    database.query {
                        upsert(
                            existingLyrics.copy(
                                lyrics = finalTranslation,
                                source = LyricsEntity.Source.AI_TRANSLATION.value,
                            )
                        )
                    }
                    Timber.d("Session translation successfully applied for song: $songId in $targetLanguage")
                }
            } catch (e: CancellationException) {
                // Cooperative cancellation on song skip
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Session translation failed for song ${mediaMetadata.id}")
            }
        }
    }
}

