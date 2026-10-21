/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.rgsekai.sekaitune.playback.sources.addon.AddonClient
import moe.rgsekai.sekaitune.playback.sources.addon.AddonMusicSource
import moe.rgsekai.sekaitune.playback.sources.addon.AddonResponse
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted state of a source in encrypted storage.
 */
@Serializable
data class SourceRecord(
    val id: String,
    val kind: SourceKind,
    val label: String,
    val baseUrl: String? = null,
    val enabled: Boolean = true,
    val orderIndex: Int = 0,
    val lastHealthStatus: String? = null,
    val lastHealthDetail: String? = null,
    val lastHealthTimestamp: Long = 0L,
)

/**
 * UI / Runtime representation of a registered music source.
 */
data class RegisteredSource(
    val id: String,
    val kind: SourceKind,
    val displayName: String,
    val baseUrl: String? = null,
    val isEnabled: Boolean = true,
    val isDeletable: Boolean = true,
    val isReorderable: Boolean = true,
    val lastHealth: SourceHealth? = null,
    val lastHealthTimestamp: Long = 0L,
    val instance: MusicSource,
)

/**
 * Registry managing active and persisted audio sources.
 * Sources are stored encrypted in [EncryptedSharedPreferences].
 * YouTube is always pinned as the last source and cannot be disabled or removed.
 */
