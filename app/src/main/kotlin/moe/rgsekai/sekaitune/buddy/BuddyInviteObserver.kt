/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.buddy

import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.TogetherDisplayNameKey
import moe.rgsekai.sekaitune.playback.PlayerConnection
import moe.rgsekai.sekaitune.utils.dataStore

@Composable
fun BuddyInviteObserver(
    buddyRepository: BuddyRepository,
    playerConnection: PlayerConnection?,
    navController: NavController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val invitedSessions by buddyRepository.observeInvitedSessions().collectAsStateWithLifecycle(initialValue = emptyList())
    val incomingRequests by buddyRepository.observeIncomingRequests().collectAsStateWithLifecycle(initialValue = emptyList())

    val observerStartTime = remember { System.currentTimeMillis() }
    val promptedSessionIds = remember { mutableSetOf<String>() }
    val promptedRequestIds = remember { mutableSetOf<String>() }
    var activeInvite by remember { mutableStateOf<TogetherSessionSummary?>(null) }
    var activeRequest by remember { mutableStateOf<BuddyRequest?>(null) }

    LaunchedEffect(invitedSessions) {
        val currentSessionIds = invitedSessions.map { it.sessionId }.toSet()

        // Only prompt in-app popup dialog for fresh invites that arrived during or right before this session
        if (activeInvite == null) {
            val freshInvite = invitedSessions.firstOrNull { session ->
                session.sessionId !in promptedSessionIds &&
                (session.createdAt == 0L || session.createdAt >= observerStartTime - 60_000L)
            }
            if (freshInvite != null) {
                promptedSessionIds.add(freshInvite.sessionId)
                activeInvite = freshInvite
            }
        } else {
            // If the active invite is no longer in invitedSessions, clear it
            if (activeInvite?.sessionId !in currentSessionIds) {
                activeInvite = null
            }
        }
    }

    LaunchedEffect(incomingRequests) {
        val currentRequestIds = incomingRequests.map { it.id }.toSet()

        if (activeRequest == null) {
            val freshRequest = incomingRequests.firstOrNull { req ->
                req.id !in promptedRequestIds &&
                (req.createdAt == 0L || req.createdAt >= observerStartTime - 60_000L)
            }
            if (freshRequest != null) {
                promptedRequestIds.add(freshRequest.id)
                activeRequest = freshRequest

                // Post system tray notification so user gets notified on device
                BuddyNotificationManager.showBuddyRequestNotification(
                    context = context,
                    fromDisplayName = freshRequest.fromDisplayName.ifBlank { "A user" },
                    fromUid = freshRequest.fromUid,
                )
            }
        } else {
            if (activeRequest?.id !in currentRequestIds) {
                activeRequest = null
            }
        }
    }

    activeRequest?.let { request ->
        AlertDialog(
            onDismissRequest = {
                promptedRequestIds.add(request.id)
                activeRequest = null
            },
            title = {
                Text(text = stringResource(R.string.together_buddy_request_title))
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.together_buddy_request_received_from,
                        request.fromDisplayName.ifBlank { "A user" },
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        promptedRequestIds.add(request.id)
                        activeRequest = null
                        navController.navigate("settings/buddies") {
                            launchSingleTop = true
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.together_buddy_view_requests))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        promptedRequestIds.add(request.id)
                        activeRequest = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.together_decline_action))
                }
            },
        )
    }

    activeInvite?.let { invite ->
        AlertDialog(
            onDismissRequest = {
                val sessionId = invite.sessionId
                promptedSessionIds.add(sessionId)
                activeInvite = null
                BuddyNotificationManager.cancelInviteNotification(context, sessionId)
                coroutineScope.launch(Dispatchers.IO) {
                    buddyRepository.dismissSessionInvite(sessionId)
                }
            },
            title = {
                Text(text = stringResource(R.string.together_invite_dialog_title))
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.together_invite_dialog_text,
                        invite.hostDisplayName,
                        invite.code,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val sessionId = invite.sessionId
                        val code = invite.code
                        promptedSessionIds.add(sessionId)
                        activeInvite = null
                        BuddyNotificationManager.cancelInviteNotification(context, sessionId)

                        coroutineScope.launch(Dispatchers.IO) {
                            buddyRepository.dismissSessionInvite(sessionId)
                            val displayName = runCatching { context.dataStore.data.first()[TogetherDisplayNameKey] }
                                .getOrNull()
                                ?.trim()
                                .orEmpty()
                                .ifBlank { Build.MODEL ?: context.getString(R.string.app_name) }

                            withContext(Dispatchers.Main) {
                                playerConnection?.service?.joinTogetherOnline(code, displayName)
                                navController.navigate("settings/music_together") {
                                    launchSingleTop = true
                                }
                            }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.together_join_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val sessionId = invite.sessionId
                        promptedSessionIds.add(sessionId)
                        activeInvite = null
                        BuddyNotificationManager.cancelInviteNotification(context, sessionId)
                        coroutineScope.launch(Dispatchers.IO) {
                            buddyRepository.dismissSessionInvite(sessionId)
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.together_decline_action))
                }
            },
        )
    }
}
