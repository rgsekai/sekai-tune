/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.viewmodels

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.playback.MusicService
import moe.rgsekai.sekaitune.together.AttachMusicTogetherServiceUseCase
import moe.rgsekai.sekaitune.together.MusicTogetherConnectionMode
import moe.rgsekai.sekaitune.together.MusicTogetherPreferences
import moe.rgsekai.sekaitune.together.MusicTogetherSessionActionsUseCase
import moe.rgsekai.sekaitune.together.MusicTogetherSnapshot
import moe.rgsekai.sekaitune.together.ObserveMusicTogetherStateUseCase
import moe.rgsekai.sekaitune.together.TogetherLink
import moe.rgsekai.sekaitune.together.TogetherRole
import moe.rgsekai.sekaitune.together.TogetherRoomSettings
import moe.rgsekai.sekaitune.together.TogetherRoomState
import moe.rgsekai.sekaitune.together.TogetherSessionState
import moe.rgsekai.sekaitune.together.UpdateMusicTogetherPreferencesUseCase
import moe.rgsekai.sekaitune.together.isConnectedToSession
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

sealed interface MusicTogetherScreenState {
    data object Loading : MusicTogetherScreenState

    data class Success(
        val model: MusicTogetherUiModel,
    ) : MusicTogetherScreenState

    data object Empty : MusicTogetherScreenState

    data class Error(
        @StringRes val messageResId: Int,
    ) : MusicTogetherScreenState
}

@Immutable
data class MusicTogetherUiModel(
    val showWelcomeDialog: Boolean,
    val welcomeDontShowAgain: Boolean,
    val dialog: MusicTogetherDialogUiState,
    val status: MusicTogetherStatusUiModel,
    val sessionShare: MusicTogetherSessionShareUiModel?,
    val playback: MusicTogetherPlaybackUiModel,
    val host: MusicTogetherHostUiModel,
    val join: MusicTogetherJoinUiModel,
    val participants: MusicTogetherParticipantUiModels,
    val activityLog: MusicTogetherActivityLogUiModels,
    val invitedSessions: List<moe.rgsekai.sekaitune.buddy.TogetherSessionSummary> = emptyList(),
)

@Immutable
sealed interface MusicTogetherDialogUiState {
    data object None : MusicTogetherDialogUiState

    data class DisplayName(
        val initialValue: String,
    ) : MusicTogetherDialogUiState

    data class Port(
        val initialValue: String,
    ) : MusicTogetherDialogUiState

    data class Join(
        val initialValue: String,
        @StringRes val placeholderResId: Int,
        val onlineMode: Boolean,
    ) : MusicTogetherDialogUiState

    data class KickParticipant(
        val participantId: String,
        val participantName: String,
    ) : MusicTogetherDialogUiState

    data class BanParticipant(
        val participantId: String,
        val participantName: String,
    ) : MusicTogetherDialogUiState

    data class TransferHost(
        val participantId: String,
        val participantName: String,
    ) : MusicTogetherDialogUiState

    data class SendBuddyRequest(
        val participantId: String,
        val participantName: String,
        val fromDisplayName: String,
    ) : MusicTogetherDialogUiState
}

@Immutable
data class MusicTogetherStatusUiModel(
    @StringRes val titleResId: Int,
    @StringRes val stateLabelResId: Int,
    val errorMessage: String?,
    @DrawableRes val iconResId: Int,
    val active: Boolean,
    val error: Boolean,
    val waitingApproval: Boolean,
    val canLeave: Boolean,
    val showSignInAction: Boolean = false,
)

@Immutable
data class MusicTogetherSessionShareUiModel(
    @StringRes val labelResId: Int,
    val value: String,
    val maxLines: Int,
)

@Immutable
data class MusicTogetherPlaybackUiModel(
    val title: String?,
    val artists: String?,
    @StringRes val playbackStateResId: Int,
    val queueSize: Int,
    val currentIndexLabel: String?,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
)

@Immutable
data class MusicTogetherHostUiModel(
    val onlineMode: Boolean,
    val displayName: String,
    val port: Int,
    val allowGuestsToAddTracks: Boolean,
    val allowGuestsToControlPlayback: Boolean,
    val requireHostApprovalToJoin: Boolean,
    val visible: Boolean,
    val startEnabled: Boolean,
    val loading: Boolean,
    val photoUrl: String? = null,
)

