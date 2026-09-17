/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.buddy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuddyNotificationRelayClient @Inject constructor() {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val baseUrl: String
        get() = BuildConfig.RELAY_SERVICE_URL.trim().trimEnd('/')

    private val apiKey: String
        get() = BuildConfig.RELAY_SERVICE_API_KEY.trim()

    /**
     * Dispatches an FCM push notification to targetUid notifying them of a new incoming buddy request.
     */
    suspend fun notifyBuddyRequest(targetUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            Timber.tag("BuddyRelay").d("Relay service not configured (RELAY_SERVICE_URL or RELAY_SERVICE_API_KEY is empty). Skipping push.")
            return@withContext Result.success(Unit)
        }

        if (targetUid.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("targetUid cannot be blank"))
        }

        try {
            val jsonPayload = JSONObject().apply {
                put("targetUid", targetUid)
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/notify/buddy-request")
                .addHeader("X-Relay-Key", apiKey)
                .post(jsonPayload.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Timber.tag("BuddyRelay").d("Successfully relayed buddy request push notification to $targetUid")
                    Result.success(Unit)
                } else {
                    val code = response.code
                    val errorBody = response.body?.string().orEmpty()
                    Timber.tag("BuddyRelay").w("Relay push notification failed ($code): $errorBody")
                    Result.failure(RuntimeException("Relay returned HTTP $code: $errorBody"))
                }
            }
        } catch (t: Throwable) {
            Timber.tag("BuddyRelay").w(t, "Failed to send buddy request notification via relay")
            Result.failure(t)
        }
    }

    /**
     * Dispatches an FCM push notification to targetUid notifying them of an invite to a Together session.
     */
    suspend fun notifySessionInvite(
        targetUid: String,
        hostDisplayName: String,
        sessionCode: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            Timber.tag("BuddyRelay").d("Relay service not configured (RELAY_SERVICE_URL or RELAY_SERVICE_API_KEY is empty). Skipping push.")
            return@withContext Result.success(Unit)
        }

        if (targetUid.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("targetUid cannot be blank"))
        }

        try {
            val jsonPayload = JSONObject().apply {
                put("targetUid", targetUid)
                put("hostDisplayName", hostDisplayName.trim().ifBlank { "A buddy" })
                put("sessionCode", sessionCode.trim())
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/notify/invite")
                .addHeader("X-Relay-Key", apiKey)
                .post(jsonPayload.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Timber.tag("BuddyRelay").d("Successfully relayed Together session invite push notification to $targetUid")
                    Result.success(Unit)
                } else {
                    val code = response.code
                    val errorBody = response.body?.string().orEmpty()
                    Timber.tag("BuddyRelay").w("Relay invite push notification failed ($code): $errorBody")
                    Result.failure(RuntimeException("Relay returned HTTP $code: $errorBody"))
                }
            }
        } catch (t: Throwable) {
            Timber.tag("BuddyRelay").w(t, "Failed to send session invite notification via relay")
            Result.failure(t)
        }
    }
}
