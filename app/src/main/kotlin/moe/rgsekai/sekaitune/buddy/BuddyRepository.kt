/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.buddy

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuddyRepository @Inject constructor() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val functions = FirebaseFunctions.getInstance()

    val currentUid: String?
        get() = auth.currentUser?.uid

    suspend fun sendBuddyRequest(
        toUid: String,
        fromDisplayName: String,
        toDisplayName: String = "",
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
            val docRef = firestore.collection("buddy_requests").document()
            val requestData = mapOf(
                "fromUid" to myUid,
                "toUid" to toUid,
                "fromDisplayName" to fromDisplayName,
                "toDisplayName" to toDisplayName,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis()
            )

            docRef.set(requestData).await()
            Timber.tag("BuddyRepository").d("Successfully sent buddy request to $toUid with doc ID ${docRef.id}")
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
                    val addedAt = doc.getTimestamp("addedAt")?.toDate()?.time
                        ?: doc.getLong("addedAt")
                        ?: 0L
                    Buddy(
                        uid = uid,
                        displayName = displayName,
                        addedAt = addedAt
                    )
                } ?: emptyList()

                trySend(buddies)
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
                    val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time
                        ?: doc.getLong("createdAt")
                        ?: 0L

                    BuddyRequest(
                        id = doc.id,
                        fromUid = fromUid,
                        toUid = toUid,
                        fromDisplayName = fromDisplayName,
                        toDisplayName = toDisplayName,
                        status = status,
                        createdAt = createdAt
                    )
                } ?: emptyList()

                trySend(requests)
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
                    val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time
                        ?: doc.getLong("createdAt")
                        ?: 0L

                    BuddyRequest(
                        id = doc.id,
                        fromUid = fromUid,
                        toUid = toUid,
                        fromDisplayName = fromDisplayName,
                        toDisplayName = toDisplayName,
                        status = status,
                        createdAt = createdAt
                    )
                } ?: emptyList()

                trySend(requests)
            }

        awaitClose {
            listener.remove()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun acceptRequest(requestId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf(
                "requestId" to requestId
            )

            functions.getHttpsCallable("acceptBuddyRequest")
                .call(payload)
                .await()

            Timber.tag("BuddyRepository").d("Successfully accepted buddy request $requestId")
            Result.success(Unit)
        } catch (t: Throwable) {
            Timber.tag("BuddyRepository").e(t, "Failed to accept buddy request $requestId")
            Result.failure(t)
        }
    }

    suspend fun rejectRequest(requestId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf(
                "requestId" to requestId
            )

            functions.getHttpsCallable("rejectBuddyRequest")
                .call(payload)
                .await()

            Timber.tag("BuddyRepository").d("Successfully rejected buddy request $requestId")
            Result.success(Unit)
        } catch (t: Throwable) {
            Timber.tag("BuddyRepository").e(t, "Failed to reject buddy request $requestId")
            Result.failure(t)
        }
    }

    suspend fun removeBuddy(buddyUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf(
                "buddyUid" to buddyUid
            )

            functions.getHttpsCallable("removeBuddy")
                .call(payload)
                .await()

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
            firestore.collection("together_sessions")
                .document(sessionId)
                .update("invitedUids", FieldValue.arrayUnion(buddyUid))
                .await()

            Timber.tag("BuddyRepository").d("Successfully invited buddy $buddyUid to session $sessionId")
            Result.success(Unit)
        } catch (t: Throwable) {
            Timber.tag("BuddyRepository").e(t, "Failed to invite buddy $buddyUid to session $sessionId")
            Result.failure(t)
        }
    }

    fun observeInvitedSessions(): Flow<List<TogetherSessionSummary>> = callbackFlow {
        val myUid = auth.currentUser?.uid
        if (myUid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener: ListenerRegistration = firestore.collection("together_sessions")
            .whereArrayContains("invitedUids", myUid)
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.tag("BuddyRepository").e(error, "Error listening to invited sessions for $myUid")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val sessions = snapshot?.documents?.mapNotNull { doc ->
                    val sessionId = doc.getString("sessionId") ?: doc.id
                    val code = doc.getString("code") ?: return@mapNotNull null
                    val hostId = doc.getString("hostId") ?: ""
                    val createdAt = doc.getLong("createdAt") ?: 0L

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
                    )
                } ?: emptyList()

                trySend(sessions)
            }

        awaitClose {
            listener.remove()
        }
    }.flowOn(Dispatchers.IO)
}