@Immutable
data class MusicTogetherJoinUiModel(
    val onlineMode: Boolean,
    val input: String,
    @StringRes val hintResId: Int,
    val canJoin: Boolean,
    val disabled: Boolean,
    val joined: Boolean,
    val waitingApproval: Boolean,
    val joining: Boolean,
)

@Immutable
data class MusicTogetherParticipantUiModels(
    private val values: List<MusicTogetherParticipantUiModel>,
) {
    val size: Int get() = values.size
    val isEmpty: Boolean get() = values.isEmpty()

    operator fun get(index: Int): MusicTogetherParticipantUiModel = values[index]
    fun find(id: String): MusicTogetherParticipantUiModel? = values.firstOrNull { it.id == id }
}

@Immutable
data class MusicTogetherParticipantUiModel(
    val id: String,
    val name: String,
    val host: Boolean,
    val pending: Boolean,
    val connected: Boolean,
    val showApproveActions: Boolean,
    val showModerationActions: Boolean,
    val showTransferHostAction: Boolean,
    val showAddBuddyAction: Boolean = false,
    val photoUrl: String? = null,
)

@Immutable
data class MusicTogetherActivityLogUiModels(
    private val values: List<MusicTogetherActivityLogItemUiModel>,
) {
    val size: Int get() = values.size
    val isEmpty: Boolean get() = values.isEmpty()

    operator fun get(index: Int): MusicTogetherActivityLogItemUiModel = values[index]
}

@Immutable
data class MusicTogetherActivityLogItemUiModel(
    val id: Long,
    @DrawableRes val iconResId: Int,
    @StringRes val messageResId: Int,
    val args: List<String>,
    val timestamp: String,
)

sealed interface MusicTogetherEffect {
    data class CopyText(
        @StringRes val labelResId: Int,
        val value: String,
    ) : MusicTogetherEffect

    data class ShareText(
        val value: String,
    ) : MusicTogetherEffect

    data class ToastMessage(
        @StringRes val messageResId: Int,
    ) : MusicTogetherEffect
}

