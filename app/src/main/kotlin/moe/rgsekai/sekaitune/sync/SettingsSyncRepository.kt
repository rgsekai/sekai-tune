/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.BuildConfig
import moe.rgsekai.sekaitune.constants.AiApiKeyKey
import moe.rgsekai.sekaitune.constants.DataSyncIdKey
import moe.rgsekai.sekaitune.constants.InnerTubeCookieKey
import moe.rgsekai.sekaitune.constants.LastCloudSyncTimestampKey
import moe.rgsekai.sekaitune.constants.SavedAccountsKey
import moe.rgsekai.sekaitune.constants.VisitorDataKey
import moe.rgsekai.sekaitune.utils.dataStore
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Success(val lastSyncedAt: Long) : SyncStatus
    data class Error(val message: String) : SyncStatus
}

@Singleton
class SettingsSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptionService: SettingsEncryptionService,
) {
    private val firestore = FirebaseFirestore.getInstance()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    companion object {
        private const val COLLECTION_USER_SETTINGS = "user_settings"

        val SECRET_KEYS = setOf(
            AiApiKeyKey.name,
            InnerTubeCookieKey.name,
            VisitorDataKey.name,
            DataSyncIdKey.name,
            SavedAccountsKey.name,
            "spotify_access_token",
            "spotify_refresh_token",
            "proxy_username",
            "proxy_password",
        )

        val EXCLUDED_KEYS = setOf(
            "github_contributors_etag",
            "github_contributors_json",
            "github_contributors_last_checked_at",
            "github_translation_contributors_json",
            "github_translation_contributors_last_checked_at",
            "github_releases_etag",
            "github_releases_json",
            "github_releases_last_checked_at",
            "github_releases_fingerprint",
            "lastUpdateCheck",
            "moriCipherManualRefreshHistory",
            "launch_count",
            "has_pressed_star",
            "remind_after",
            "last_cloud_sync_timestamp",
        )

        val INT_KEYS = setOf(
            "aodThumbnailShapeRotation",
            "aodTitleMaxLines",
            "backdropBlurAmount",
            "miniPlayerLastAnchor",
            "proxyPort",
            "local_songs_min_duration_seconds",
            "together_default_port",
            "deviceMutePlaybackRecoveryVolume",
            "equalizerOutputGainMb",
            "equalizerBassBoostStrength",
            "equalizerVirtualizerStrength",
            "maxImageCacheSize",
            "maxSongCacheSize",
            "maxCanvasCacheSize",
            "historyDuration",
            "queue_lyrics_preload_count",
            "repeatMode",
            "launch_count",
            "remind_after",
            "widget_dominant_color",
        )
    }

    suspend fun pushSettings(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("User ID cannot be blank"))
        }
        _syncStatus.value = SyncStatus.Syncing
        try {
            val currentPreferences = context.dataStore.data.first().asMap()
            val plainSettings = LinkedHashMap<String, Any?>()
            val encryptedSecrets = LinkedHashMap<String, Map<String, String>>()

            for ((key, value) in currentPreferences) {
                val keyName = key.name
                if (keyName in EXCLUDED_KEYS) continue

                if (keyName in SECRET_KEYS || keyName.endsWith("_key") || keyName.endsWith("_token") || keyName.endsWith("_secret") || keyName.endsWith("_password")) {
                    val strValue = value?.toString().orEmpty()
                    if (strValue.isNotEmpty()) {
                        val encrypted = encryptionService.encrypt(strValue)
                        encryptedSecrets[keyName] = mapOf(
                            "ciphertext" to encrypted.ciphertext,
                            "iv" to encrypted.iv
                        )
                    }
                } else {
                    when (value) {
                        is Set<*> -> {
                            plainSettings[keyName] = value.mapNotNull { it?.toString() }
                        }
                        is Float -> {
                            plainSettings[keyName] = value.toDouble()
                        }
                        else -> {
                            plainSettings[keyName] = value
                        }
                    }
                }
            }

            val now = System.currentTimeMillis()
            val documentData = mapOf(
                "userId" to userId,
                "lastSyncedAt" to now,
                "appVersion" to BuildConfig.VERSION_NAME,
                "settings" to plainSettings,
                "encryptedSecrets" to encryptedSecrets,
            )

            firestore.collection(COLLECTION_USER_SETTINGS)
                .document(userId)
                .set(documentData)
                .await()

            context.dataStore.edit { prefs ->
                prefs[LastCloudSyncTimestampKey] = now
            }

            _syncStatus.value = SyncStatus.Success(now)
            Result.success(Unit)
        } catch (t: Throwable) {
            Timber.tag("SettingsSync").e(t, "Failed to push settings to Firestore")
            val msg = t.message ?: "Sync failed"
            _syncStatus.value = SyncStatus.Error(msg)
            Result.failure(t)
        }
    }

    suspend fun pullSettings(userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("User ID cannot be blank"))
        }
        _syncStatus.value = SyncStatus.Syncing
        try {
            val snapshot = firestore.collection(COLLECTION_USER_SETTINGS)
                .document(userId)
                .get()
                .await()

            if (!snapshot.exists()) {
                _syncStatus.value = SyncStatus.Idle
                return@withContext Result.success(false)
            }

            val rawSettings = snapshot.get("settings") as? Map<String, Any?> ?: emptyMap()
            val rawEncryptedSecrets = snapshot.get("encryptedSecrets") as? Map<String, Map<String, String>> ?: emptyMap()
            val lastSyncedAt = snapshot.getLong("lastSyncedAt") ?: System.currentTimeMillis()

            val decryptedSecrets = LinkedHashMap<String, String>()
            for ((keyName, secretMap) in rawEncryptedSecrets) {
                val ciphertext = secretMap["ciphertext"]
                val iv = secretMap["iv"]
                if (!ciphertext.isNullOrEmpty() && !iv.isNullOrEmpty()) {
                    val plaintext = encryptionService.decrypt(
                        SettingsEncryptionService.EncryptedValue(ciphertext = ciphertext, iv = iv)
                    )
                    decryptedSecrets[keyName] = plaintext
                }
            }

            context.dataStore.edit { prefs ->
                // Apply decrypted secrets
                for ((keyName, plaintext) in decryptedSecrets) {
                    prefs[stringPreferencesKey(keyName)] = plaintext
                }

                // Apply non-secret settings
                for ((keyName, value) in rawSettings) {
                    when (value) {
                        is Boolean -> {
                            prefs[booleanPreferencesKey(keyName)] = value
                        }
                        is String -> {
                            prefs[stringPreferencesKey(keyName)] = value
                        }
                        is Long -> {
                            if (keyName in INT_KEYS) {
                                prefs[intPreferencesKey(keyName)] = value.toInt()
                            } else {
                                prefs[longPreferencesKey(keyName)] = value
                            }
                        }
                        is Number -> {
                            prefs[floatPreferencesKey(keyName)] = value.toFloat()
                        }
                        is List<*> -> {
                            val set = value.mapNotNull { it?.toString() }.toSet()
                            prefs[stringSetPreferencesKey(keyName)] = set
                        }
                    }
                }

                prefs[LastCloudSyncTimestampKey] = lastSyncedAt
            }

            _syncStatus.value = SyncStatus.Success(lastSyncedAt)
            Result.success(true)
        } catch (t: Throwable) {
            Timber.tag("SettingsSync").e(t, "Failed to pull settings from Firestore")
            val msg = t.message ?: "Sync failed"
            _syncStatus.value = SyncStatus.Error(msg)
            Result.failure(t)
        }
    }

    suspend fun syncOnSignIn(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val pullResult = pullSettings(userId)
        if (pullResult.isSuccess && pullResult.getOrNull() == false) {
            // No remote backup exists yet for this user -> push local settings as initial backup
            pushSettings(userId)
        } else {
            pullResult.map { Unit }
        }
    }
}
