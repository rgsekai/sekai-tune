/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.lyrics.translation

import android.util.LruCache
import moe.rgsekai.sekaitune.ai.AiLyricsDocumentParser
import moe.rgsekai.sekaitune.lyrics.LyricsUtils

/**
 * In-memory LRU cache and translation coordinator for lyrics.
 * Stores up to 30 translations by songId (or lyrics hash) + targetLanguage.
 */
object LyricsTranslationCache {

    private val cache = LruCache<String, String>(30)

    fun getCacheKey(identifier: String, targetLanguage: String, mode: String = "standard"): String {
        val normalizedLang = GoogleTranslateEngine.normalizeLanguageCode(targetLanguage)
        return "${identifier}_${normalizedLang}_$mode"
    }

    fun get(identifier: String, targetLanguage: String, mode: String = "standard"): String? {
        return cache.get(getCacheKey(identifier, targetLanguage, mode))
    }

    fun put(identifier: String, targetLanguage: String, translatedLyrics: String, mode: String = "standard") {
        if (translatedLyrics.isNotBlank()) {
            cache.put(getCacheKey(identifier, targetLanguage, mode), translatedLyrics)
        }
    }

    fun clear() {
        cache.evictAll()
    }
}

