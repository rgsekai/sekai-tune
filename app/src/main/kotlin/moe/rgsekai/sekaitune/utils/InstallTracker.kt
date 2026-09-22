/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit

object InstallTracker {
    private const val PREFS_NAME = "sekai_tune_prefs"
    private const val KEY_HAS_REPORTED_INSTALL = "has_reported_install"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Checks if this install has already been reported.
     * If not, performs a fire-and-forget ping to the Render relay endpoint.
     * Marks `has_reported_install` as true immediately so it never fires again.
     */
    suspend fun reportInstallIfNeeded(context: Context) {
        withContext(Dispatchers.IO) {
            val baseUrl = BuildConfig.RELAY_SERVICE_URL.trim().trimEnd('/')
            if (baseUrl.isBlank()) {
                Timber.tag("InstallTracker").w("Relay service URL is not configured. Skipping install report.")
                return@withContext
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val alreadyReported = prefs.getBoolean(KEY_HAS_REPORTED_INSTALL, false)
            if (alreadyReported) return@withContext

            // Mark as reported immediately on this device
            prefs.edit().putBoolean(KEY_HAS_REPORTED_INSTALL, true).apply()

            val targetUrl = "$baseUrl/record-install"
            runCatching {
                val request = Request.Builder()
                    .url(targetUrl)
                    .post("".toRequestBody(null))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.tag("InstallTracker").w("Install ping returned status %d", response.code)
                    }
                }
            }.onFailure { error ->
                Timber.tag("InstallTracker").w(error, "Install ping failed")
            }
        }
    }
}
