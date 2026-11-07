/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.discord

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import moe.rgsekai.sekaitune.BuildConfig
import moe.rgsekai.sekaitune.constants.DiscordAccessTokenKey
import moe.rgsekai.sekaitune.constants.DiscordLoginStateKey
import moe.rgsekai.sekaitune.constants.DiscordLoginVerifierKey
import moe.rgsekai.sekaitune.constants.DiscordProfileKey
import moe.rgsekai.sekaitune.constants.DiscordRefreshTokenKey
import moe.rgsekai.sekaitune.constants.DiscordTokenExpiresAtKey
import moe.rgsekai.sekaitune.utils.dataStore
import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DiscordLogin {
    data object Idle : DiscordLogin
    data object InBrowser : DiscordLogin
    data object Finishing : DiscordLogin
    data class Failed(val message: String) : DiscordLogin
}

@Singleton
class DiscordAuth @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshing = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val _login = MutableStateFlow<DiscordLogin>(DiscordLogin.Idle)
    val login: StateFlow<DiscordLogin> = _login.asStateFlow()

    val isConfigured: Boolean get() = ClientId.isNotBlank()

    val signedIn: Flow<Boolean> = context.dataStore.data
        .map { it[DiscordAccessTokenKey] != null }
        .distinctUntilChanged()

    val profile: Flow<DiscordProfile?> = context.dataStore.data
        .map { it[DiscordProfileKey] }
        .distinctUntilChanged()
        .map { DiscordProfile.fromJson(it) }

    suspend fun loginUri(): Uri {
        val state = randomCode(24)
        val verifier = randomCode(64)
        context.dataStore.edit {
            it[DiscordLoginStateKey] = state
            it[DiscordLoginVerifierKey] = verifier
        }
        _login.value = DiscordLogin.InBrowser
        return Uri.parse("https://discord.com/oauth2/authorize").buildUpon()
            .appendQueryParameter("client_id", ClientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", RedirectUri)
            .appendQueryParameter("scope", Scopes)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challengeOf(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
    }

    fun handleRedirect(redirect: Uri) {
        scope.launch { finish(redirect) }
    }

    suspend fun finish(redirect: Uri) {
        Timber.tag(LogTag).i(
            "Finishing Discord OAuth: ClientId=%s, RedirectUri=%s, Scopes=%s",
            ClientId, RedirectUri, Scopes,
        )
        val prefs = context.dataStore.data.first()
        val expectedState = prefs[DiscordLoginStateKey]
        val verifier = prefs[DiscordLoginVerifierKey]
        if (expectedState == null || verifier == null || redirect.getQueryParameter("state") != expectedState) {
            Timber.tag(LogTag).w("Discord state mismatch or expired. redirect state=%s, expected=%s", redirect.getQueryParameter("state"), expectedState)
            _login.value = DiscordLogin.Failed("That sign-in link had expired. Try logging in again.")
            return
        }
        context.dataStore.edit {
            it.remove(DiscordLoginStateKey)
            it.remove(DiscordLoginVerifierKey)
        }
        val code = redirect.getQueryParameter("code")
        if (code == null) {
            val error = redirect.getQueryParameter("error")
            val errorDescription = redirect.getQueryParameter("error_description")
            Timber.tag(LogTag).w(
                "Discord authorization failed (no code returned): error=%s, error_description=%s",
                error, errorDescription,
            )
            _login.value = DiscordLogin.Failed(
                if (error == "access_denied") {
                    "Sign-in was cancelled on Discord's page."
                } else {
                    "Discord didn't let the sign-in through. Try again."
                },
            )
            return
        }

        _login.value = DiscordLogin.Finishing
        try {
            Timber.tag(LogTag).i(
                "Sending POST /oauth2/token: client_id=%s, redirect_uri=%s, grant_type=authorization_code",
                ClientId, RedirectUri,
            )
            val reply = DiscordHttp.postForm(
                "/oauth2/token",
                mapOf(
                    "client_id" to ClientId,
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to RedirectUri,
                    "code_verifier" to verifier,
                ),
            )
            Timber.tag(LogTag).i(
                "POST /oauth2/token response: HTTP %d, body=%s",
                reply.code,
                if (reply.ok) "<success, tokens redacted>" else reply.body,
            )
            check(reply.ok) { "token exchange failed: HTTP ${reply.code} ${reply.body.take(200)}" }
            val token = saveTokens(reply.body)
            val profile = DiscordProfile.fetch(token)
            context.dataStore.edit { it[DiscordProfileKey] = profile.toJson() }
            _login.value = DiscordLogin.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(LogTag).w(e, "Discord sign-in didn't finish")
            _login.value = DiscordLogin.Failed("Couldn't finish signing in to Discord. Check your connection and try again.")
        }
    }

    fun cancelWaiting() {
        if (_login.value == DiscordLogin.InBrowser) _login.value = DiscordLogin.Idle
    }

    fun dismissProblem() {
        if (_login.value is DiscordLogin.Failed) _login.value = DiscordLogin.Idle
    }

    suspend fun accessToken(): String? {
        val prefs = context.dataStore.data.first()
        val token = prefs[DiscordAccessTokenKey] ?: return null
        val expiresAt = prefs[DiscordTokenExpiresAtKey] ?: 0L
        if (expiresAt == 0L || System.currentTimeMillis() < expiresAt - RenewEarlyMs) return token
        return renew(token)
    }

    suspend fun renew(rejected: String): String? = refreshing.withLock {
        val prefs = context.dataStore.data.first()
        val current = prefs[DiscordAccessTokenKey] ?: return@withLock null
        if (current != rejected) return@withLock current
        val refreshToken = prefs[DiscordRefreshTokenKey] ?: return@withLock null.also { signOutHere() }
        val reply = try {
            DiscordHttp.postForm(
                "/oauth2/token",
                mapOf(
                    "client_id" to ClientId,
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withLock current
        }
        when {
            reply.ok -> saveTokens(reply.body)
            reply.code in 400..401 -> null.also { signOutHere() }
            else -> current
        }
    }

    suspend fun refreshProfile() {
        val token = accessToken() ?: return
        runCatching { DiscordProfile.fetch(token) }
            .onSuccess { profile -> context.dataStore.edit { it[DiscordProfileKey] = profile.toJson() } }
    }

    suspend fun logOut() {
        val token = context.dataStore.data.first()[DiscordAccessTokenKey]
        signOutHere()
        if (token != null) {
            runCatching {
                DiscordHttp.postForm(
                    "/oauth2/token/revoke",
                    mapOf("client_id" to ClientId, "token" to token, "token_type_hint" to "access_token"),
                )
            }
        }
    }

    suspend fun forgetOldLogin() {
        val prefs = context.dataStore.data.first()
        if (LegacyKeys.none { prefs.contains(it) }) return
        context.dataStore.edit { stored -> LegacyKeys.forEach { stored.remove(it) } }
    }

    private suspend fun signOutHere() {
        context.dataStore.edit {
            it.remove(DiscordAccessTokenKey)
            it.remove(DiscordRefreshTokenKey)
            it.remove(DiscordTokenExpiresAtKey)
            it.remove(DiscordProfileKey)
        }
    }

    private suspend fun saveTokens(body: String): String {
        val reply = json.parseToJsonElement(body).jsonObject
        val access = reply["access_token"]?.jsonPrimitive?.contentOrNull ?: error("no access token in Discord's reply")
        val refresh = reply["refresh_token"]?.jsonPrimitive?.contentOrNull
        val expiresIn = reply["expires_in"]?.jsonPrimitive?.longOrNull ?: 0L
        context.dataStore.edit {
            it[DiscordAccessTokenKey] = access
            if (refresh != null) it[DiscordRefreshTokenKey] = refresh
            it[DiscordTokenExpiresAtKey] = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else 0L
        }
        return access
    }

    private fun randomCode(bytes: Int): String {
        val raw = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun challengeOf(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    companion object {
        private const val LogTag = "DiscordAuth"

        val ClientId: String get() = BuildConfig.DISCORD_CLIENT_ID
        val RedirectUri: String get() = "discord-$ClientId:/authorize/callback"

        private const val Scopes = "openid identify sdk.social_layer_presence"
        private const val RenewEarlyMs = 10 * 60_000L

        private val LegacyKeys: List<Preferences.Key<*>> = listOf(
            "discordToken", "discordUsername", "discordName", "discordAvatar", "discordStatus",
            "discordButton1Text", "discordButton2Text", "discordActivityName",
        ).map { stringPreferencesKey(it) } + listOf(
            "discordInfoDismissed", "discordUseDetails", "discordButton1Visible", "discordButton2Visible",
            "discordAdvancedMode",
        ).map { booleanPreferencesKey(it) }
    }
}

