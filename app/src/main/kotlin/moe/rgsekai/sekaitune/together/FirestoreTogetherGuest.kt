/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.together

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

class FirestoreTogetherGuest(
    private val scope: CoroutineScope,
    private val guestUid: String,
    private val displayName: String,
) {
    private val firestore = FirebaseFirestore.getInstance()
    private var sessionDocRef: DocumentReference? = null
    private var listenerRegistration: ListenerRegistration? = null
    private var currentSessionId: String? = null
    private var currentCode: String? = null

    private val _events = MutableSharedFlow<TogetherClientEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<TogetherClientEvent> = _events.asSharedFlow()

    suspend fun joinByCode(code: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val query = firestore.collection("together_sessions")
                    .whereEqualTo("code", code.trim())
                    .whereEqualTo("active", true)
                    .limit(1)
                    .get()
                    .await()

                if (query.isEmpty) {
                    _events.emit(TogetherClientEvent.Error("Session not found with code: $code"))
                    return@withContext null
                }

                val doc = query.documents[0]
                val sessionId = doc.id
                currentSessionId = sessionId
                currentCode = doc.getString("code") ?: code.trim()
                val docRef = doc.reference
                sessionDocRef = docRef

                val rawSettings = doc.get("settings") as? Map<String, Any?>
                val requireApproval = rawSettings?.get("requireHostApprovalToJoin") as? Boolean ?: false
                val allowGuestsAdd = rawSettings?.get("allowGuestsToAddTracks") as? Boolean ?: true
                val allowGuestsControl = rawSettings?.get("allowGuestsToControlPlayback") as? Boolean ?: false

                val settings = TogetherRoomSettings(
                    allowGuestsToAddTracks = allowGuestsAdd,
                    allowGuestsToControlPlayback = allowGuestsControl,
                    requireHostApprovalToJoin = requireApproval
                )

                // Add self to participants
                val selfParticipantData = mapOf(
                    "id" to guestUid,
                    "name" to displayName,
                    "isHost" to false,
                    "isPending" to requireApproval,
                    "isConnected" to true,
                    "joinedAt" to System.currentTimeMillis()
                )

                docRef.update("participants.$guestUid", selfParticipantData).await()

                _events.emit(
                    TogetherClientEvent.Welcome(
                        welcome = ServerWelcome(
                            protocolVersion = TogetherProtocolVersion,
                            sessionId = sessionId,
                            participantId = guestUid,
                            role = ServerRole.GUEST,
                            isPending = requireApproval,
                            settings = settings
                        )
                    )
                )

                startListening(docRef)
                sessionId
            } catch (t: Throwable) {
                Timber.tag("Together").e(t, "Failed to join Firestore together session")
                _events.emit(TogetherClientEvent.Error(t.message ?: "Failed to join session", t))
                null
            }
        }
    }

    private fun startListening(docRef: DocumentReference) {
        listenerRegistration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Timber.tag("Together").e(error, "Firestore guest listener error")
                _events.tryEmit(TogetherClientEvent.Error(error.message ?: "Firestore error", error))
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists() || snapshot.getBoolean("active") == false) {
                _events.tryEmit(TogetherClientEvent.Disconnected)
                return@addSnapshotListener
            }

            val rawParticipants = snapshot.get("participants") as? Map<String, Map<String, Any?>> ?: emptyMap()
            val selfData = rawParticipants[guestUid]
            if (selfData == null) {
                // Guest was kicked/banned/removed
                _events.tryEmit(TogetherClientEvent.Disconnected)
                return@addSnapshotListener
            }

            val rawSettings = snapshot.get("settings") as? Map<String, Any?>
            val settings = TogetherRoomSettings(
                allowGuestsToAddTracks = rawSettings?.get("allowGuestsToAddTracks") as? Boolean ?: true,
                allowGuestsToControlPlayback = rawSettings?.get("allowGuestsToControlPlayback") as? Boolean ?: false,
                requireHostApprovalToJoin = rawSettings?.get("requireHostApprovalToJoin") as? Boolean ?: false
            )

            val parsedParticipants = rawParticipants.values.mapNotNull { p ->
                val id = p["id"] as? String ?: return@mapNotNull null
                val name = p["name"] as? String ?: "Guest"
                val isHost = p["isHost"] as? Boolean ?: false
                val isPending = p["isPending"] as? Boolean ?: false
                val isConnected = p["isConnected"] as? Boolean ?: true
                TogetherParticipant(id = id, name = name, isHost = isHost, isPending = isPending, isConnected = isConnected)
            }

            val rawQueue = snapshot.get("queue") as? List<Map<String, Any?>> ?: emptyList()
            val parsedQueue = rawQueue.mapNotNull { t ->
                val id = t["id"] as? String ?: return@mapNotNull null
                val title = t["title"] as? String ?: ""
                val artists = (t["artists"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val durationSec = (t["durationSec"] as? Number)?.toInt() ?: -1
                val thumbnailUrl = t["thumbnailUrl"] as? String
                TogetherTrack(id = id, title = title, artists = artists, durationSec = durationSec, thumbnailUrl = thumbnailUrl)
            }

            val rawPlayback = snapshot.get("playback") as? Map<String, Any?>
            val isPlaying = rawPlayback?.get("isPlaying") as? Boolean ?: false
            val positionMs = (rawPlayback?.get("positionMs") as? Number)?.toLong() ?: 0L
            val currentIndex = (rawPlayback?.get("currentIndex") as? Number)?.toInt() ?: 0
            val repeatMode = (rawPlayback?.get("repeatMode") as? Number)?.toInt() ?: 0
            val shuffleEnabled = rawPlayback?.get("shuffleEnabled") as? Boolean ?: false
            val queueHash = rawPlayback?.get("queueHash") as? String ?: ""
            val hostId = snapshot.getString("hostId") ?: ""
            val sessionId = snapshot.getString("sessionId") ?: currentSessionId.orEmpty()
            val sessionCode = snapshot.getString("code") ?: currentCode

            val roomState = TogetherRoomState(
                sessionId = sessionId,
                hostId = hostId,
                participants = parsedParticipants,
                settings = settings,
                queue = parsedQueue,
                queueHash = queueHash,
                currentIndex = currentIndex,
                isPlaying = isPlaying,
                positionMs = positionMs,
                repeatMode = repeatMode,
                shuffleEnabled = shuffleEnabled,
                sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                code = sessionCode,
            )

            _events.tryEmit(TogetherClientEvent.RoomState(roomState))
        }
    }

    suspend fun requestControl(action: ControlAction) {
        val docRef = sessionDocRef ?: return
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                when (action) {
                    ControlAction.Play -> {
                        docRef.update(
                            mapOf(
                                "playback.isPlaying" to true,
                                "playback.positionUpdatedAt" to now,
                                "lastUpdatedAt" to now
                            )
                        ).await()
                    }
                    ControlAction.Pause -> {
                        docRef.update(
                            mapOf(
                                "playback.isPlaying" to false,
                                "playback.positionUpdatedAt" to now,
                                "lastUpdatedAt" to now
                            )
                        ).await()
                    }
                    is ControlAction.SeekTo -> {
                        docRef.update(
                            mapOf(
                                "playback.positionMs" to action.positionMs,
                                "playback.positionUpdatedAt" to now,
                                "lastUpdatedAt" to now
                            )
                        ).await()
                    }
                    is ControlAction.SeekToIndex -> {
                        docRef.update(
                            mapOf(
                                "playback.currentIndex" to action.index,
                                "playback.positionMs" to 0L,
                                "playback.positionUpdatedAt" to now,
                                "lastUpdatedAt" to now
                            )
                        ).await()
                    }
                    is ControlAction.SeekToTrack -> {
                        // Will be handled via currentIndex
                    }
                    ControlAction.SkipNext -> {
                        // Handled via seekToIndex
                    }
                    ControlAction.SkipPrevious -> {
                        // Handled via seekToIndex
                    }
                    is ControlAction.SetRepeatMode -> {
                        docRef.update(
                            mapOf(
                                "playback.repeatMode" to action.repeatMode,
                                "lastUpdatedAt" to now,
                            ),
                        ).await()
                    }
                    is ControlAction.SetShuffleEnabled -> {
                        docRef.update(
                            mapOf(
                                "playback.shuffleEnabled" to action.shuffleEnabled,
                                "lastUpdatedAt" to now,
                            ),
                        ).await()
                    }
                }
            } catch (t: Throwable) {
                Timber.tag("Together").e(t, "Failed to request guest control in Firestore")
            }
        }
    }

    suspend fun requestAddTrack(track: TogetherTrack, mode: AddTrackMode) {
        val docRef = sessionDocRef ?: return
        withContext(Dispatchers.IO) {
            try {
                val trackMap = mapOf(
                    "id" to track.id,
                    "title" to track.title,
                    "artists" to track.artists,
                    "durationSec" to track.durationSec,
                    "thumbnailUrl" to track.thumbnailUrl
                )
                docRef.update(
                    "queue", FieldValue.arrayUnion(trackMap),
                    "lastUpdatedAt", System.currentTimeMillis()
                ).await()
            } catch (t: Throwable) {
                Timber.tag("Together").e(t, "Failed to add track to Firestore queue")
            }
        }
    }

    suspend fun leave() {
        listenerRegistration?.remove()
        listenerRegistration = null
        val docRef = sessionDocRef ?: return
        withContext(Dispatchers.IO) {
            try {
                docRef.update("participants.$guestUid", FieldValue.delete()).await()
            } catch (t: Throwable) {
                // Ignored
            }
        }
    }
}