@HiltViewModel
class MusicTogetherViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val observeMusicTogetherState: ObserveMusicTogetherStateUseCase,
        private val attachMusicTogetherService: AttachMusicTogetherServiceUseCase,
        private val updatePreferences: UpdateMusicTogetherPreferencesUseCase,
        private val sessionActions: MusicTogetherSessionActionsUseCase,
        private val buddyRepository: moe.rgsekai.sekaitune.buddy.BuddyRepository,
        private val userProfileManager: moe.rgsekai.sekaitune.auth.UserProfileManager,
    ) : ViewModel() {
        private val hostModeOnline = MutableStateFlow(false)
        private val joinModeOnline = MutableStateFlow(false)
        private val dialog = MutableStateFlow<MusicTogetherDialogUiState>(MusicTogetherDialogUiState.None)
        private val welcomeDismissedThisSession = MutableStateFlow(false)
        private val welcomeDontShowAgain = MutableStateFlow(true)
        private val activityLog = MutableStateFlow(MusicTogetherActivityLogUiModels(emptyList()))
        private val effectsFlow = MutableSharedFlow<MusicTogetherEffect>(extraBufferCapacity = 8)
        val effects = effectsFlow.asSharedFlow()

        private val logItems = ArrayDeque<MusicTogetherActivityLogItemUiModel>()
        private var nextLogId = 0L
        private var lastSessionId: String? = null
        private var lastRoomState: TogetherRoomState? = null
        private var lastStateWasError = false

        init {
            com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null && !user.isAnonymous) {
                    sessionActions.leaveSession()
                }
            }
        }

        private data class StateInputs(
            val snapshot: MusicTogetherSnapshot,
            val hostOnline: Boolean,
            val joinOnline: Boolean,
            val dialogState: MusicTogetherDialogUiState,
            val welcomeDismissed: Boolean,
            val buddies: List<moe.rgsekai.sekaitune.buddy.Buddy>,
            val outgoingRequests: List<moe.rgsekai.sekaitune.buddy.BuddyRequest>,
            val invitedSessions: List<moe.rgsekai.sekaitune.buddy.TogetherSessionSummary>,
        )

        private val snapshots =
            observeMusicTogetherState()
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    emit(
                        MusicTogetherSnapshot(
                            preferences =
                                MusicTogetherPreferences(
                                    displayName = "",
                                    port = 42117,
                                    allowGuestsToAddTracks = true,
                                    allowGuestsToControlPlayback = false,
                                    requireHostApprovalToJoin = false,
                                    lastJoinLink = "",
                                    welcomeShown = false,
                                ),
                            sessionState = TogetherSessionState.Error(message = throwable.message.orEmpty()),
                        ),
                    )
                }

        val state: StateFlow<MusicTogetherScreenState> =
            combine(
                snapshots,
                hostModeOnline,
                joinModeOnline,
                dialog,
                welcomeDismissedThisSession,
                buddyRepository.observeBuddies(),
                buddyRepository.observeOutgoingRequests(),
                buddyRepository.observeInvitedSessions(),
            ) { args: Array<Any?> ->
                @Suppress("UNCHECKED_CAST")
                StateInputs(
                    snapshot = args[0] as MusicTogetherSnapshot,
                    hostOnline = args[1] as Boolean,
                    joinOnline = args[2] as Boolean,
                    dialogState = args[3] as MusicTogetherDialogUiState,
                    welcomeDismissed = args[4] as Boolean,
                    buddies = args[5] as List<moe.rgsekai.sekaitune.buddy.Buddy>,
                    outgoingRequests = args[6] as List<moe.rgsekai.sekaitune.buddy.BuddyRequest>,
                    invitedSessions = args[7] as List<moe.rgsekai.sekaitune.buddy.TogetherSessionSummary>,
                )
            }.let { inputs ->
                combine(inputs, welcomeDontShowAgain, activityLog) { stateInputs, dontShowAgain, log ->
                    val screenState: MusicTogetherScreenState =
                        MusicTogetherScreenState.Success(
                            stateInputs.snapshot.toUiModel(
                                hostOnline = stateInputs.hostOnline,
                                joinOnline = stateInputs.joinOnline,
                                dialogState = stateInputs.dialogState,
                                welcomeDismissed = stateInputs.welcomeDismissed,
                                dontShowAgain = dontShowAgain,
                                log = log,
                                buddies = stateInputs.buddies,
                                outgoingRequests = stateInputs.outgoingRequests,
                                invitedSessions = stateInputs.invitedSessions,
                            ),
                        )
                    screenState
                }
            }.catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(MusicTogetherScreenState.Error(R.string.error_unknown))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = MusicTogetherScreenState.Loading,
            )

        init {
            viewModelScope.launch {
                snapshots
                    .map { it.sessionState }
                    .distinctUntilChanged()
                    .collect { sessionState ->
                        updateActivityLog(sessionState)
                    }
            }
        }

        fun attachService(service: MusicService?) {
            attachMusicTogetherService(service)
        }

        fun setWelcomeDontShowAgain(value: Boolean) {
            welcomeDontShowAgain.value = value
        }

        fun dismissWelcomeDialog() {
            welcomeDismissedThisSession.value = true
        }

        fun confirmWelcomeDialog() {
            val dontShowAgain = welcomeDontShowAgain.value
            welcomeDismissedThisSession.value = true
            if (dontShowAgain) {
                viewModelScope.launch(Dispatchers.IO) {
                    updatePreferences.setWelcomeShown(true)
                }
            }
        }

        fun openDisplayNameDialog() {
            val model = successModel() ?: return
            dialog.value = MusicTogetherDialogUiState.DisplayName(model.host.displayName)
        }

        fun submitDisplayName(value: String) {
            val trimmed = value.trim()
            if (trimmed.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                updatePreferences.setDisplayName(trimmed)
                userProfileManager.updateDisplayName(trimmed)
            }
        }

        fun openPortDialog() {
            val model = successModel() ?: return
            dialog.value = MusicTogetherDialogUiState.Port(model.host.port.toString())
        }

        fun submitPort(value: String) {
            val port = value.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return
            viewModelScope.launch(Dispatchers.IO) {
                updatePreferences.setPort(port)
            }
        }

        fun openJoinDialog() {
            val model = successModel() ?: return
            if (model.join.disabled || model.join.joining || model.join.joined || model.join.waitingApproval) return
            dialog.value =
                MusicTogetherDialogUiState.Join(
                    initialValue = model.join.input,
                    placeholderResId = model.join.hintResId,
                    onlineMode = model.join.onlineMode,
                )
        }

        fun submitJoinInput(value: String) {
            val model = successModel() ?: return
            val trimmed = value.trim()
            if (trimmed.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                updatePreferences.setLastJoinLink(trimmed)
            }
            sessionActions.joinSession(
                mode = if (model.join.onlineMode) MusicTogetherConnectionMode.ONLINE else MusicTogetherConnectionMode.LAN,
                rawInput = trimmed,
                displayName = model.host.displayName,
            )
        }

        fun dismissDialog() {
            dialog.value = MusicTogetherDialogUiState.None
        }

        fun clearError() {
            sessionActions.leaveSession()
        }

        fun setHostModeOnline(value: Boolean) {
            hostModeOnline.value = value
        }

        fun setJoinModeOnline(value: Boolean) {
            val model = successModel()
            if (model?.join?.disabled == true) return
            joinModeOnline.value = value
        }

        fun setAllowGuestsToAddTracks(value: Boolean) {
            viewModelScope.launch(Dispatchers.IO) {
                updatePreferences.setAllowGuestsToAddTracks(value)
            }
            pushSettingsToActiveSession(addTracks = value)
        }

        fun setAllowGuestsToControlPlayback(value: Boolean) {
            viewModelScope.launch(Dispatchers.IO) {
                updatePreferences.setAllowGuestsToControlPlayback(value)
            }
            pushSettingsToActiveSession(controlPlayback = value)
        }

        fun setRequireHostApprovalToJoin(value: Boolean) {
            viewModelScope.launch(Dispatchers.IO) {
                updatePreferences.setRequireHostApprovalToJoin(value)
            }
            pushSettingsToActiveSession(approval = value)
        }

        fun startSession() {
            val model = successModel() ?: return
            if (!model.host.startEnabled) return
            sessionActions.startSession(
                mode = if (model.host.onlineMode) MusicTogetherConnectionMode.ONLINE else MusicTogetherConnectionMode.LAN,
                displayName = model.host.displayName,
                port = model.host.port,
                settings =
                    TogetherRoomSettings(
                        allowGuestsToAddTracks = model.host.allowGuestsToAddTracks,
                        allowGuestsToControlPlayback = model.host.allowGuestsToControlPlayback,
                        requireHostApprovalToJoin = model.host.requireHostApprovalToJoin,
                    ),
            )
        }

        fun joinSession() {
            val model = successModel() ?: return
            if (!model.join.canJoin) return
            submitJoinInput(model.join.input)
        }

        fun joinInvitedSession(session: moe.rgsekai.sekaitune.buddy.TogetherSessionSummary) {
            val model = successModel() ?: return
            viewModelScope.launch(Dispatchers.IO) {
                updatePreferences.setLastJoinLink(session.code)
                buddyRepository.dismissSessionInvite(session.sessionId)
            }
            sessionActions.joinSession(
                mode = MusicTogetherConnectionMode.ONLINE,
                rawInput = session.code,
                displayName = model.host.displayName,
            )
        }

        fun dismissInvitedSession(session: moe.rgsekai.sekaitune.buddy.TogetherSessionSummary) {
            viewModelScope.launch(Dispatchers.IO) {
                buddyRepository.dismissSessionInvite(session.sessionId)
            }
        }

        fun leaveSession() {
            sessionActions.leaveSession()
        }

        fun copySessionValue(
            @StringRes labelResId: Int,
            value: String,
        ) {
            effectsFlow.tryEmit(MusicTogetherEffect.CopyText(labelResId = labelResId, value = value))
            effectsFlow.tryEmit(MusicTogetherEffect.ToastMessage(R.string.copied))
        }

        fun shareSessionValue(value: String) {
            effectsFlow.tryEmit(MusicTogetherEffect.ShareText(value))
        }

        fun approveParticipant(participantId: String) {
            sessionActions.approveParticipant(participantId, approved = true)
        }

        fun rejectParticipant(participantId: String) {
            sessionActions.approveParticipant(participantId, approved = false)
        }

        fun requestTransferHost(participantId: String) {
            val participant = successModel()?.participants?.find(participantId) ?: return
            dialog.value = MusicTogetherDialogUiState.TransferHost(participant.id, participant.name)
        }

        fun requestKickParticipant(participantId: String) {
            val participant = successModel()?.participants?.find(participantId) ?: return
            dialog.value = MusicTogetherDialogUiState.KickParticipant(participant.id, participant.name)
        }

        fun requestBanParticipant(participantId: String) {
            val participant = successModel()?.participants?.find(participantId) ?: return
            dialog.value = MusicTogetherDialogUiState.BanParticipant(participant.id, participant.name)
        }

        fun confirmTransferHost(participantId: String) {
            dismissDialog()
            sessionActions.transferHostOwnership(participantId)
        }

        fun confirmKickParticipant(participantId: String) {
            dismissDialog()
            sessionActions.kickParticipant(participantId)
        }

        fun confirmBanParticipant(participantId: String) {
            dismissDialog()
            sessionActions.banParticipant(participantId)
        }

        fun requestSendBuddy(participantId: String, participantName: String) {
            val model = successModel() ?: return
            val myUid = buddyRepository.currentUid
            val selfParticipant = myUid?.let { model.participants.find(it) }
            val selfDisplayName = selfParticipant?.name ?: model.host.displayName

            dialog.value = MusicTogetherDialogUiState.SendBuddyRequest(
                participantId = participantId,
                participantName = participantName,
                fromDisplayName = selfDisplayName,
            )
        }

        fun confirmSendBuddyRequest(
            participantId: String,
            fromDisplayName: String,
            participantName: String,
        ) {
            dismissDialog()
            viewModelScope.launch {
                val result = buddyRepository.sendBuddyRequest(
                    toUid = participantId,
                    fromDisplayName = fromDisplayName,
                    toDisplayName = participantName,
                )
                if (result.isSuccess) {
                    effectsFlow.emit(MusicTogetherEffect.ToastMessage(R.string.together_buddy_request_sent))
                }
            }
        }

        private fun successModel(): MusicTogetherUiModel? = (state.value as? MusicTogetherScreenState.Success)?.model

        private fun pushSettingsToActiveSession(
            addTracks: Boolean? = null,
            controlPlayback: Boolean? = null,
            approval: Boolean? = null,
        ) {
            val model = successModel() ?: return
            if (!model.status.active || !model.host.visible) return
            sessionActions.updateSettings(
                TogetherRoomSettings(
                    allowGuestsToAddTracks = addTracks ?: model.host.allowGuestsToAddTracks,
                    allowGuestsToControlPlayback = controlPlayback ?: model.host.allowGuestsToControlPlayback,
                    requireHostApprovalToJoin = approval ?: model.host.requireHostApprovalToJoin,
                ),
            )
        }

        private fun MusicTogetherSnapshot.toUiModel(
            hostOnline: Boolean,
            joinOnline: Boolean,
            dialogState: MusicTogetherDialogUiState,
            welcomeDismissed: Boolean,
            dontShowAgain: Boolean,
            log: MusicTogetherActivityLogUiModels,
            buddies: List<moe.rgsekai.sekaitune.buddy.Buddy>,
            outgoingRequests: List<moe.rgsekai.sekaitune.buddy.BuddyRequest>,
            invitedSessions: List<moe.rgsekai.sekaitune.buddy.TogetherSessionSummary>,
        ): MusicTogetherUiModel {
            val state = sessionState
            val roomState = state.roomStateOrNull()
            val isHosting = state is TogetherSessionState.Hosting || state is TogetherSessionState.HostingOnline
            val isJoining = state is TogetherSessionState.Joining || state is TogetherSessionState.JoiningOnline
            val isHostRole =
                when (state) {
                    is TogetherSessionState.Hosting,
                    is TogetherSessionState.HostingOnline,
                    -> true

                    is TogetherSessionState.Joined -> state.role is TogetherRole.Host

                    else -> false
                }
            val isCreatingSessionLoading =
                when (state) {
                    is TogetherSessionState.Hosting -> state.roomState == null
                    is TogetherSessionState.HostingOnline -> state.roomState == null
                    else -> false
                }
            val isJoinedAsGuest = state is TogetherSessionState.Joined && state.role is TogetherRole.Guest
            val isWaitingApproval =
                state is TogetherSessionState.Joined &&
                    state.role is TogetherRole.Guest &&
                    state.roomState.participants
                        .firstOrNull { it.id == state.selfParticipantId }
                        ?.isPending == true
            val isJoinedAsAcceptedGuest = isJoinedAsGuest && !isWaitingApproval
            val disableJoinUi = isHostRole || isCreatingSessionLoading || isJoinedAsGuest
            val isActive = isHosting || (state is TogetherSessionState.Joined)
            val isSignedIn = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.isAnonymous == false
            val rawIsSignInRequired = (state as? TogetherSessionState.Error)?.isSignInRequired == true
            val isSignInRequired = rawIsSignInRequired && !isSignedIn
            val isActualError = state is TogetherSessionState.Error && (!rawIsSignInRequired || isSignInRequired)
            if (rawIsSignInRequired && isSignedIn) {
                sessionActions.leaveSession()
            }
            val status =
                MusicTogetherStatusUiModel(
                    titleResId = R.string.together_status,
                    stateLabelResId =
                        when (state) {
                            TogetherSessionState.Idle -> {
                                R.string.together_idle
                            }

                            is TogetherSessionState.Hosting,
                            is TogetherSessionState.HostingOnline,
                            -> {
                                R.string.together_hosting
                            }

                            is TogetherSessionState.Joining,
                            is TogetherSessionState.JoiningOnline,
                            -> {
                                R.string.together_joining
                            }

                            is TogetherSessionState.Joined -> {
                                if (isWaitingApproval) R.string.together_waiting_approval else R.string.together_connected
                            }

                            is TogetherSessionState.Error -> {
                                if (isSignInRequired) {
                                    R.string.together_signin_required
                                } else if (!isActualError) {
                                    R.string.together_idle
                                } else {
                                    R.string.together_error_state
                                }
                            }
                        },
                    errorMessage = if (isActualError) (state as? TogetherSessionState.Error)?.message else null,
                    iconResId =
                        when {
                            isSignInRequired -> R.drawable.person
                            isActualError -> R.drawable.error
                            else -> R.drawable.fire
                        },
                    active = isActive,
                    error = isActualError,
                    waitingApproval = isWaitingApproval,
                    canLeave = isActive || isJoining,
                    showSignInAction = isSignInRequired,
                )
            val sessionShare =
                if (isHostRole) {
                    val code =
                        when (state) {
                            is TogetherSessionState.HostingOnline -> state.code
                            is TogetherSessionState.Joined -> state.code ?: roomState?.code
                            else -> roomState?.code
                        }
                    val link =
                        when (state) {
                            is TogetherSessionState.Hosting -> state.joinLink
                            is TogetherSessionState.Joined -> state.joinLink ?: roomState?.joinLink
                            else -> roomState?.joinLink
                        }
                    when {
                        !code.isNullOrBlank() -> {
                            MusicTogetherSessionShareUiModel(
                                labelResId = R.string.session_code,
                                value = code,
                                maxLines = 2,
                            )
                        }

                        !link.isNullOrBlank() -> {
                            MusicTogetherSessionShareUiModel(
                                labelResId = R.string.session_link,
                                value = link,
                                maxLines = 3,
                            )
                        }

                        else -> {
                            null
                        }
                    }
                } else {
                    null
                }
            val playback = roomState.toPlaybackUiModel()
            val host =
                MusicTogetherHostUiModel(
                    onlineMode = hostOnline,
                    displayName = preferences.displayName,
                    port = preferences.port,
                    allowGuestsToAddTracks = preferences.allowGuestsToAddTracks,
                    allowGuestsToControlPlayback = preferences.allowGuestsToControlPlayback,
                    requireHostApprovalToJoin = preferences.requireHostApprovalToJoin,
                    visible = !isJoinedAsGuest,
                    startEnabled = !isCreatingSessionLoading && !isJoining && !isHosting && state !is TogetherSessionState.Joined,
                    loading = isCreatingSessionLoading,
                    photoUrl = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()?.ifBlank { null },
                )
            val joinInput = preferences.lastJoinLink
            val canJoin =
                if (joinOnline) {
                    joinInput.trim().isNotBlank()
                } else {
                    TogetherLink.decode(joinInput) != null
                }
            val join =
                MusicTogetherJoinUiModel(
                    onlineMode = joinOnline,
                    input = joinInput,
                    hintResId = if (joinOnline) R.string.together_join_code_hint else R.string.together_join_link_hint,
                    canJoin = canJoin && !disableJoinUi && !isJoining && !isJoinedAsAcceptedGuest && !isWaitingApproval,
                    disabled = disableJoinUi,
                    joined = isJoinedAsAcceptedGuest,
                    waitingApproval = isWaitingApproval,
                    joining = isJoining,
                )
            val showModerationActions =
                state is TogetherSessionState.HostingOnline ||
                    (state is TogetherSessionState.Joined && state.role is TogetherRole.Host && (state.code != null || roomState?.code != null))

            val currentAuthUid = buddyRepository.currentUid
            val selfParticipantId = when (state) {
                is TogetherSessionState.Joined -> state.selfParticipantId
                is TogetherSessionState.HostingOnline -> state.roomState?.hostId ?: currentAuthUid
                is TogetherSessionState.Hosting -> state.roomState?.hostId ?: currentAuthUid
                else -> currentAuthUid
            }
            val isOnlineSession = state is TogetherSessionState.HostingOnline || (state is TogetherSessionState.Joined && state.code != null)

            val participantModels =
                MusicTogetherParticipantUiModels(
                    roomState
                        ?.participants
                        .orEmpty()
                        .map { participant ->
                            val isSelf = participant.id == selfParticipantId || (isHostRole && participant.isHost)
                            val isBuddy = buddies.any { it.uid == participant.id }
                            val hasOutgoingRequest = outgoingRequests.any { it.toUid == participant.id }
                            val showAddBuddy = isOnlineSession && !isSelf && !isBuddy && !hasOutgoingRequest && !participant.isPending

                            MusicTogetherParticipantUiModel(
                                id = participant.id,
                                name = participant.name,
                                host = participant.isHost,
                                pending = participant.isPending,
                                connected = participant.isConnected,
                                showApproveActions =
                                    isHostRole && showModerationActions && participant.isPending &&
                                        roomState?.settings?.requireHostApprovalToJoin == true,
                                showModerationActions = isHostRole && showModerationActions && !participant.isHost,
                                showTransferHostAction = isHostRole && !participant.isHost && !participant.isPending,
                                showAddBuddyAction = showAddBuddy,
                                photoUrl = participant.photoUrl,
                            )
                        },
                )
            return MusicTogetherUiModel(
                showWelcomeDialog = !preferences.welcomeShown && !welcomeDismissed,
                welcomeDontShowAgain = dontShowAgain,
                dialog = dialogState,
                status = status,
                sessionShare = sessionShare,
                playback = playback,
                host = host,
                join = join,
                participants = participantModels,
                activityLog = log,
                invitedSessions = invitedSessions,
            )
        }

        private fun TogetherRoomState?.toPlaybackUiModel(): MusicTogetherPlaybackUiModel {
            val track = this?.queue?.getOrNull(currentIndex)
            return MusicTogetherPlaybackUiModel(
                title = track?.title,
                artists = track?.artists?.joinToString()?.takeIf { it.isNotBlank() },
                playbackStateResId =
                    when {
                        this == null -> R.string.together_playback_empty
                        isPlaying -> R.string.together_playback_playing
                        else -> R.string.together_playback_paused
                    },
                queueSize = this?.queue?.size ?: 0,
                currentIndexLabel =
                    if (this != null && queue.isNotEmpty()) {
                        context.getString(
                            R.string.together_queue_position,
                            currentIndex.coerceAtLeast(0) + 1,
                            queue.size,
                        )
                    } else {
                        null
                    },
                shuffleEnabled = this?.shuffleEnabled ?: false,
                repeatMode = this?.repeatMode ?: 0,
            )
        }

        private suspend fun updateActivityLog(sessionState: TogetherSessionState) {
            withContext(Dispatchers.Default) {
                val sessionId = sessionState.sessionIdOrNull() ?: lastSessionId
                val currentRoomState = sessionState.roomStateOrNull()

                if (sessionState == TogetherSessionState.Idle) {
                    lastSessionId = null
                    lastRoomState = null
                    lastStateWasError = false
                    clearLog()
                    return@withContext
                }

                if (sessionId == null) return@withContext

                if (lastSessionId != sessionId) {
                    lastSessionId = sessionId
                    lastRoomState = null
                    lastStateWasError = false
                    clearLog()
                    addLog(
                        iconResId = R.drawable.fire,
                        messageResId =
                            when (sessionState) {
                                is TogetherSessionState.Joining,
                                is TogetherSessionState.JoiningOnline,
                                -> R.string.together_activity_joining_session

                                else -> R.string.together_activity_session_started
                            },
                        args = emptyList(),
                    )
                }

                if (sessionState is TogetherSessionState.Error && !lastStateWasError) {
                    addLog(
                        iconResId = R.drawable.error,
                        messageResId = R.string.together_activity_error,
                        args = listOf(sessionState.message.ifBlank { "" }),
                    )
                    lastStateWasError = true
                } else if (sessionState !is TogetherSessionState.Error) {
                    lastStateWasError = false
                }

                if (currentRoomState != null) {
                    val previousRoomState = lastRoomState
                    if (previousRoomState != null) {
                        appendRoomStateDiff(previousRoomState, currentRoomState)
                    }
                    lastRoomState = currentRoomState
                }

                activityLog.value = MusicTogetherActivityLogUiModels(logItems.toList())
            }
        }

        private fun appendRoomStateDiff(
            previous: TogetherRoomState,
            current: TogetherRoomState,
        ) {
            val previousParticipants = previous.participants.associateBy { it.id }
            val currentParticipants = current.participants.associateBy { it.id }
            current.participants.forEach { participant ->
                if (previousParticipants[participant.id] == null && !participant.isHost) {
                    addLog(
                        iconResId = R.drawable.person,
                        messageResId =
                            if (participant.isPending) {
                                R.string.together_activity_join_requested
                            } else {
                                R.string.together_participant_joined_notification
                            },
                        args = listOf(participant.name),
                    )
                }
            }
            previous.participants.forEach { participant ->
                if (currentParticipants[participant.id] == null && !participant.isHost) {
                    addLog(
                        iconResId = R.drawable.leave,
                        messageResId = R.string.together_participant_left_notification,
                        args = listOf(participant.name),
                    )
                }
            }

            val prevHostName = previous.actorName(previous.hostId)
            val currHostName = current.actorName(current.hostId)
            if (previous.hostId.isNotBlank() && current.hostId.isNotBlank() && previous.hostId != current.hostId && prevHostName != currHostName && prevHostName != "Unknown" && currHostName != "Unknown") {
                addLog(
                    iconResId = R.drawable.sync,
                    messageResId = R.string.together_activity_host_transferred,
                    args = listOf(currHostName),
                )
            }

            val actor = current.actorName(current.hostId)
            if (previous.isPlaying != current.isPlaying) {
                addLog(
                    iconResId = if (current.isPlaying) R.drawable.play else R.drawable.pause,
                    messageResId = if (current.isPlaying) R.string.together_activity_playback_resumed else R.string.together_activity_playback_paused,
                    args = listOf(actor),
                )
            }

            val previousTrack = previous.queue.getOrNull(previous.currentIndex)
            val currentTrack = current.queue.getOrNull(current.currentIndex)
            if (previousTrack?.id != currentTrack?.id && currentTrack != null) {
                addLog(
                    iconResId = R.drawable.music_note,
                    messageResId = R.string.together_activity_song_changed,
                    args = listOf(actor, currentTrack.title),
                )
            } else if (previous.currentIndex != current.currentIndex && currentTrack != null) {
                addLog(
                    iconResId = R.drawable.skip_next,
                    messageResId = R.string.together_activity_song_skipped,
                    args = listOf(actor, currentTrack.title),
                )
            }

            if (previous.queueHash != current.queueHash || previous.queue.size != current.queue.size) {
                addLog(
                    iconResId = R.drawable.queue_music,
                    messageResId = R.string.together_activity_queue_updated,
                    args = listOf(actor),
                )
            }

            if (previous.shuffleEnabled != current.shuffleEnabled || previous.repeatMode != current.repeatMode) {
                addLog(
                    iconResId = R.drawable.settings,
                    messageResId = R.string.together_activity_playback_state_changed,
                    args = listOf(actor),
                )
            }
        }

        private fun TogetherRoomState.actorName(participantId: String): String =
            participants.firstOrNull { it.id == participantId }?.name
                ?: participants.firstOrNull { it.isHost }?.name
                ?: context.getString(R.string.together_role_host)

        private fun addLog(
            @DrawableRes iconResId: Int,
            @StringRes messageResId: Int,
            args: List<String>,
        ) {
            logItems.addFirst(
                MusicTogetherActivityLogItemUiModel(
                    id = nextLogId++,
                    iconResId = iconResId,
                    messageResId = messageResId,
                    args = args,
                    timestamp = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()),
                ),
            )
            while (logItems.size > MaxActivityLogSize) {
                logItems.removeLast()
            }
        }

        private fun clearLog() {
            logItems.clear()
            activityLog.value = MusicTogetherActivityLogUiModels(emptyList())
        }

        private fun TogetherSessionState.sessionIdOrNull(): String? =
            when (this) {
                is TogetherSessionState.Hosting -> sessionId

                is TogetherSessionState.HostingOnline -> sessionId

                is TogetherSessionState.Joined -> sessionId

                is TogetherSessionState.Joining -> TogetherLink.decode(joinLink)?.sessionId

                is TogetherSessionState.JoiningOnline -> code

                is TogetherSessionState.Error,
                TogetherSessionState.Idle,
                -> null
            }

        private fun TogetherSessionState.roomStateOrNull(): TogetherRoomState? =
            when (this) {
                is TogetherSessionState.Hosting -> roomState
                is TogetherSessionState.HostingOnline -> roomState
                is TogetherSessionState.Joined -> roomState
                else -> null
            }

        private fun MusicTogetherParticipantUiModels.find(participantId: String): MusicTogetherParticipantUiModel? {
            for (index in 0 until size) {
                val participant = get(index)
                if (participant.id == participantId) return participant
            }
            return null
        }

        private companion object {
            const val MaxActivityLogSize = 80
        }
    }




