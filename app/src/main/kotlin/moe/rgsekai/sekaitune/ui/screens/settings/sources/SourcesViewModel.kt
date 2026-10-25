/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.settings.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.playback.sources.RegisteredSource
import moe.rgsekai.sekaitune.playback.sources.SourceRegistry
import moe.rgsekai.sekaitune.playback.sources.webdav.WebDavCredentialStore
import moe.rgsekai.sekaitune.playback.sources.webdav.WebDavScanner
import moe.rgsekai.sekaitune.webdav.WebDavClient
import moe.rgsekai.sekaitune.webdav.WebDavTestResult
import javax.inject.Inject

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val sourceRegistry: SourceRegistry,
    private val webDavCredentialStore: WebDavCredentialStore,
    private val database: MusicDatabase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val sources: StateFlow<List<RegisteredSource>> = sourceRegistry.sourcesFlow
    val toleranceSeconds: StateFlow<Int> = sourceRegistry.toleranceSecondsFlow

    private val _testingSourceId = MutableStateFlow<String?>(null)
    val testingSourceId: StateFlow<String?> = _testingSourceId.asStateFlow()

    private val _isAdding = MutableStateFlow(false)
    val isAdding: StateFlow<Boolean> = _isAdding.asStateFlow()

    private val _addError = MutableStateFlow<String?>(null)
    val addError: StateFlow<String?> = _addError.asStateFlow()

    // WebDAV State
    val isWebDavConfigured: StateFlow<Boolean> = webDavCredentialStore.isConfiguredFlow
    val webDavUrl: StateFlow<String> = webDavCredentialStore.urlFlow
    val webDavUsername: StateFlow<String> = webDavCredentialStore.usernameFlow
    val webDavTrustSelfSigned: StateFlow<Boolean> = webDavCredentialStore.trustSelfSignedFlow
    val webDavWifiOnlyTags: StateFlow<Boolean> = webDavCredentialStore.wifiOnlyTagsFlow
    val webDavResolveMatching: StateFlow<Boolean> = webDavCredentialStore.resolveMatchingFlow

    private val _isTestingWebDav = MutableStateFlow(false)
    val isTestingWebDav: StateFlow<Boolean> = _isTestingWebDav.asStateFlow()

    private val _webDavTestResult = MutableStateFlow<WebDavTestResult?>(null)
    val webDavTestResult: StateFlow<WebDavTestResult?> = _webDavTestResult.asStateFlow()

    fun setWebDavWifiOnlyTags(enabled: Boolean) {
        webDavCredentialStore.setWifiOnlyTags(enabled)
    }

    fun setWebDavResolveMatching(enabled: Boolean) {
        webDavCredentialStore.setResolveMatching(enabled)
    }

    fun toggleSource(id: String, enabled: Boolean) {
        sourceRegistry.setSourceEnabled(id, enabled)
    }

    fun reorderSources(fromIndex: Int, toIndex: Int) {
        sourceRegistry.reorderSources(fromIndex, toIndex)
    }

    fun removeSource(id: String) {
        sourceRegistry.removeSource(id)
    }

    fun setTolerance(seconds: Int) {
        sourceRegistry.setToleranceSeconds(seconds)
    }

    fun testSource(id: String) {
        viewModelScope.launch {
            _testingSourceId.value = id
            try {
                sourceRegistry.testSource(id)
            } finally {
                _testingSourceId.value = null
            }
        }
    }

    fun addAddon(url: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isAdding.value = true
            _addError.value = null
            val result = sourceRegistry.addAddon(url)
            _isAdding.value = false
            if (result.isSuccess) {
                onSuccess()
            } else {
                _addError.value = result.exceptionOrNull()?.message ?: "Failed to add addon source"
            }
        }
    }

    fun clearAddError() {
        _addError.value = null
    }

    fun getWebDavPassword(): String? = webDavCredentialStore.password.takeIf { it.isNotEmpty() }

    fun saveWebDav(url: String, username: String, password: String?, trustSelfSigned: Boolean) {
        webDavCredentialStore.saveCredentials(
            url = url,
            username = username,
            password = password.orEmpty(),
            trustSelfSigned = trustSelfSigned,
        )
        // Trigger initial scan upon successful save
        WebDavScanner.startScan(context, force = true)
    }

    fun disconnectWebDav(deleteData: Boolean = true) {
        webDavCredentialStore.disconnect()
        _webDavTestResult.value = null
        if (deleteData) {
            WebDavScanner.clearCachedData(context, database)
        } else {
            WebDavScanner.cancelScan(context)
        }
    }

    fun clearWebDavTestResult() {
        _webDavTestResult.value = null
    }

    fun testWebDavConnection(
        url: String,
        username: String,
        password: String?,
        trustSelfSigned: Boolean,
    ) {
        viewModelScope.launch {
            _isTestingWebDav.value = true
            _webDavTestResult.value = null
            val result = withContext(Dispatchers.IO) {
                WebDavClient.testConnection(
                    url = url,
                    username = username,
                    password = password,
                    trustSelfSignedCert = trustSelfSigned,
                )
            }
            _webDavTestResult.value = result
            _isTestingWebDav.value = false
        }
    }

    fun testSavedWebDavConnection() {
        viewModelScope.launch {
            _isTestingWebDav.value = true
            _webDavTestResult.value = null
            val result = withContext(Dispatchers.IO) {
                WebDavClient.testConnection(
                    url = webDavCredentialStore.url,
                    username = webDavCredentialStore.username,
                    password = webDavCredentialStore.password,
                    trustSelfSignedCert = webDavCredentialStore.trustSelfSigned,
                )
            }
            _webDavTestResult.value = result
            _isTestingWebDav.value = false
        }
    }
}
