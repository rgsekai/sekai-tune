/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.viewmodels

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.downloads.DeviceDownloadRepository
import moe.rgsekai.sekaitune.downloads.DownloadEntryUiModel
import moe.rgsekai.sekaitune.downloads.DownloadLibraryUiModel
import moe.rgsekai.sekaitune.downloads.DownloadMediaType
import moe.rgsekai.sekaitune.downloads.DownloadSectionUiModel
import moe.rgsekai.sekaitune.downloads.ManageDownloadsUseCase
import moe.rgsekai.sekaitune.models.MediaMetadata
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

enum class DownloadStorageType {
    IN_APP,
    DEVICE,
}

enum class DownloadLibraryTab {
    DOWNLOADED,
    PROGRESS,
}

sealed interface DownloadLibraryScreenState {
    val selectedStorage: DownloadStorageType
    val selectedTab: DownloadLibraryTab
    val query: String
    val isSearchActive: Boolean
    val pendingRemoval: DownloadRemovalConfirmation?

    data class Loading(
        override val selectedStorage: DownloadStorageType,
        override val selectedTab: DownloadLibraryTab,
        override val query: String,
        override val isSearchActive: Boolean,
        override val pendingRemoval: DownloadRemovalConfirmation? = null,
    ) : DownloadLibraryScreenState

    data class Success(
        override val selectedStorage: DownloadStorageType,
        override val selectedTab: DownloadLibraryTab,
        val library: DownloadLibraryUiModel,
        override val query: String,
        override val isSearchActive: Boolean,
        override val pendingRemoval: DownloadRemovalConfirmation? = null,
    ) : DownloadLibraryScreenState

    data class Empty(
        override val selectedStorage: DownloadStorageType,
        override val selectedTab: DownloadLibraryTab,
        override val query: String,
        override val isSearchActive: Boolean,
        override val pendingRemoval: DownloadRemovalConfirmation? = null,
    ) : DownloadLibraryScreenState

    data class Error(
        override val selectedStorage: DownloadStorageType,
        override val selectedTab: DownloadLibraryTab,
        @StringRes val messageRes: Int,
        override val query: String,
        override val isSearchActive: Boolean,
        override val pendingRemoval: DownloadRemovalConfirmation? = null,
    ) : DownloadLibraryScreenState
}

@Immutable
sealed interface DownloadRemovalConfirmation {
    data class Entry(
        val entry: DownloadEntryUiModel,
    ) : DownloadRemovalConfirmation

    data class Section(
        val section: DownloadSectionUiModel,
    ) : DownloadRemovalConfirmation
}

sealed interface DownloadLibraryEvent {
    data class Message(
        @StringRes val messageRes: Int,
    ) : DownloadLibraryEvent

    data class Navigate(
        val route: String,
    ) : DownloadLibraryEvent

