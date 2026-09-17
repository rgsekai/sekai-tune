/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.buddy

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.together.ObserveMusicTogetherStateUseCase
import moe.rgsekai.sekaitune.together.TogetherRole
import moe.rgsekai.sekaitune.together.TogetherSessionState
import javax.inject.Inject

enum class BuddyTab {
    BUDDIES,
    REQUESTS,
}

@Immutable
data class BuddyListUiState(
    val currentTab: BuddyTab = BuddyTab.BUDDIES,
    val buddies: List<Buddy> = emptyList(),
    val incomingRequests: List<BuddyRequest> = emptyList(),
    val outgoingRequests: List<BuddyRequest> = emptyList(),
    val pendingRemoveBuddy: Buddy? = null,
    val pendingInviteBuddy: Buddy? = null,
    val isLoading: Boolean = false,
    val activeHostingSessionId: String? = null,
    val activeParticipantIds: Set<String> = emptySet(),
) {
    val isHosting: Boolean get() = activeHostingSessionId != null
}

sealed interface BuddyListEffect {
    data class ShowMessage(val message: String) : BuddyListEffect
}

@HiltViewModel
class BuddyListViewModel @Inject constructor(
    private val buddyRepository: BuddyRepository,
    private val fcmTokenManager: BuddyFcmTokenManager,
    private val observeMusicTogetherState: ObserveMusicTogetherStateUseCase,
) : ViewModel() {

    private val currentTabFlow = MutableStateFlow(BuddyTab.BUDDIES)
    private val pendingRemoveBuddyFlow = MutableStateFlow<Buddy?>(null)
    private val pendingInviteBuddyFlow = MutableStateFlow<Buddy?>(null)
    private val isLoadingFlow = MutableStateFlow(false)

    private val effectsFlow = MutableSharedFlow<BuddyListEffect>(extraBufferCapacity = 8)
    val effects = effectsFlow.asSharedFlow()

    init {
        // Ensure current device token is registered when entering buddy system
        viewModelScope.launch(Dispatchers.IO) {
            fcmTokenManager.registerCurrentToken()
        }
    }

    private data class SessionInfo(
        val sessionId: String?,
        val participantIds: Set<String>,
    )

    private val activeSessionInfoFlow = observeMusicTogetherState().map { snapshot ->
        when (val sessionState = snapshot.sessionState) {
            is TogetherSessionState.HostingOnline -> {
                val participantIds = sessionState.roomState?.participants?.map { it.id }?.toSet() ?: emptySet()
                SessionInfo(sessionState.sessionId, participantIds)
            }
            is TogetherSessionState.Joined -> {
                if (sessionState.role is TogetherRole.Host && (sessionState.code != null || sessionState.roomState.code != null)) {
                    val participantIds = sessionState.roomState.participants.map { it.id }.toSet()
                    SessionInfo(sessionState.sessionId, participantIds)
                } else {
                    SessionInfo(null, emptySet())
                }
            }
            else -> SessionInfo(null, emptySet())
        }
    }

    val state: StateFlow<BuddyListUiState> = combine(
        currentTabFlow,
        buddyRepository.observeBuddies(),
        buddyRepository.observeIncomingRequests(),
        buddyRepository.observeOutgoingRequests(),
        pendingRemoveBuddyFlow,
        pendingInviteBuddyFlow,
        isLoadingFlow,
        activeSessionInfoFlow,
    ) { args: Array<Any?> ->
        val sessionInfo = args[7] as SessionInfo
        @Suppress("UNCHECKED_CAST")
        BuddyListUiState(
            currentTab = args[0] as BuddyTab,
            buddies = args[1] as List<Buddy>,
            incomingRequests = args[2] as List<BuddyRequest>,
            outgoingRequests = args[3] as List<BuddyRequest>,
            pendingRemoveBuddy = args[4] as Buddy?,
            pendingInviteBuddy = args[5] as Buddy?,
            isLoading = args[6] as Boolean,
            activeHostingSessionId = sessionInfo.sessionId,
            activeParticipantIds = sessionInfo.participantIds,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = BuddyListUiState(),
    )

    fun setTab(tab: BuddyTab) {
        currentTabFlow.value = tab
    }

    fun requestRemoveBuddy(buddy: Buddy) {
        pendingRemoveBuddyFlow.value = buddy
    }

    fun dismissRemoveBuddyDialog() {
        pendingRemoveBuddyFlow.value = null
    }

    fun requestInviteBuddy(buddy: Buddy) {
        pendingInviteBuddyFlow.value = buddy
    }

    fun dismissInviteBuddyDialog() {
        pendingInviteBuddyFlow.value = null
    }

    fun confirmInviteBuddy() {
        val buddy = pendingInviteBuddyFlow.value ?: return
        pendingInviteBuddyFlow.value = null
        inviteBuddy(buddy)
    }

    fun confirmRemoveBuddy() {
        val buddy = pendingRemoveBuddyFlow.value ?: return
        pendingRemoveBuddyFlow.value = null

        viewModelScope.launch {
            isLoadingFlow.value = true
            val result = buddyRepository.removeBuddy(buddy.uid)
            isLoadingFlow.value = false

            if (result.isSuccess) {
                effectsFlow.emit(BuddyListEffect.ShowMessage("Removed ${buddy.displayName} from buddies"))
            } else {
                effectsFlow.emit(
                    BuddyListEffect.ShowMessage(
                        result.exceptionOrNull()?.message ?: "Failed to remove buddy"
                    )
                )
            }
        }
    }

    fun acceptRequest(request: BuddyRequest) {
        viewModelScope.launch {
            val result = buddyRepository.acceptRequest(request.id)
            if (result.isFailure) {
                effectsFlow.emit(
                    BuddyListEffect.ShowMessage(
                        result.exceptionOrNull()?.message ?: "Failed to accept request"
                    )
                )
            }
        }
    }

    fun rejectRequest(request: BuddyRequest) {
        viewModelScope.launch {
            val result = buddyRepository.rejectRequest(request.id)
            if (result.isFailure) {
                effectsFlow.emit(
                    BuddyListEffect.ShowMessage(
                        result.exceptionOrNull()?.message ?: "Failed to reject request"
                    )
                )
            }
        }
    }

    fun cancelOutgoingRequest(request: BuddyRequest) {
        viewModelScope.launch {
            val result = buddyRepository.cancelOutgoingRequest(request.id)
            if (result.isFailure) {
                effectsFlow.emit(
                    BuddyListEffect.ShowMessage(
                        result.exceptionOrNull()?.message ?: "Failed to cancel request"
                    )
                )
            }
        }
    }

    fun inviteBuddy(buddy: Buddy) {
        val sessionId = state.value.activeHostingSessionId ?: return
        if (buddy.uid in state.value.activeParticipantIds) {
            viewModelScope.launch {
                effectsFlow.emit(BuddyListEffect.ShowMessage("${buddy.displayName.ifBlank { "Buddy" }} is already in the session"))
            }
            return
        }
        viewModelScope.launch {
            val result = buddyRepository.inviteBuddyToSession(
                sessionId = sessionId,
                buddyUid = buddy.uid,
            )
            result.onSuccess {
                effectsFlow.emit(BuddyListEffect.ShowMessage("Invited ${buddy.displayName.ifBlank { "Buddy" }}"))
            }.onFailure { error ->
                effectsFlow.emit(BuddyListEffect.ShowMessage(error.message ?: "Failed to invite buddy"))
            }
        }
    }
}
