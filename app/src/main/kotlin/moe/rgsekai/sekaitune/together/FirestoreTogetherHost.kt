/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.together

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

class FirestoreTogetherHost(
    private val scope: CoroutineScope,
    val sessionId: String,
    val code: String,
    private val hostUid: String,
    private val hostDisplayName: String,
    private var settings: TogetherRoomSettings,
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val sessionDoc = firestore.collection("together_sessions").document(sessionId)
    private var listenerRegistration: ListenerRegistration? = null

    @Volatile
    private var lastParticipants: List<TogetherParticipant> = listOf(
        TogetherParticipant(
            id = hostUid,
            name = hostDisplayName,
            isHost = true,
            isPending = false,
            isConnected = true
        )
    )

    var onEvent: ((TogetherServerEvent) -> Unit)? = null

    fun currentParticipants(): List<TogetherParticipant> = lastParticipants
    fun currentSettings(): TogetherRoomSettings = settings

    suspend fun createSession(initialState: TogetherRoomState? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val queueData = initialState?.queue?.map { track ->
                    mapOf(
                        "id" to track.id,
                        "title" to track.title,
                        "artists" to track.artists,
                        "durationSec" to track.durationSec,
                        "thumbnailUrl" to track.thumbnailUrl
                    )
                } ?: emptyList<Map<String, Any?>>()

                val initialData = mapOf(
                    "sessionId" to sessionId,
                    "code" to code,
                    "hostId" to hostUid,
                    "createdAt" to now,
                    "lastUpdatedAt" to now,
                    "active" to true,
                    "settings" to mapOf(
                        "allowGuestsToAddTracks" to settings.allowGuestsToAddTracks,
                        "allowGuestsToControlPlayback" to settings.allowGuestsToControlPlayback,
                        "requireHostApprovalToJoin" to settings.requireHostApprovalToJoin,
                    ),
                    "playback" to mapOf(
                        "isPlaying" to (initialState?.isPlaying ?: false),
                        "positionMs" to (initialState?.positionMs ?: 0L),
                        "positionUpdatedAt" to now,
                        "currentIndex" to (initialState?.currentIndex ?: 0),
                        "repeatMode" to (initialState?.repeatMode ?: 0),
                        "shuffleEnabled" to (initialState?.shuffleEnabled ?: false),
                        "queueHash" to (initialState?.queueHash ?: ""),
                    ),
                    "queue" to queueData,
                    "participants" to mapOf(
                        hostUid to mapOf(
                            "id" to hostUid,
                            "name" to hostDisplayName,
                            "isHost" to true,
                            "isPending" to false,
                            "isConnected" to true,
                            "joinedAt" to now,
                        )
                    )
                )
                sessionDoc.set(initialData).await()
                startListening()
                true
            } catch (t: Throwable) {
                Timber.tag("Together").e(t, "Failed to create Firestore together session")
                false
            }
        }
    }

    private fun startListening() {
        listenerRegistration = sessionDoc.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Timber.tag("Together").e(error, "Firestore host listener error")
                onEvent?.invoke(TogetherServerEvent.Error(error.message ?: "Firestore listener error", error))
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists() || snapshot.getBoolean("active") == false) {
                return@addSnapshotListener
            }

            // Parse participants
            val rawParticipants = snapshot.get("participants") as? Map<String, Map<String, Any?>> ?: emptyMap()
            val parsedParticipants = rawParticipants.values.mapNotNull { p ->
                val id = p["id"] as? String ?: return@mapNotNull null
                val name = p["name"] as? String ?: "Guest"
                val isHost = p["isHost"] as? Boolean ?: false
                val isPending = p["isPending"] as? Boolean ?: false
                val isConnected = p["isConnected"] as? Boolean ?: true
                TogetherParticipant(
                    id = id,
                    name = name,
                    isHost = isHost,
                    isPending = isPending,
                    isConnected = isConnected
                )
            }

            // Detect participant changes
            val prevMap = lastParticipants.associateBy { it.id }
            val currentMap = parsedParticipants.associateBy { it.id }

            // Newly joined or updated
            for ((id, participant) in currentMap) {
                val prev = prevMap[id]
                if (prev == null) {
                    onEvent?.invoke(TogetherServerEvent.ParticipantJoined(participant))
                }
            }

            // Left
            for ((id, _) in prevMap) {
                if (!currentMap.containsKey(id) && id != hostUid) {
                    onEvent?.invoke(TogetherServerEvent.ParticipantLeft(id, "Left"))
                }
            }

            lastParticipants = parsedParticipants

            // Check if room state was updated by authorized guest (e.g. guest added track or controlled playback)
            val rawSettings = snapshot.get("settings") as? Map<String, Any?>
            val allowGuestsAdd = rawSettings?.get("allowGuestsToAddTracks") as? Boolean ?: settings.allowGuestsToAddTracks
            val allowGuestsControl = rawSettings?.get("allowGuestsToControlPlayback") as? Boolean ?: settings.allowGuestsToControlPlayback

            val rawQueue = snapshot.get("queue") as? List<Map<String, Any?>> ?: emptyList()
            val parsedQueue = rawQueue.mapNotNull { t ->
                val id = t["id"] as? String ?: return@mapNotNull null
                val title = t["title"] as? String ?: ""
                val artists = (t["artists"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val durationSec = (t["durationSec"] as? Number)?.toInt() ?: -1
                val thumbnailUrl = t["thumbnailUrl"] as? String
                TogetherTrack(
                    id = id,
                    title = title,
                    artists = artists,
                    durationSec = durationSec,
                    thumbnailUrl = thumbnailUrl
                )
            }

            val rawPlayback = snapshot.get("playback") as? Map<String, Any?>
            if (rawPlayback != null) {
                val isPlaying = rawPlayback["isPlaying"] as? Boolean ?: false
                val positionMs = (rawPlayback["positionMs"] as? Number)?.toLong() ?: 0L
                val currentIndex = (rawPlayback["currentIndex"] as? Number)?.toInt() ?: 0
                val repeatMode = (rawPlayback["repeatMode"] as? Number)?.toInt() ?: 0
                val shuffleEnabled = rawPlayback["shuffleEnabled"] as? Boolean ?: false
                val queueHash = rawPlayback["queueHash"] as? String ?: ""

                val roomState = TogetherRoomState(
                    sessionId = sessionId,
                    hostId = hostUid,
                    participants = parsedParticipants,
                    settings = settings,
                    queue = parsedQueue,
                    queueHash = queueHash,
                    currentIndex = currentIndex,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    repeatMode = repeatMode,
                    shuffleEnabled = shuffleEnabled,
                    sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime()
                )

                onEvent?.invoke(TogetherServerEvent.RoomStateReceived(roomState))
            }
        }
    }

    suspend fun broadcastRoomState(state: TogetherRoomState) {
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val queueData = state.queue.map { track ->
                    mapOf(
                        "id" to track.id,
                        "title" to track.title,
                        "artists" to track.artists,
                        "durationSec" to track.durationSec,
                        "thumbnailUrl" to track.thumbnailUrl
                    )
                }

                sessionDoc.update(
                    mapOf(
                        "playback.isPlaying" to state.isPlaying,
                        "playback.positionMs" to state.positionMs,
                        "playback.positionUpdatedAt" to now,
                        "playback.currentIndex" to state.currentIndex,
                        "playback.repeatMode" to state.repeatMode,
                        "playback.shuffleEnabled" to state.shuffleEnabled,
                        "playback.queueHash" to state.queueHash,
                        "queue" to queueData,
                        "lastUpdatedAt" to now,
                    )
                ).await()
            } catch (t: Throwable) {
                Timber.tag("Together").e(t, "Failed to broadcast room state in Firestore")
            }
        }
    }

    suspend fun updateSettings(newSettings: TogetherRoomSettings) {
        settings = newSettings
        withContext(Dispatchers.IO) {
            try {
                sessionDoc.update(
                    mapOf(
                        "settings.allowGuestsToAddTracks" to newSettings.allowGuestsToAddTracks,
                        "settings.allowGuestsToControlPlayback" to newSettings.allowGuestsToControlPlayback,
                        "settings.requireHostApprovalToJoin" to newSettings.requireHostApprovalToJoin,
                        "lastUpdatedAt" to System.currentTimeMillis()
                    )
                ).await()
            } catch (t: Throwable) {
                Timber.tag("Together").e(t, "Failed to update room settings in Firestore")
            }
        }
    }

    suspend fun approveParticipant(participantId: String, approved: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                if (approved) {
                    sessionDoc.update("participants.$participantId.isPending", false).await()
                } else {
                    sessionDoc.update("participants.$participantId", FieldValue.delete()).await()
                }
            } catch (t: Throwable) {
                Timber.tag("Together").e(t, "Failed to approve/reject participant")
            }
        }
    }

    suspend fun kickParticipant(participantId: String, reason: String? = null) {
        withContext(Dispatchers.IO) {
            try {
                sessionDoc.update("participants.$participantId", FieldValue.delete()).await()
            } catch (t: Throwable) {
                Timber.tag("Together").e(t, "Failed to kick participant")
            }
        }
    }

    suspend fun banParticipant(participantId: String, reason: String? = null) {
        kickParticipant(participantId, reason)
    }

    suspend fun transferHostOwnership(newHostUid: String) {
        withContext(Dispatchers.IO) {
            try {
                sessionDoc.update(
                    mapOf(
                        "hostId" to newHostUid,
                        "participants.$hostUid.isHost" to false,
                        "participants.$newHostUid.isHost" to true,
                        "lastUpdatedAt" to System.currentTimeMillis()
                    )
                ).await()
            } catch (t: Throwable) {
                Timber.tag("Together").e(t, "Failed to transfer host ownership")
            }
        }
    }

    suspend fun close() {
        listenerRegistration?.remove()
        listenerRegistration = null
        withContext(Dispatchers.IO) {
            try {
                sessionDoc.update(
                    mapOf(
                        "active" to false,
                        "lastUpdatedAt" to System.currentTimeMillis()
                    )
                ).await()
            } catch (t: Throwable) {
                // Ignore if already deleted
            }
        }
    }
}
