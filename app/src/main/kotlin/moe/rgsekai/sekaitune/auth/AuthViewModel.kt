/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.sync.SettingsSyncRepository
import moe.rgsekai.sekaitune.sync.SyncStatus
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val settingsSyncRepository: SettingsSyncRepository,
    private val userProfileManager: UserProfileManager,
) : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    var currentUser by mutableStateOf<FirebaseUser?>(auth.currentUser)
        private set

    val syncStatus: StateFlow<SyncStatus> = settingsSyncRepository.syncStatus

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            val previousUser = currentUser
            currentUser = user
            if (user != null && !user.isAnonymous && user.uid != previousUser?.uid) {
                viewModelScope.launch {
                    settingsSyncRepository.syncOnSignIn(user.uid)
                    userProfileManager.syncUserProfile(user)
                }
            }
        }
    }

    fun syncNow() {
        val user = currentUser ?: return
        if (user.isAnonymous) return
        viewModelScope.launch {
            settingsSyncRepository.pushSettings(user.uid)
        }
    }

    fun pullNow() {
        val user = currentUser ?: return
        if (user.isAnonymous) return
        viewModelScope.launch {
            settingsSyncRepository.pullSettings(user.uid)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}