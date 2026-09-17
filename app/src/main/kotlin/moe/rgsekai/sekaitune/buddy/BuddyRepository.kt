/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rgsekai.sekaitune.buddy

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.constants.DismissedTogetherInviteIdsKey
import moe.rgsekai.sekaitune.utils.dataStore
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuddyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val relayClient: BuddyNotificationRelayClient,
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val localDismissed = MutableStateFlow<Set<String>>(emptySet())

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.dataStore.data
                    .map { it[DismissedTogetherInviteIdsKey] ?: emptySet() }
                    .collect { dismissed ->
                        localDismissed.value = dismissed
                    }
            } catch (t: Throwable) {
                Timber.tag("BuddyRepository").w(t, "Failed to load dismissed invites from DataStore")
            }
        }
    }

    val currentUid: String?
        get() = auth.currentUser?.uid

    suspend fun sendBuddyRequest(
        toUid: String,
        fromDisplayName: String,
        toDisplayName: String = "",
        fromPhotoUrl: String? = null,
        toPhotoUrl: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val myUid = auth.currentUser?.uid
            ?: return@withContext Result.failure(IllegalStateException("User is not authenticated"))

        if (toUid.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Target user ID cannot be blank"))
        }

        if (myUid == toUid) {
            return@withContext Result.failure(IllegalArgumentException("Cannot send buddy request to yourself"))
        }

        try {
            val resolvedFromPhotoUrl = fromPhotoUrl?.ifBlank { null }
                ?: auth.currentUser?.photoUrl?.toString()?.ifBlank { null }

            val docRef = firestore.collection("buddy_requests").document()
            val requestData = mutableMapOf<String, Any?>(
                "fromUid" to myUid,
                "toUid" to toUid,
                "fromDisplayName" to fromDisplayName,
                "toDisplayName" to toDisplayName,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis()
            )
            if (resolvedFromPhotoUrl != null) {
                requestData["fromPhotoUrl"] = resolvedFromPhotoUrl
            }
            if (!toPhotoUrl.isNullOrBlank()) {
                requestData["toPhotoUrl"] = toPhotoUrl
            }

            docRef.set(requestData).await()
            Timber.tag("BuddyRepository").d("Successfully sent buddy request to $toUid with doc ID ${docRef.id}")

            // Asynchronously dispatch FCM push notification via self-hosted relay (fire-and-forget)
            launch {
                relayClient.notifyBuddyRequest(toUid)
            }

            Result.success(Unit)
        } catch (t: Throwable) {
            Timber.tag("BuddyRepository").e(t, "Failed to send buddy request to $toUid")
            Result.failure(t)
        }
    }

    fun observeBuddies(): Flow<List<Buddy>> = callbackFlow {
        val myUid = auth.currentUser?.uid
        if (myUid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener: ListenerRegistration = firestore.collection("buddies")
            .document(myUid)
            .collection("list")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.tag("BuddyRepository").e(error, "Error listening to buddies list for $myUid")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val buddies = snapshot?.documents?.mapNotNull { doc ->
                    val uid = doc.getString("uid") ?: doc.id
                    val displayName = doc.getString("displayName") ?: ""
                    val addedAt = doc.extractTimestampMs("addedAt")
                    val photoUrl = doc.getString("photoUrl")?.ifBlank { null }
                    Buddy(
                        uid = uid,
                        displayName = displayName,
                        addedAt = addedAt,
                        photoUrl = photoUrl,
                    )
                } ?: emptyList()

                trySend(buddies)

                // Asynchronously fetch photoUrl for any legacy buddy with missing photo
                val missingPhotoBuddies = buddies.filter { it.photoUrl.isNullOrBlank() }
                if (missingPhotoBuddies.isNotEmpty()) {
                    launch {
                        var hasUpdates = false
                        val updatedList = buddies.map { b ->
                            if (b.photoUrl.isNullOrBlank()) {
                                val fetchedPhoto = try {
                                    val userDoc = firestore.collection("users").document(b.uid).get().await()
                                    userDoc.getString("photoUrl")?.ifBlank { null }
                                } catch (_: Throwable) {
                                    null
                                }
                                if (fetchedPhoto != null) {
                                    hasUpdates = true
                                    try {
                                        firestore.collection("buddies")
                                            .document(myUid)
                                            .collection("list")
                                            .document(b.uid)
                                            .update("photoUrl", fetchedPhoto)
                                            .await()
                                    } catch (_: Throwable) {}
                                    b.copy(photoUrl = fetchedPhoto)
                                } else {
                                    b
                                }
                            } else {
                                b
                            }
                        }
                        if (hasUpdates) {
                            trySend(updatedList)
                        }
                    }
                }
            }

        awaitClose {
            listener.remove()
        }
    }.flowOn(Dispatchers.IO)

    fun observeIncomingRequests(): Flow<List<BuddyRequest>> = callbackFlow {
        val myUid = auth.currentUser?.uid
        if (myUid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener: ListenerRegistration = firestore.collection("buddy_requests")
            .whereEqualTo("toUid", myUid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.tag("BuddyRepository").e(error, "Error listening to incoming buddy requests for $myUid")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val requests = snapshot?.documents?.mapNotNull { doc ->
                    val fromUid = doc.getString("fromUid") ?: return@mapNotNull null
                    val toUid = doc.getString("toUid") ?: return@mapNotNull null
                    val fromDisplayName = doc.getString("fromDisplayName") ?: ""
                    val toDisplayName = doc.getString("toDisplayName") ?: ""
                    val status = doc.getString("status") ?: "pending"
                    val createdAt = doc.extractTimestampMs("createdAt")
                    val fromPhotoUrl = doc.getString("fromPhotoUrl")?.ifBlank { null }
                    val toPhotoUrl = doc.getString("toPhotoUrl")?.ifBlank { null }

                    BuddyRequest(
                        id = doc.id,
                        fromUid = fromUid,
                        toUid = toUid,
                        fromDisplayName = fromDisplayName,
                        toDisplayName = toDisplayName,
                        status = status,
                        createdAt = createdAt,
                        fromPhotoUrl = fromPhotoUrl,
                        toPhotoUrl = toPhotoUrl,
                    )
                } ?: emptyList()

                trySend(requests)

                // Asynchronously resolve missing fromPhotoUrl for incoming requests
                val missingRequests = requests.filter { it.fromPhotoUrl.isNullOrBlank() }
                if (missingRequests.isNotEmpty()) {
                    launch {
                        var hasUpdates = false
                        val updatedList = requests.map { req ->
                            if (req.fromPhotoUrl.isNullOrBlank()) {
                                val fetchedPhoto = try {
                                    val userDoc = firestore.collection("users").document(req.fromUid).get().await()
                                    userDoc.getString("photoUrl")?.ifBlank { null }
                                } catch (_: Throwable) {
                                    null
                                }
                                if (fetchedPhoto != null) {
                                    hasUpdates = true
                                    req.copy(fromPhotoUrl = fetchedPhoto)
                                } else {
                                    req
                                }
                            } else {
                                req
                            }
                        }
                        if (hasUpdates) {
                            trySend(updatedList)
                        }
                    }
                }
            }

        awaitClose {
            listener.remove()
        }
    }.flowOn(Dispatchers.IO)

    fun observeOutgoingRequests(): Flow<List<BuddyRequest>> = callbackFlow {
        val myUid = auth.currentUser?.uid
        if (myUid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener: ListenerRegistration = firestore.collection("buddy_requests")
            .whereEqualTo("fromUid", myUid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.tag("BuddyRepository").e(error, "Error listening to outgoing buddy requests for $myUid")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val requests = snapshot?.documents?.mapNotNull { doc ->
                    val fromUid = doc.getString("fromUid") ?: return@mapNotNull null
                    val toUid = doc.getString("toUid") ?: return@mapNotNull null
                    val fromDisplayName = doc.getString("fromDisplayName") ?: ""
                    val toDisplayName = doc.getString("toDisplayName") ?: ""
                    val status = doc.getString("status") ?: "pending"
                    val createdAt = doc.extractTimestampMs("createdAt")
                    val fromPhotoUrl = doc.getString("fromPhotoUrl")?.ifBlank { null }
                    val toPhotoUrl = doc.getString("toPhotoUrl")?.ifBlank { null }

                    BuddyRequest(
                        id = doc.id,
                        fromUid = fromUid,
                        toUid = toUid,
                        fromDisplayName = fromDisplayName,
                        toDisplayName = toDisplayName,
                        status = status,
                        createdAt = createdAt,
                        fromPhotoUrl = fromPhotoUrl,
                        toPhotoUrl = toPhotoUrl,
                    )
                } ?: emptyList()

                trySend(requests)
            }

        awaitClose {
            listener.remove()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun acceptRequest(
        requestId: String,
        toDisplayName: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val myUid = auth.currentUser?.uid
            ?: return@withContext Result.failure(IllegalStateException("User is not authenticated"))

        try {
            val reqDoc = firestore.collection("buddy_requests").document(requestId).get().await()
            if (!reqDoc.exists()) {
                return@withContext Result.failure(IllegalStateException("Buddy request does not exist"))
            }

            val fromUid = reqDoc.getString("fromUid")
                ?: return@withContext Result.failure(IllegalStateException("Invalid request data: missing sender UID"))
            val fromDisplayName = reqDoc.getString("fromDisplayName")?.ifBlank { null } ?: "Buddy"
            var fromPhotoUrl = reqDoc.getString("fromPhotoUrl")?.ifBlank { null }
            if (fromPhotoUrl == null) {
                // Fallback to reading sender's public users/{uid} document
                try {
                    val userDoc = firestore.collection("users").document(fromUid).get().await()
                    fromPhotoUrl = userDoc.getString("photoUrl")?.ifBlank { null }
                } catch (_: Throwable) {
                    // Ignore fallback failure
                }
            }

            val resolvedMyName = toDisplayName?.ifBlank { null }
                ?: auth.currentUser?.displayName?.ifBlank { null }
                ?: reqDoc.getString("toDisplayName")?.ifBlank { null }
                ?: "Buddy"
            val myPhotoUrl = auth.currentUser?.photoUrl?.toString()?.ifBlank { null }

            val batch = firestore.batch()

            // 1. Add sender to my buddy list: /buddies/{myUid}/list/{fromUid}
            val myBuddyRef = firestore.collection("buddies")
                .document(myUid)
                .collection("list")
                .document(fromUid)
            val myBuddyData = mutableMapOf<String, Any?>(
                "uid" to fromUid,
                "displayName" to fromDisplayName,
                "addedAt" to FieldValue.serverTimestamp(),
            )
            if (fromPhotoUrl != null) {
                myBuddyData["photoUrl"] = fromPhotoUrl
            }
            batch.set(myBuddyRef, myBuddyData)

            // 2. Add myself to sender's buddy list: /buddies/{fromUid}/list/{myUid}
            val otherBuddyRef = firestore.collection("buddies")
                .document(fromUid)
                .collection("list")
                .document(myUid)
            val otherBuddyData = mutableMapOf<String, Any?>(
                "uid" to myUid,
                "displayName" to resolvedMyName,
                "addedAt" to FieldValue.serverTimestamp(),
            )
            if (myPhotoUrl != null) {
                otherBuddyData["photoUrl"] = myPhotoUrl
            }
            batch.set(otherBuddyRef, otherBuddyData)

            // 3. Atomically delete the buddy_requests document
            val reqRef = firestore.collection("buddy_requests").document(requestId)
            batch.delete(reqRef)

            batch.commit().await()

            Timber.tag("BuddyRepository").d("Successfully accepted buddy request $requestId")
            Result.success(Unit)
        } catch (t: Throwable) {
            Timber.tag("BuddyRepository").e(t, "Failed to accept buddy request $requestId")
            Result.failure(t)
        }
    }

    suspend fun rejectRequest(requestId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val myUid = auth.currentUser?.uid
            ?: return@withContext Result.failure(IllegalStateException("User is not authenticated"))

        try {
            firestore.collection("buddy_requests")
                .document(requestId)
                .delete()
                .await()

            Timber.tag("BuddyRepository").d("Successfully rejected buddy request $requestId")
            Result.success(Unit)
        } catch (t: Throwable) {
            Timber.tag("BuddyRepository").e(t, "Failed to reject buddy request $requestId")
            Result.failure(t)
        }
    }

    suspend fun removeBuddy(buddyUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val myUid = auth.currentUser?.uid
            ?: return@withContext Result.failure(IllegalStateException("User is not authenticated"))

        try {
            val batch = firestore.batch()

            // Delete buddies/{myUid}/list/{buddyUid}
            val myBuddyRef = firestore.collection("buddies")
                .document(myUid)
                .collection("list")
                .document(buddyUid)
            batch.delete(myBuddyRef)

            // Delete buddies/{buddyUid}/list/{myUid}
            val otherBuddyRef = firestore.collection("buddies")
                .document(buddyUid)
                .collection("list")
                .document(myUid)
            batch.delete(otherBuddyRef)

            batch.commit().await()

            Timber.tag("BuddyRepository").d("Successfully removed buddy $buddyUid")
            Result.success(Unit)
        } catch (t: Throwable) {
            Timber.tag("BuddyRepository").e(t, "Failed to remove buddy $buddyUid")
            Result.failure(t)
        }
    }

    suspend fun cancelOutgoingRequest(requestId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("buddy_requests")
                .document(requestId)
                .delete()
                .await()

            Timber.tag("BuddyRepository").d("Successfully cancelled outgoing buddy request $requestId")
            Result.success(Unit)
        } catch (t: Throwable) {
            Timber.tag("BuddyRepository").e(t, "Failed to cancel outgoing buddy request $requestId")
            Result.failure(t)
        }
    }

    suspend fun inviteBuddyToSession(
        sessionId: String,
        buddyUid: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val myUid = auth.currentUser?.uid
            ?: return@withContext Result.failure(IllegalStateException("User is not authenticated"))

        if (sessionId.isBlank() || buddyUid.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Session ID and Buddy UID cannot be blank"))
        }

        try {
            // 1. Add buddyUid to together_sessions/{sessionId}.invitedUids (array union, additive)
            val sessionDocRef = firestore.collection("together_sessions").document(sessionId)
            sessionDocRef.update("invitedUids", FieldValue.arrayUnion(buddyUid)).await()
            Timber.tag("BuddyRepository").d("Successfully invited buddy $buddyUid to session $sessionId")

            // 2. Asynchronously dispatch FCM push notification via self-hosted relay (fire-and-forget)
            launch {
                val hostName = auth.currentUser?.displayName?.ifBlank { null } ?: "A buddy"
                val sessionCode = runCatching {
                    sessionDocRef.get().await().getString("code")
                }.getOrNull().orEmpty()

                relayClient.notifySessionInvite(
                    targetUid = buddyUid,
                    hostDisplayName = hostName,
                    sessionCode = sessionCode,
                )
            }

            Result.success(Unit)
        } catch (t: Throwable) {
            Timber.tag("BuddyRepository").e(t, "Failed to invite buddy $buddyUid to session $sessionId")
            Result.failure(t)
        }
    }

    suspend fun dismissSessionInvite(
        sessionId: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val myUid = auth.currentUser?.uid
            ?: return@withContext Result.failure(IllegalStateException("User is not authenticated"))

        if (sessionId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Session ID cannot be blank"))
        }

        // 1. Immediately drop from local reactive state so UI drops it instantly
        localDismissed.update { it + sessionId }

        // 2. Persist to DataStore so it survives app restarts
        try {
            context.dataStore.edit { prefs ->
                val current = prefs[DismissedTogetherInviteIdsKey] ?: emptySet()
                prefs[DismissedTogetherInviteIdsKey] = current + sessionId
            }
        } catch (t: Throwable) {
            Timber.tag("BuddyRepository").w(t, "Failed to persist dismissed invite to DataStore")
        }

        // 3. Best-effort Firestore update to remove UID from doc
        try {
            firestore.collection("together_sessions")
                .document(sessionId)
                .update("invitedUids", FieldValue.arrayRemove(myUid))
                .await()
            Timber.tag("BuddyRepository").d("Successfully dismissed session invite $sessionId for user $myUid")
        } catch (t: Throwable) {
            Timber.tag("BuddyRepository").w(t, "Non-fatal: could not remove invitedUid from Firestore doc $sessionId")
        }

        Result.success(Unit)
    }

    fun observeInvitedSessions(): Flow<List<TogetherSessionSummary>> {
        val myUid = auth.currentUser?.uid
        if (myUid == null) {
            return flowOf(emptyList())
        }

        val firestoreFlow = callbackFlow {
            val listener: ListenerRegistration = firestore.collection("together_sessions")
                .whereArrayContains("invitedUids", myUid)
                .whereEqualTo("active", true)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.tag("BuddyRepository").e(error, "Error listening to invited sessions for $myUid")
                        trySend(emptyList())
                        return@addSnapshotListener
                    }

                    val now = System.currentTimeMillis()
                    val rawSessions = snapshot?.documents?.mapNotNull { doc ->
                        val sessionId = doc.getString("sessionId") ?: doc.id
                        val code = doc.getString("code") ?: return@mapNotNull null
                        val hostId = doc.getString("hostId") ?: ""
                        val createdAt = doc.extractTimestampMs("createdAt")
                        val lastUpdatedAt = doc.extractTimestampMs("lastUpdatedAt").takeIf { it > 0 } ?: createdAt
                        val activeTime = if (lastUpdatedAt > 0) lastUpdatedAt else createdAt

                        // If a session has not had any heartbeat/update in 10 minutes, or is older than 2 hours total, it's expired!
                        if (activeTime > 0 && (now - activeTime) > 10 * 60 * 1000L) {
                            return@mapNotNull null
                        }

                        val rawParticipants = doc.get("participants") as? Map<*, *>
                        val hostData = rawParticipants?.get(hostId) as? Map<*, *>
                        val hostDisplayName = (hostData?.get("name") as? String)
                            ?.takeIf { it.isNotBlank() }
                            ?: "Host"

                        TogetherSessionSummary(
                            sessionId = sessionId,
                            code = code,
                            hostId = hostId,
                            hostDisplayName = hostDisplayName,
                            createdAt = createdAt,
                            lastUpdatedAt = lastUpdatedAt,
                        )
                    } ?: emptyList()

                    // If a host has multiple active session docs, keep ONLY the newest one
                    val newestPerHost = rawSessions
                        .groupBy { it.hostId.ifBlank { it.sessionId } }
                        .mapNotNull { (_, hostSessions) ->
                            hostSessions.maxByOrNull { maxOf(it.lastUpdatedAt, it.createdAt) }
                        }

                    // Automatically mark older superseded sessions of that host as dismissed
                    val supersededIds = rawSessions.map { it.sessionId }.toSet() - newestPerHost.map { it.sessionId }.toSet()
                    if (supersededIds.isNotEmpty()) {
                        localDismissed.update { it + supersededIds }
                    }

                    trySend(newestPerHost)
                }

            awaitClose {
                listener.remove()
            }
        }

        return firestoreFlow.combine(localDismissed) { sessions, dismissed ->
            sessions.filter { it.sessionId !in dismissed }
        }.flowOn(Dispatchers.IO)
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.extractTimestampMs(field: String): Long {
    val raw = get(field) ?: return 0L
    return when (raw) {
        is com.google.firebase.Timestamp -> raw.toDate().time
        is Number -> raw.toLong()
        is java.util.Date -> raw.time
        else -> 0L
    }
}

