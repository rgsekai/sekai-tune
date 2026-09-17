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
             Timber.tag("BuddyRelay").w("Relay service not configured (baseUrl='$baseUrl', apiKey is blank? ${apiKey.isBlank()}). Skipping push.")
             return@withContext Result.success(Unit)
         }

         if (targetUid.isBlank()) {
             return@withContext Result.failure(IllegalArgumentException("targetUid cannot be blank"))
         }

         try {
             val jsonPayload = JSONObject().apply {
                 put("targetUid", targetUid)
             }.toString()

             val targetUrl = "$baseUrl/notify/buddy-request"
             Timber.tag("BuddyRelay").d("Dispatching buddy request push to $targetUrl for targetUid=$targetUid")

             val request = Request.Builder()
                 .url(targetUrl)
                 .addHeader("X-Relay-Key", apiKey)
                 .post(jsonPayload.toRequestBody(jsonMediaType))
                 .build()

             httpClient.newCall(request).execute().use { response ->
                 val code = response.code
                 val respBody = response.body?.string().orEmpty()
                 if (response.isSuccessful) {
                     Timber.tag("BuddyRelay").d("Successfully relayed buddy request push to $targetUid (HTTP $code): $respBody")
                     Result.success(Unit)
                 } else {
                     Timber.tag("BuddyRelay").w("Relay push notification failed (HTTP $code): $respBody")
                     Result.failure(RuntimeException("Relay returned HTTP $code: $respBody"))
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
             Timber.tag("BuddyRelay").w("Relay service not configured (baseUrl='$baseUrl', apiKey is blank? ${apiKey.isBlank()}). Skipping push.")
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

             val targetUrl = "$baseUrl/notify/invite"
             Timber.tag("BuddyRelay").d("Dispatching Together session invite push to $targetUrl for targetUid=$targetUid, host=$hostDisplayName, code=$sessionCode")

             val request = Request.Builder()
                 .url(targetUrl)
                 .addHeader("X-Relay-Key", apiKey)
                 .post(jsonPayload.toRequestBody(jsonMediaType))
                 .build()

             httpClient.newCall(request).execute().use { response ->
                 val code = response.code
                 val respBody = response.body?.string().orEmpty()
                 if (response.isSuccessful) {
                     Timber.tag("BuddyRelay").d("Successfully relayed Together session invite push to $targetUid (HTTP $code): $respBody")
                     Result.success(Unit)
                 } else {
                     Timber.tag("BuddyRelay").w("Relay invite push notification failed (HTTP $code): $respBody")
                     Result.failure(RuntimeException("Relay returned HTTP $code: $respBody"))
                 }
             }
         } catch (t: Throwable) {
             Timber.tag("BuddyRelay").w(t, "Failed to send session invite notification via relay")
             Result.failure(t)
         }
     }
}