@Singleton
class SourceRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val PREFS_FILE = "sekai_sources_encrypted"
        const val FALLBACK_PREFS_FILE = "sekai_sources_fallback"
        const val KEY_SOURCES = "sources_list_v1"
        const val KEY_MATCH_TOLERANCE_SECONDS = "source_match_tolerance_seconds"

        const val DEFAULT_TOLERANCE_SECONDS = 3
        const val MIN_TOLERANCE_SECONDS = 1
        const val MAX_TOLERANCE_SECONDS = 10
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize EncryptedSharedPreferences, falling back to standard prefs")
            context.getSharedPreferences(FALLBACK_PREFS_FILE, Context.MODE_PRIVATE)
        }
    }

    private val instanceCache = ConcurrentHashMap<String, MusicSource>()
    private val youtubeSource = YouTubeMusicSource()

    private val _sourcesFlow = MutableStateFlow<List<RegisteredSource>>(emptyList())
    val sourcesFlow: StateFlow<List<RegisteredSource>> = _sourcesFlow.asStateFlow()

    private val _toleranceSecondsFlow = MutableStateFlow(DEFAULT_TOLERANCE_SECONDS)
    val toleranceSecondsFlow: StateFlow<Int> = _toleranceSecondsFlow.asStateFlow()

    init {
        _toleranceSecondsFlow.value = loadTolerance()
        _sourcesFlow.value = loadSources()
    }

    /**
     * Loads configured sources from preferences, reconstructs instances,
     * and guarantees YouTube is attached at the end.
     */
    private fun loadSources(): List<RegisteredSource> {
        val rawJson = prefs.getString(KEY_SOURCES, null)
        val savedRecords = if (!rawJson.isNullOrBlank()) {
            try {
                json.decodeFromString<List<SourceRecord>>(rawJson)
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse saved sources JSON")
                emptyList()
            }
        } else {
            emptyList()
        }

        // Filter out any stored youtube entry to avoid duplicates or order corruption
        val addonRecords = savedRecords
            .filter { it.kind != SourceKind.YOUTUBE }
            .sortedBy { it.orderIndex }

        val registered = addonRecords.map { record ->
            val source = instanceCache.getOrPut(record.id) {
                AddonMusicSource(
                    configId = record.id,
                    displayName = record.label,
                    client = AddonClient(record.baseUrl ?: ""),
                )
            }

            val health = when (record.lastHealthStatus) {
                "OK" -> SourceHealth.Ok(record.lastHealthDetail)
                "UNREACHABLE" -> SourceHealth.Unreachable(record.lastHealthDetail ?: "Unreachable")
                "REJECTED" -> SourceHealth.Rejected(record.lastHealthDetail ?: "Rejected")
                else -> null
            }

            RegisteredSource(
                id = record.id,
                kind = record.kind,
                displayName = record.label,
                baseUrl = record.baseUrl,
                isEnabled = record.enabled,
                isDeletable = true,
                isReorderable = true,
                lastHealth = health,
                lastHealthTimestamp = record.lastHealthTimestamp,
                instance = source,
            )
        }

        // YouTube baseline is ALWAYS present, enabled, and placed last
        val youtubeRegistered = RegisteredSource(
            id = YouTubeMusicSource.YOUTUBE_CONFIG_ID,
            kind = SourceKind.YOUTUBE,
            displayName = youtubeSource.displayName,
            baseUrl = null,
            isEnabled = true,
            isDeletable = false,
            isReorderable = false,
            lastHealth = SourceHealth.Ok("Baseline active"),
            lastHealthTimestamp = System.currentTimeMillis(),
            instance = youtubeSource,
        )

        return registered + youtubeRegistered
    }

    private fun loadTolerance(): Int {
        val saved = prefs.getInt(KEY_MATCH_TOLERANCE_SECONDS, DEFAULT_TOLERANCE_SECONDS)
        return saved.coerceIn(MIN_TOLERANCE_SECONDS, MAX_TOLERANCE_SECONDS)
    }

    private fun persistSources(sources: List<RegisteredSource>) {
        val records = sources
            .filter { it.kind != SourceKind.YOUTUBE }
            .mapIndexed { index, item ->
                val (status, detail) = when (val h = item.lastHealth) {
                    is SourceHealth.Ok -> "OK" to h.details
                    is SourceHealth.Unreachable -> "UNREACHABLE" to h.message
                    is SourceHealth.Rejected -> "REJECTED" to h.message
                    null -> null to null
                }
                SourceRecord(
                    id = item.id,
                    kind = item.kind,
                    label = item.displayName,
                    baseUrl = item.baseUrl,
                    enabled = item.isEnabled,
                    orderIndex = index,
                    lastHealthStatus = status,
                    lastHealthDetail = detail,
                    lastHealthTimestamp = item.lastHealthTimestamp,
                )
            }

        try {
            val encoded = json.encodeToString(records)
            prefs.edit().putString(KEY_SOURCES, encoded).apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist sources")
        }
    }

    /**
     * Registers a new addon source URL, testing its manifest beforehand.
     */
    suspend fun addAddon(url: String, customLabel: String? = null): Result<RegisteredSource> = withContext(Dispatchers.IO) {
        val normalizedUrl = AddonClient.normalizeBaseUrl(url)
        if (normalizedUrl.isBlank() || (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://"))) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid HTTP or HTTPS URL"))
        }

        // Probe the manifest
        val client = AddonClient(normalizedUrl)
        val manifestResponse = client.fetchManifest()
        val manifest = when (manifestResponse) {
            is AddonResponse.Success -> manifestResponse.value
            is AddonResponse.HttpError -> return@withContext Result.failure(
                IllegalStateException("Server returned HTTP ${manifestResponse.statusCode}: ${manifestResponse.message}")
            )
            is AddonResponse.RateLimited -> return@withContext Result.failure(
                IllegalStateException("Rate limited by server (retry after ${manifestResponse.retryAfterSeconds ?: 0}s)")
            )
            is AddonResponse.SerializationError -> return@withContext Result.failure(
                IllegalStateException("Server returned invalid manifest JSON: ${manifestResponse.cause.message ?: "parse error"}")
            )
            is AddonResponse.NetworkError -> return@withContext Result.failure(
                manifestResponse.cause
            )
        }

        val displayName = customLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: manifest.name.takeIf { it.isNotBlank() }
            ?: "Addon Source"

        val id = UUID.randomUUID().toString()
        val sourceInstance = AddonMusicSource(
            configId = id,
            displayName = displayName,
            client = client,
        )
        instanceCache[id] = sourceInstance

        val healthDetail = listOfNotNull(
            manifest.name.takeIf { it.isNotBlank() },
            manifest.version.takeIf { it.isNotBlank() }?.let { "v$it" }
        ).joinToString(" ")

        val newSource = RegisteredSource(
            id = id,
            kind = SourceKind.ADDON,
            displayName = displayName,
            baseUrl = normalizedUrl,
            isEnabled = true,
            isDeletable = true,
            isReorderable = true,
            lastHealth = SourceHealth.Ok(healthDetail.ifBlank { "Connected" }),
            lastHealthTimestamp = System.currentTimeMillis(),
            instance = sourceInstance,
        )

        val currentList = _sourcesFlow.value.filter { it.kind != SourceKind.YOUTUBE }
        val updated = currentList + newSource + _sourcesFlow.value.filter { it.kind == SourceKind.YOUTUBE }

        _sourcesFlow.value = updated
        persistSources(updated)

        Result.success(newSource)
    }

    /**
     * Executes a health check for the given source and stores the result.
     */
    suspend fun testSource(id: String): SourceHealth = withContext(Dispatchers.IO) {
        val source = _sourcesFlow.value.find { it.id == id }
            ?: return@withContext SourceHealth.Unreachable("Source not found")

        val health = source.instance.health()
        val now = System.currentTimeMillis()

        val updated = _sourcesFlow.value.map { item ->
            if (item.id == id) {
                item.copy(lastHealth = health, lastHealthTimestamp = now)
            } else {
                item
            }
        }

        _sourcesFlow.value = updated
        persistSources(updated)
        health
    }

    /**
     * Updates the enabled state of a source.
     * YouTube cannot be disabled.
     */
    fun setSourceEnabled(id: String, enabled: Boolean) {
        if (id == YouTubeMusicSource.YOUTUBE_CONFIG_ID) return // YouTube is always on

        val updated = _sourcesFlow.value.map { item ->
            if (item.id == id) {
                item.copy(isEnabled = enabled)
            } else {
                item
            }
        }
        _sourcesFlow.value = updated
        persistSources(updated)
    }

    /**
     * Reorders sources while ensuring YouTube remains strictly last.
     */
    fun reorderSources(fromIndex: Int, toIndex: Int) {
        val currentList = _sourcesFlow.value.toMutableList()
        val youtubeIndex = currentList.indexOfFirst { it.id == YouTubeMusicSource.YOUTUBE_CONFIG_ID }

        // Cannot move YouTube, and cannot move items past YouTube
        val maxReorderableIndex = if (youtubeIndex >= 0) youtubeIndex - 1 else currentList.size - 1
        if (fromIndex < 0 || fromIndex > maxReorderableIndex) return
        val clampedTo = toIndex.coerceIn(0, maxReorderableIndex)
        if (fromIndex == clampedTo) return

        val item = currentList.removeAt(fromIndex)
        currentList.add(clampedTo, item)

        _sourcesFlow.value = currentList
        persistSources(currentList)
    }

    /**
     * Removes an addon source.
     * YouTube cannot be removed.
     */
    fun removeSource(id: String): Boolean {
        if (id == YouTubeMusicSource.YOUTUBE_CONFIG_ID) return false

        val currentList = _sourcesFlow.value
        val updated = currentList.filterNot { it.id == id }
        if (updated.size == currentList.size) return false

        instanceCache.remove(id)
        _sourcesFlow.value = updated
        persistSources(updated)
        return true
    }

    /**
     * Updates track matching length tolerance in seconds (1 to 10).
     */
    fun setToleranceSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(MIN_TOLERANCE_SECONDS, MAX_TOLERANCE_SECONDS)
        _toleranceSecondsFlow.value = clamped
        prefs.edit().putInt(KEY_MATCH_TOLERANCE_SECONDS, clamped).apply()
    }

    /**
     * Returns ordered list of active sources for resolution.
     */
    fun getActiveSources(): List<MusicSource> {
        return _sourcesFlow.value.filter { it.isEnabled }.map { it.instance }
    }
}
