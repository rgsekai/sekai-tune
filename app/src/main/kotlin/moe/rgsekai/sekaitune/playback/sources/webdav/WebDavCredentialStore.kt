/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources.webdav

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.rgsekai.sekaitune.webdav.WebDavAuth
import moe.rgsekai.sekaitune.webdav.WebDavConfig
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages storage and lifecycle for WebDAV server configuration and credentials.
 *
 * - Server URL, Username, TrustSelfSigned flag, and Password are ALL stored in
 *   [EncryptedSharedPreferences] backed by Android Keystore.
 * - One-time automatic migration transitions any existing plain preferences to the encrypted store.
 * - If the Keystore is corrupted or keys cannot be unwrapped, the encrypted store is deleted
 *   and safely recreated to avoid app crashes.
 * - Automatically publishes active credentials to in-memory [WebDavAuth].
 */
@Singleton
class WebDavCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val securePrefs: SharedPreferences by lazy {
        openSecurePreferences(context)
    }

    private val _urlFlow = MutableStateFlow("")
    val urlFlow: StateFlow<String> = _urlFlow.asStateFlow()

    private val _usernameFlow = MutableStateFlow("")
    val usernameFlow: StateFlow<String> = _usernameFlow.asStateFlow()

    private val _trustSelfSignedFlow = MutableStateFlow(false)
    val trustSelfSignedFlow: StateFlow<Boolean> = _trustSelfSignedFlow.asStateFlow()

    private val _isConfiguredFlow = MutableStateFlow(false)
    val isConfiguredFlow: StateFlow<Boolean> = _isConfiguredFlow.asStateFlow()

    private val _wifiOnlyTagsFlow = MutableStateFlow(true)
    val wifiOnlyTagsFlow: StateFlow<Boolean> = _wifiOnlyTagsFlow.asStateFlow()

    private val _resolveMatchingFlow = MutableStateFlow(false)
    val resolveMatchingFlow: StateFlow<Boolean> = _resolveMatchingFlow.asStateFlow()

    init {
        migrateFromPlainPrefs()
        loadAndPublish()
    }

    /**
     * Reloads credentials from disk and publishes them to in-memory [WebDavAuth].
     */
    fun refreshAuth() {
        loadAndPublish()
    }

    val url: String
        get() = securePrefs.getString(KEY_URL, "").orEmpty()

    val username: String
        get() = securePrefs.getString(KEY_USERNAME, "").orEmpty()

    val trustSelfSigned: Boolean
        get() = securePrefs.getBoolean(KEY_TRUST_SELF_SIGNED, false)

    val password: String
        get() = securePrefs.getString(KEY_PASSWORD, "").orEmpty()

    val wifiOnlyTags: Boolean
        get() = securePrefs.getBoolean(KEY_WIFI_ONLY_TAGS, true)

    val resolveMatching: Boolean
        get() = securePrefs.getBoolean(KEY_RESOLVE_MATCHING, false)

    val isConfigured: Boolean
        get() = WebDavConfig.splitAddress(url) != null

    fun setWifiOnlyTags(enabled: Boolean) {
        securePrefs.edit().putBoolean(KEY_WIFI_ONLY_TAGS, enabled).apply()
        _wifiOnlyTagsFlow.value = enabled
    }

    fun setResolveMatching(enabled: Boolean) {
        securePrefs.edit().putBoolean(KEY_RESOLVE_MATCHING, enabled).apply()
        _resolveMatchingFlow.value = enabled
    }

    /**
     * Migrates legacy plain SharedPreferences into the encrypted store and deletes the unencrypted file.
     */
    private fun migrateFromPlainPrefs() {
        try {
            val plain = context.getSharedPreferences(PREFS_PLAIN, Context.MODE_PRIVATE)
            val hasPlainUrl = plain.contains(KEY_URL)
            val hasPlainUser = plain.contains(KEY_USERNAME)
            if (hasPlainUrl || hasPlainUser) {
                val oldUrl = plain.getString(KEY_URL, null)
                val oldUser = plain.getString(KEY_USERNAME, null)
                val oldTrust = plain.getBoolean(KEY_TRUST_SELF_SIGNED, false)

                val editor = securePrefs.edit()
                if (!oldUrl.isNullOrBlank() && !securePrefs.contains(KEY_URL)) {
                    editor.putString(KEY_URL, WebDavConfig.normalizeUrl(oldUrl))
                }
                if (!oldUser.isNullOrBlank() && !securePrefs.contains(KEY_USERNAME)) {
                    editor.putString(KEY_USERNAME, oldUser.trim())
                }
                if (plain.contains(KEY_TRUST_SELF_SIGNED) && !securePrefs.contains(KEY_TRUST_SELF_SIGNED)) {
                    editor.putBoolean(KEY_TRUST_SELF_SIGNED, oldTrust)
                }
                editor.apply()

                plain.edit().clear().apply()
                context.deleteSharedPreferences(PREFS_PLAIN)
            }
        } catch (e: Throwable) {
            Timber.w(e, "WebDAV plain-to-encrypted preference migration failed")
        }
    }

    /**
     * Saves new WebDAV connection details, updates secure password storage,
     * and refreshes in-memory [WebDavAuth].
     *
     * Automatically strips embedded credentials (user:pass@) if typed into the URL.
     */
    fun saveCredentials(
        url: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ) {
        val extracted = WebDavConfig.extractCredentialsFromUrl(url)
        val finalUrl = WebDavConfig.normalizeUrl(extracted.cleanUrl)
        val finalUser = (extracted.username ?: username).trim()
        val finalPass = extracted.password ?: password

        securePrefs.edit()
            .putString(KEY_URL, finalUrl)
            .putString(KEY_USERNAME, finalUser)
            .putString(KEY_PASSWORD, finalPass)
            .putBoolean(KEY_TRUST_SELF_SIGNED, trustSelfSigned)
            .apply()

        loadAndPublish()
    }

    /**
     * Clears all saved credentials, trust flags, and resets in-memory [WebDavAuth].
     */
    fun disconnect() {
        securePrefs.edit().clear().apply()
        runCatching { context.deleteSharedPreferences(PREFS_PLAIN) }

        // HOOK: Phase 2 will also offer deleting scanned WebDAV Room tracks and cached artwork here.

        WebDavAuth.clear()
        _urlFlow.value = ""
        _usernameFlow.value = ""
        _trustSelfSignedFlow.value = false
        _isConfiguredFlow.value = false
        _wifiOnlyTagsFlow.value = true
        _resolveMatchingFlow.value = false
    }

    private fun loadAndPublish() {
        val currentUrl = url
        val currentUsername = username
        val currentTrust = trustSelfSigned
        val currentPassword = password
        val configured = WebDavConfig.splitAddress(currentUrl) != null

        _urlFlow.value = currentUrl
        _usernameFlow.value = currentUsername
        _trustSelfSignedFlow.value = currentTrust
        _isConfiguredFlow.value = configured
        _wifiOnlyTagsFlow.value = wifiOnlyTags
        _resolveMatchingFlow.value = resolveMatching

        if (configured) {
            WebDavAuth.update(
                url = currentUrl,
                username = currentUsername,
                password = currentPassword,
                trustSelfSignedCert = currentTrust,
            )
        } else {
            WebDavAuth.clear()
        }
    }

    private fun openSecurePreferences(context: Context): SharedPreferences {
        return try {
            createEncryptedPreferences(context)
        } catch (e: Throwable) {
            Timber.w(e, "EncryptedSharedPreferences initialization failed; attempting recovery")
            // Keystore keyset might be stale from a backup restore without keys. Delete and recreate.
            context.deleteSharedPreferences(PREFS_SECURE)
            try {
                createEncryptedPreferences(context)
            } catch (retryException: Throwable) {
                Timber.e(retryException, "Failed to recreate EncryptedSharedPreferences; degrading to fallback store")
                context.getSharedPreferences(PREFS_FALLBACK, Context.MODE_PRIVATE)
            }
        }
    }

    private fun createEncryptedPreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_SECURE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    companion object {
        private const val PREFS_PLAIN = "sekaitune_webdav_config"
        private const val PREFS_SECURE = "sekaitune_webdav_auth"
        private const val PREFS_FALLBACK = "sekaitune_webdav_auth_fallback"

        private const val KEY_URL = "webdav_url"
        private const val KEY_USERNAME = "webdav_username"
        private const val KEY_TRUST_SELF_SIGNED = "webdav_trust_self_signed"
        private const val KEY_PASSWORD = "webdav_password"
        private const val KEY_WIFI_ONLY_TAGS = "webdav_wifi_only_tags"
        private const val KEY_RESOLVE_MATCHING = "webdav_resolve_matching"
    }
}
