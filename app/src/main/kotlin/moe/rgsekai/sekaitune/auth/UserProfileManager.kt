/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class PublicUserProfile(
    val uid: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
)

@Singleton
class UserProfileManager @Inject constructor() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null && !user.isAnonymous) {
                scope.launch {
                    syncUserProfile(user)
                }
            }
        }
    }

    suspend fun syncUserProfile(user: FirebaseUser? = auth.currentUser): Result<Unit> = withContext(Dispatchers.IO) {
        if (user == null || user.uid.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No authenticated user to sync profile"))
        }

        try {
            val uid = user.uid
            val photoUrl = user.photoUrl?.toString()?.ifBlank { null }
            val docRef = firestore.collection("users").document(uid)

            // Check if document already exists to preserve any existing custom displayName
            val existingDoc = try {
                docRef.get().await()
            } catch (t: Throwable) {
                Timber.tag("UserProfileManager").w(t, "Failed to check existing profile for $uid")
                null
            }

            val existingDisplayName = existingDoc?.getString("displayName")?.ifBlank { null }
            val resolvedDisplayName = existingDisplayName ?: user.displayName?.ifBlank { null } ?: "User"

            val data = mutableMapOf<String, Any?>(
                "uid" to uid,
                "displayName" to resolvedDisplayName,
                "photoUrl" to photoUrl,
                "updatedAt" to System.currentTimeMillis(),
            )

            docRef.set(data, SetOptions.merge()).await()
            Timber.tag("UserProfileManager").d("Synced public user profile for $uid (name: $resolvedDisplayName, photo: $photoUrl)")
            Result.success(Unit)
        } catch (t: Throwable) {
            Timber.tag("UserProfileManager").e(t, "Failed to sync public user profile")
            Result.failure(t)
        }
    }

    suspend fun updateDisplayName(newDisplayName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid
            ?: return@withContext Result.failure(IllegalStateException("User is not authenticated"))
        if (newDisplayName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Display name cannot be blank"))
        }

        try {
            val docRef = firestore.collection("users").document(uid)
            val data = mapOf(
                "displayName" to newDisplayName,
                "updatedAt" to System.currentTimeMillis(),
            )
            docRef.set(data, SetOptions.merge()).await()
            Timber.tag("UserProfileManager").d("Updated public displayName for $uid: $newDisplayName")
            Result.success(Unit)
        } catch (t: Throwable) {
            Timber.tag("UserProfileManager").e(t, "Failed to update public displayName for $uid")
            Result.failure(t)
        }
    }

    suspend fun fetchUserProfile(uid: String): PublicUserProfile? = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext null
        try {
            val doc = firestore.collection("users").document(uid).get().await()
            if (doc.exists()) {
                val displayName = doc.getString("displayName") ?: ""
                val photoUrl = doc.getString("photoUrl")?.ifBlank { null }
                PublicUserProfile(uid = uid, displayName = displayName, photoUrl = photoUrl)
            } else {
                null
            }
        } catch (t: Throwable) {
            Timber.tag("UserProfileManager").w(t, "Failed to fetch public user profile for $uid")
            null
        }
    }
}
