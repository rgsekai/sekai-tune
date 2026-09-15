/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.buddy

import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuddyFcmTokenManager @Inject constructor() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun registerCurrentToken(): Result<String> {
        return try {
            val uid = auth.currentUser?.uid
            if (uid.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Cannot register FCM token: user is not authenticated"))
            }

            val token = FirebaseMessaging.getInstance().token.await()
            if (token.isNullOrBlank()) {
                return Result.failure(IllegalStateException("FCM token is null or blank"))
            }

            updateTokenInFirestore(uid, token)
            Result.success(token)
        } catch (t: Throwable) {
            Timber.tag("BuddyFCM").e(t, "Failed to register current FCM token")
            Result.failure(t)
        }
    }

    suspend fun updateToken(token: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid
            if (uid.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Cannot update FCM token: user is not authenticated"))
            }

            updateTokenInFirestore(uid, token)
            Result.success(Unit)
        } catch (t: Throwable) {
            Timber.tag("BuddyFCM").e(t, "Failed to update FCM token")
            Result.failure(t)
        }
    }

    private suspend fun updateTokenInFirestore(uid: String, token: String) {
        val tokenId = hashTokenToId(token)
        val tokenDoc = firestore.collection("users")
            .document(uid)
            .collection("fcmTokens")
            .document(tokenId)

        val tokenData = mapOf(
            "token" to token,
            "deviceModel" to (Build.MODEL ?: "Android Device"),
            "updatedAt" to System.currentTimeMillis()
        )

        tokenDoc.set(tokenData).await()
        Timber.tag("BuddyFCM").d("Successfully registered FCM token for user $uid")
    }

    private fun hashTokenToId(token: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(token.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            token.take(64).filter { it.isLetterOrDigit() }
        }
    }
}
