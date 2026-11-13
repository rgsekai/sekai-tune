/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.lyrics.translation

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

/**
 * Robust, zero-config translation engine using Google's web translate endpoint (client=gtx).
 * Features:
 * - 1,500 character batch budgeting
 * - Preservation delimiter that survives machine translation without line-merging
 * - Paced requests with concurrency limiting, inter-batch delays, and exponential backoff on 429/5xx
 * - Fail-safe line-by-line fallback with Semaphore(2) if delimiter is mangled
 * - Per-line failure tolerance (retains original text instead of discarding the entire song)
 */
object GoogleTranslateEngine {

    private const val LineMarker = "\n@@@\n"
    private const val BatchCharBudget = 1_500
    private const val MaxConcurrentBatches = 2
    private const val InterBatchDelayMs = 250L
    private const val MaxRetries = 3

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(20, TimeUnit.SECONDS)
                    writeTimeout(15, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }
        }
    }

    class TranslationBusyException(message: String = "Translation service is busy, try again in a minute") : Exception(message)

    /**
     * Translates [lines] into [targetLanguageCode], preserving exact 1-to-1 order and count.
     */
    suspend fun translate(
        lines: List<String>,
        targetLanguageCode: String,
        sourceLanguageCode: String = "auto",
    ): List<String>? = withContext(Dispatchers.IO) {
        if (lines.isEmpty() || targetLanguageCode.isBlank()) return@withContext null

        val targetLang = normalizeLanguageCode(targetLanguageCode)
        val sourceLang = normalizeLanguageCode(sourceLanguageCode).ifBlank { "auto" }

        val out = ArrayList<String>(lines.size)
        var batch = ArrayList<String>()
        var budget = 0

        suspend fun flush(): Boolean {
            if (batch.isEmpty()) return true
            val translated = translateBatchWithRetry(batch, targetLang, sourceLang)
                ?: translateLineByLinePaced(batch, targetLang, sourceLang)
                ?: return false

            out.addAll(translated)
            batch = ArrayList()
            budget = 0
            delay(InterBatchDelayMs)
            return true
        }

        for (line in lines) {
            if (budget + line.length > BatchCharBudget && batch.isNotEmpty()) {
                if (!flush()) return@withContext null
            }
            batch.add(line)
            budget += line.length + LineMarker.length
        }
        if (!flush()) return@withContext null

        if (out.size == lines.size) out else null
    }

    private suspend fun translateBatchWithRetry(
        lines: List<String>,
        targetLanguage: String,
        sourceLanguage: String,
    ): List<String>? {
        var attempt = 0
        var backoffMs = 800L

        while (attempt < MaxRetries) {
            attempt++
            try {
                val payload = lines.joinToString(LineMarker)
                val response: HttpResponse = client.get("https://translate.googleapis.com/translate_a/single") {
                    parameter("client", "gtx")
                    parameter("sl", sourceLanguage)
                    parameter("tl", targetLanguage)
                    parameter("dt", "t")
                    parameter("q", payload)
                    header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    header("Accept", "*/*")
                }

                if (response.status.value == 429) {
                    val retryAfterSeconds = response.headers["Retry-After"]?.toLongOrNull() ?: (backoffMs / 1000L)
                    Timber.w("Google Translate 429 received. Backoff for ${retryAfterSeconds}s (attempt $attempt)")
                    if (attempt >= MaxRetries) {
                        throw TranslationBusyException()
                    }
                    delay((retryAfterSeconds * 1000L).coerceAtLeast(backoffMs))
                    backoffMs = (backoffMs * 2).coerceAtMost(8000L)
                    continue
                }

                if (response.status.value !in 200..299) {
                    Timber.w("Google Translate HTTP ${response.status.value}")
                    if (attempt < MaxRetries) {
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(4000L)
                        continue
                    }
                    return null
                }

                val body = response.bodyAsText()
                val parsed = parseTranslationResponse(body, lines.size)
                if (parsed != null && parsed.size == lines.size) {
                    return parsed
                } else {
                    Timber.w("Delimiter split mismatch: got ${parsed?.size ?: 0}, expected ${lines.size}")
                    return null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: TranslationBusyException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Translation request error on attempt $attempt")
                if (attempt < MaxRetries) {
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(4000L)
                }
            }
        }
        return null
    }

    private suspend fun translateLineByLinePaced(
        lines: List<String>,
        targetLanguage: String,
        sourceLanguage: String,
    ): List<String>? = coroutineScope {
        val gate = Semaphore(MaxConcurrentBatches)
        lines.mapIndexed { index, line ->
            async {
                gate.withPermit {
                    delay(150L)
                    val result = translateBatchWithRetry(listOf(line), targetLanguage, sourceLanguage)?.firstOrNull()
                    result ?: line
                }
            }
        }.awaitAll().takeIf { it.size == lines.size }
    }

    private fun parseTranslationResponse(body: String, expectedCount: Int): List<String>? = runCatching {
        val root = json.parseToJsonElement(body).jsonArray
        val segments = root.getOrNull(0) as? JsonArray ?: return null
        val joined = buildString {
            for (segment in segments) {
                val piece = (segment as? JsonArray)?.getOrNull(0) as? JsonPrimitive ?: continue
                append(piece.content)
            }
        }
        val parts = joined.split(Regex("\\s*@@@\\s*")).map { it.trim() }
        if (parts.size == expectedCount) parts else null
    }.getOrNull()

    /**
     * Converts language names, enum names, or country-style codes to ISO 639-1 format
     * (e.g. "JAPANESE" -> "ja", "CHINESE_SIMPLIFIED" -> "zh-CN", "en-US" -> "en").
     */
    fun normalizeLanguageCode(input: String): String {
        val trimmed = input.trim()
        if (trimmed.equals("auto", ignoreCase = true)) return "auto"

        // Map common uppercase names/codes from translator_languages.json
        LanguageNameToCode[trimmed.uppercase(Locale.ROOT)]?.let { return it }

        // Standard 2-letter or BCP-47 codes (e.g. "ja", "es", "zh-CN")
        return when (trimmed.lowercase(Locale.ROOT)) {
            "zh-cn", "chinese_simplified" -> "zh-CN"
            "zh-tw", "chinese_traditional" -> "zh-TW"
            else -> trimmed.substringBefore('-').lowercase(Locale.ROOT)
        }
    }

    private val LanguageNameToCode = mapOf(
        "AFRIKAANS" to "af",
        "ALBANIAN" to "sq",
        "AMHARIC" to "am",
        "ARABIC" to "ar",
        "ARMENIAN" to "hy",
        "ASSAMESE" to "as",
        "AZERBAIJANI" to "az",
        "BASQUE" to "eu",
        "BELARUSIAN" to "be",
        "BENGALI" to "bn",
        "BOSNIAN" to "bs",
        "BULGARIAN" to "bg",
        "BURMESE" to "my",
        "CATALAN" to "ca",
        "CEBUANO" to "ceb",
        "CHINESE_SIMPLIFIED" to "zh-CN",
        "CHINESE_TRADITIONAL" to "zh-TW",
        "CORSICAN" to "co",
        "CROATIAN" to "hr",
        "CZECH" to "cs",
        "DANISH" to "da",
        "DUTCH" to "nl",
        "ENGLISH" to "en",
        "ESPERANTO" to "eo",
        "ESTONIAN" to "et",
        "FILIPINO" to "tl",
        "FINNISH" to "fi",
        "FRENCH" to "fr",
        "FRISIAN" to "fy",
        "GALICIAN" to "gl",
        "GEORGIAN" to "ka",
        "GERMAN" to "de",
        "GREEK" to "el",
        "GUJARATI" to "gu",
        "HAITIAN_CREOLE" to "ht",
        "HAUSA" to "ha",
        "HAWAIIAN" to "haw",
        "HEBREW" to "iw",
        "HINDI" to "hi",
        "HMONG" to "hmn",
        "HUNGARIAN" to "hu",
        "ICELANDIC" to "is",
        "IGBO" to "ig",
        "INDONESIAN" to "id",
        "IRISH" to "ga",
        "ITALIAN" to "it",
        "JAPANESE" to "ja",
        "JAVANESE" to "jw",
        "KANNADA" to "kn",
        "KAZAKH" to "kk",
        "KHMER" to "km",
        "KOREAN" to "ko",
        "KURDISH" to "ku",
        "KYRGYZ" to "ky",
        "LAO" to "lo",
        "LATIN" to "la",
        "LATVIAN" to "lv",
        "LITHUANIAN" to "lt",
        "LUXEMBOURGISH" to "lb",
        "MACEDONIAN" to "mk",
        "MALAGASY" to "mg",
        "MALAY" to "ms",
        "MALAYALAM" to "ml",
        "MALTESE" to "mt",
        "MAORI" to "mi",
        "MARATHI" to "mr",
        "MONGOLIAN" to "mn",
        "MYANMAR" to "my",
        "NEPALI" to "ne",
        "NORWEGIAN" to "no",
        "PASHTO" to "ps",
        "PERSIAN" to "fa",
        "POLISH" to "pl",
        "PORTUGUESE" to "pt",
        "PUNJABI" to "pa",
        "ROMANIAN" to "ro",
        "RUSSIAN" to "ru",
        "SAMOAN" to "sm",
        "SCOTS_GAELIC" to "gd",
        "SERBIAN" to "sr",
        "SESOTHO" to "st",
        "SHONA" to "sn",
        "SINDHI" to "sd",
        "SINHALA" to "si",
        "SLOVAK" to "sk",
        "SLOVENIAN" to "sl",
        "SOMALI" to "so",
        "SPANISH" to "es",
        "SUNDANESE" to "su",
        "SWAHILI" to "sw",
        "SWEDISH" to "sv",
        "TAJIK" to "tg",
        "TAMIL" to "ta",
        "TELUGU" to "te",
        "THAI" to "th",
        "TURKISH" to "tr",
        "UKRAINIAN" to "uk",
        "URDU" to "ur",
        "UZBEK" to "uz",
        "VIETNAMESE" to "vi",
        "WELSH" to "cy",
        "XHOSA" to "xh",
        "YIDDISH" to "yi",
        "YORUBA" to "yo",
        "ZULU" to "zu"
    )
}