    data class PlaySong(
        val metadata: MediaMetadata,
        val queue: ImmutableList<MediaMetadata>,
        val startIndex: Int,
    ) : DownloadLibraryEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DownloadLibraryViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val manageDownloads: ManageDownloadsUseCase,
        private val deviceDownloadRepository: DeviceDownloadRepository,
    ) : ViewModel() {
        private val initialTab =
            if (savedStateHandle.get<String>("tab") == PROGRESS_TAB_ARGUMENT) {
                DownloadLibraryTab.PROGRESS
            } else {
                DownloadLibraryTab.DOWNLOADED
            }

        private val initialStorage =
            if (savedStateHandle.get<String>("storage") == DEVICE_STORAGE_ARGUMENT) {
                DownloadStorageType.DEVICE
            } else {
                DownloadStorageType.IN_APP
            }

        private val selectedStorage = MutableStateFlow(initialStorage)
        private val selectedTab = MutableStateFlow(initialTab)
        private val query = MutableStateFlow("")
        private val isSearchActive = MutableStateFlow(false)
        private val pendingRemoval = MutableStateFlow<DownloadRemovalConfirmation?>(null)
        private val actionJobs = ConcurrentHashMap<String, Job>()
        private val eventChannel = Channel<DownloadLibraryEvent>(Channel.BUFFERED)

        val events = eventChannel.receiveAsFlow()

        private val activeLibraryFlow =
            selectedStorage.flatMapLatest { storage ->
                when (storage) {
                    DownloadStorageType.IN_APP ->
                        manageDownloads
                            .observe()
                            .map<DownloadLibraryUiModel, DownloadLibraryResult> { DownloadLibraryResult.Data(it) }
                            .catch { emit(DownloadLibraryResult.Failure) }

                    DownloadStorageType.DEVICE ->
                        combine(
                            deviceDownloadRepository.observeDownloaded(),
                            deviceDownloadRepository.observeInProgress(),
                        ) { downloaded, inProgress ->
                            val downloadedSections =
                                if (downloaded.isNotEmpty()) {
                                    listOf(DownloadSectionUiModel(DownloadMediaType.SONG, downloaded))
                                } else {
                                    emptyList()
                                }
                            val inProgressSections =
                                if (inProgress.isNotEmpty()) {
                                    listOf(DownloadSectionUiModel(DownloadMediaType.SONG, inProgress))
                                } else {
                                    emptyList()
                                }
                            DownloadLibraryUiModel(
                                downloadedSections = downloadedSections,
                                progressSections = inProgressSections,
                            )
                        }.map<DownloadLibraryUiModel, DownloadLibraryResult> { DownloadLibraryResult.Data(it) }
                            .catch { emit(DownloadLibraryResult.Failure) }
                }
            }

        val screenState: StateFlow<DownloadLibraryScreenState> =
            combine(
                activeLibraryFlow,
                selectedStorage,
                selectedTab,
                query,
                combine(isSearchActive, pendingRemoval) { active, removal -> active to removal },
            ) { result, storage, tab, currentQuery, (searchActive, removalConfirmation) ->
                when (result) {
                    is DownloadLibraryResult.Data -> {
                        if (result.library.isEmpty) {
                            DownloadLibraryScreenState.Empty(
                                selectedStorage = storage,
                                selectedTab = tab,
                                query = currentQuery,
                                isSearchActive = searchActive,
                                pendingRemoval = removalConfirmation,
                            )
                        } else {
                            DownloadLibraryScreenState.Success(
                                selectedStorage = storage,
                                selectedTab = tab,
                                library = result.library.filteredBy(currentQuery),
                                query = currentQuery,
                                isSearchActive = searchActive,
                                pendingRemoval = removalConfirmation,
                            )
                        }
                    }

                    DownloadLibraryResult.Failure -> {
                        DownloadLibraryScreenState.Error(
                            selectedStorage = storage,
                            selectedTab = tab,
                            messageRes = R.string.downloads_load_failed,
                            query = currentQuery,
                            isSearchActive = searchActive,
                            pendingRemoval = removalConfirmation,
                        )
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue =
                    DownloadLibraryScreenState.Loading(
                        selectedStorage = selectedStorage.value,
                        selectedTab = selectedTab.value,
                        query = "",
                        isSearchActive = false,
                    ),
            )

        fun selectStorage(storage: DownloadStorageType) {
            selectedStorage.value = storage
        }

        fun selectTab(tab: DownloadLibraryTab) {
            selectedTab.value = tab
        }

        fun activateSearch() {
            isSearchActive.value = true
        }

        fun closeSearch() {
            query.value = ""
            isSearchActive.value = false
        }

        fun submitSearch() {
            isSearchActive.value = false
        }

        fun updateQuery(value: String) {
            query.value = value
        }

        fun clearQuery() {
            query.value = ""
        }

        fun open(entry: DownloadEntryUiModel) {
            val event =
                entry.destinationRoute?.let { DownloadLibraryEvent.Navigate(it) }
                    ?: createPlaySongEvent(entry)
                    ?: return
            eventChannel.trySend(event)
        }

        private fun createPlaySongEvent(entry: DownloadEntryUiModel): DownloadLibraryEvent.PlaySong? {
            val selectedMetadata = entry.playbackMetadata ?: return null
            val downloadedSongQueue = currentDownloadedSongQueue()
            val startIndex = downloadedSongQueue.indexOfFirst { metadata -> metadata.id == selectedMetadata.id }

            return if (startIndex >= 0) {
                DownloadLibraryEvent.PlaySong(
                    metadata = selectedMetadata,
                    queue = downloadedSongQueue,
                    startIndex = startIndex,
                )
            } else {
                DownloadLibraryEvent.PlaySong(
                    metadata = selectedMetadata,
                    queue = ImmutableList.of(selectedMetadata),
                    startIndex = 0,
                )
            }
        }

        private fun currentDownloadedSongQueue(): ImmutableList<MediaMetadata> {
            val state = screenState.value as? DownloadLibraryScreenState.Success ?: return ImmutableList.of()
            if (state.selectedTab != DownloadLibraryTab.DOWNLOADED) return ImmutableList.of()

            val metadata =
                state.library.downloadedSections
                    .firstOrNull { section -> section.mediaType == DownloadMediaType.SONG }
                    ?.entries
                    .orEmpty()
                    .mapNotNull { entry -> entry.playbackMetadata }
            return ImmutableList.copyOf(metadata)
        }

        fun pause(entry: DownloadEntryUiModel) =
            runAction(entry.id) {
                if (selectedStorage.value == DownloadStorageType.IN_APP) {
                    manageDownloads.pause(entry.songIds)
                } else {
                    deviceDownloadRepository.cancel(entry.id)
                }
            }

        fun resume(entry: DownloadEntryUiModel) =
            runAction(entry.id) {
                if (selectedStorage.value == DownloadStorageType.IN_APP) {
                    manageDownloads.resume(entry.songIds)
                } else {
                    deviceDownloadRepository.retry(entry)
                }
            }

        fun remove(entry: DownloadEntryUiModel) =
            runAction(entry.id) {
                if (selectedStorage.value == DownloadStorageType.IN_APP) {
                    manageDownloads.remove(entry.songIds)
                } else {
                    deviceDownloadRepository.delete(entry)
                }
            }

        fun pause(section: DownloadSectionUiModel) =
            runAction("section:${section.mediaType}") {
                if (selectedStorage.value == DownloadStorageType.IN_APP) {
                    manageDownloads.pause(section.songIds)
                }
            }

        fun resume(section: DownloadSectionUiModel) =
            runAction("section:${section.mediaType}") {
                if (selectedStorage.value == DownloadStorageType.IN_APP) {
                    manageDownloads.resume(section.songIds)
                }
            }

        fun remove(section: DownloadSectionUiModel) =
            runAction("section:${section.mediaType}") {
                if (selectedStorage.value == DownloadStorageType.IN_APP) {
                    manageDownloads.remove(section.songIds)
                } else {
                    section.entries.forEach { entry ->
                        deviceDownloadRepository.delete(entry)
                    }
                }
            }

        fun requestRemove(entry: DownloadEntryUiModel) {
            pendingRemoval.value = DownloadRemovalConfirmation.Entry(entry)
        }

        fun requestRemove(section: DownloadSectionUiModel) {
            pendingRemoval.value = DownloadRemovalConfirmation.Section(section)
        }

        fun dismissRemoveConfirmation() {
            pendingRemoval.value = null
        }

        fun confirmRemove() {
            val confirmation = pendingRemoval.value ?: return
            pendingRemoval.value = null
            when (confirmation) {
                is DownloadRemovalConfirmation.Entry -> remove(confirmation.entry)
                is DownloadRemovalConfirmation.Section -> remove(confirmation.section)
            }
        }

        private fun runAction(
            key: String,
            action: () -> Unit,
        ) {
            actionJobs.remove(key)?.cancel()
            val job =
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching(action)
                        .onFailure {
                            eventChannel.send(DownloadLibraryEvent.Message(R.string.download_action_failed))
                        }
                }
            actionJobs[key] = job
            job.invokeOnCompletion { actionJobs.remove(key, job) }
        }

        private sealed interface DownloadLibraryResult {
            data class Data(
                val library: DownloadLibraryUiModel,
            ) : DownloadLibraryResult

            data object Failure : DownloadLibraryResult
        }

        private fun DownloadLibraryUiModel.filteredBy(query: String): DownloadLibraryUiModel {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isEmpty()) return this

            fun List<DownloadSectionUiModel>.filteredSections(): List<DownloadSectionUiModel> =
                mapNotNull { section ->
                    section.entries
                        .filter { entry ->
                            entry.title.contains(normalizedQuery, ignoreCase = true) ||
                                entry.supportingText.orEmpty().contains(normalizedQuery, ignoreCase = true)
                        }.takeIf { it.isNotEmpty() }
                        ?.let { entries -> section.copy(entries = entries) }
                }

            return copy(
                downloadedSections = downloadedSections.filteredSections(),
                progressSections = progressSections.filteredSections(),
            )
        }

        private companion object {
            const val PROGRESS_TAB_ARGUMENT = "progress"
            const val DEVICE_STORAGE_ARGUMENT = "device"
        }
    }
