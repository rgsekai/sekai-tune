/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.viewmodels

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.artist.ArtistBlockRequest
import moe.rgsekai.sekaitune.artist.ObserveArtistBlockedUseCase
import moe.rgsekai.sekaitune.artist.SetArtistBlockedUseCase
import moe.rgsekai.sekaitune.constants.HideExplicitKey
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.extensions.filterBlockedArtists
import moe.rgsekai.sekaitune.extensions.filterExplicit
import moe.rgsekai.sekaitune.extensions.filterExplicitAlbums
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.innertube.models.filterExplicit
import moe.rgsekai.sekaitune.innertube.pages.ArtistPage
import moe.rgsekai.sekaitune.utils.dataStore
import moe.rgsekai.sekaitune.utils.get
import moe.rgsekai.sekaitune.utils.reportException
import javax.inject.Inject

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import moe.rgsekai.sekaitune.constants.ShowSpotifyFollowArtistKey
import moe.rgsekai.sekaitune.constants.SpotifyAccessTokenKey
import moe.rgsekai.sekaitune.constants.SpotifySpDcKey
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.spotify.SpotifyArtistResolution
import moe.rgsekai.sekaitune.spotify.SpotifyArtistResolver
import moe.rgsekai.sekaitune.spotify.SpotifyLibraryRepository

sealed interface SpotifyFollowState {
    data object Hidden : SpotifyFollowState
    data class Follow(val spotifyArtistId: String) : SpotifyFollowState
    data class Following(val spotifyArtistId: String) : SpotifyFollowState
    data object Busy : SpotifyFollowState
}

sealed interface ArtistBlockState {
    data object Loading : ArtistBlockState

    @Immutable
    data class Success(
        val isBlocked: Boolean,
    ) : ArtistBlockState

    data object Empty : ArtistBlockState

    @Immutable
    data class Error(
        @StringRes val messageRes: Int,
    ) : ArtistBlockState
}

sealed interface ArtistAction {
    data object Share : ArtistAction

    data object CopyLink : ArtistAction

    data object ToggleBlock : ArtistAction
}

sealed interface ArtistEvent {
    @Immutable
    data class Share(
        val link: String,
    ) : ArtistEvent

    @Immutable
    data class CopyLink(
        val link: String,
    ) : ArtistEvent

    @Immutable
    data class ShowMessage(
        @StringRes val messageRes: Int,
    ) : ArtistEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        observeArtistBlocked: ObserveArtistBlockedUseCase,
        private val setArtistBlocked: SetArtistBlockedUseCase,
        private val spotifyRepository: SpotifyLibraryRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val artistId = savedStateHandle.get<String>("artistId")!!
        var artistPage by mutableStateOf<ArtistPage?>(null)
        private val eventChannel = Channel<ArtistEvent>(capacity = Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()
        private var blockJob: Job? = null
        private var resolveSpotifyJob: Job? = null

        private val _spotifyFollowState = MutableStateFlow<SpotifyFollowState>(SpotifyFollowState.Hidden)
        val spotifyFollowState: StateFlow<SpotifyFollowState> = _spotifyFollowState.asStateFlow()

        val libraryArtist =
            database
                .artist(artistId)
                .stateIn(viewModelScope, SharingStarted.Lazily, null)
        val blockState =
            observeArtistBlocked(artistId)
                .map { blocked ->
                    if (blocked == null) {
                        ArtistBlockState.Empty
                    } else {
                        ArtistBlockState.Success(isBlocked = blocked)
                    }
                }.catch {
                    emit(ArtistBlockState.Error(R.string.error_unknown))
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtistBlockState.Loading)
        val librarySongs =
            context.dataStore.data
                .map { it[HideExplicitKey] ?: false }
                .distinctUntilChanged()
                .flatMapLatest { hideExplicit ->
                    database.artistSongsByCreateDateAsc(artistId).map { it.filterExplicit(hideExplicit) } // show all
                    // database.artistSongsPreview(artistId).map { it.filterExplicit(hideExplicit) } // only preview
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        val libraryAlbums =
            context.dataStore.data
                .map { it[HideExplicitKey] ?: false }
                .distinctUntilChanged()
                .flatMapLatest { hideExplicit ->
                    database.artistAlbumsPreview(artistId).map { it.filterExplicitAlbums(hideExplicit) }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        init {
            // Load artist page and reload when hide explicit setting changes
            viewModelScope.launch {
                context.dataStore.data
                    .map { it[HideExplicitKey] ?: false }
                    .distinctUntilChanged()
                    .collect {
                        fetchArtistsFromYTM()
                    }
            }

            // Observe Spotify follow settings and auth changes
            viewModelScope.launch {
                combine(
                    context.dataStore.data.map { it[ShowSpotifyFollowArtistKey] ?: false }.distinctUntilChanged(),
                    context.dataStore.data.map {
                        it[SpotifyAccessTokenKey].orEmpty().isNotBlank() || it[SpotifySpDcKey].orEmpty().isNotBlank()
                    }.distinctUntilChanged(),
                ) { show, connected -> show && connected }
                    .collect { enabled ->
                        if (!enabled) {
                            _spotifyFollowState.value = SpotifyFollowState.Hidden
                        } else {
                            artistPage?.let { resolveSpotifyFollow(it) }
                        }
                    }
            }
        }

        fun fetchArtistsFromYTM() {
            viewModelScope.launch {
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val blockedArtistIds = database.getBlockedArtistIds().toSet()
                YouTube
                    .artist(artistId)
                    .onSuccess { page ->
                        val filteredSections =
                            page.sections
                                .map { section ->
                                    section.copy(
                                        items =
                                            section.items
                                                .filterExplicit(hideExplicit)
                                                .filterBlockedArtists(blockedArtistIds),
                                    )
                                }

                        val updatedPage = page.copy(sections = filteredSections)
                        artistPage = updatedPage
                        resolveSpotifyFollow(updatedPage)

                        withContext(Dispatchers.IO) {
                            database.artist(artistId).firstOrNull()?.artist?.let { artistEntity ->
                                database.update(artistEntity, page)
                            }
                        }
                    }.onFailure {
                        reportException(it)
                    }
            }
        }

        private fun resolveSpotifyFollow(page: ArtistPage) {
            resolveSpotifyJob?.cancel()
            resolveSpotifyJob =
                viewModelScope.launch {
                    val prefs = context.dataStore.data.first()
                    val showFollowArtist = prefs[ShowSpotifyFollowArtistKey] ?: false
                    val token = prefs[SpotifyAccessTokenKey].orEmpty()
                    val spDc = prefs[SpotifySpDcKey].orEmpty()
                    val isSpotifyConnected = token.isNotBlank() || spDc.isNotBlank()

                    if (!showFollowArtist || !isSpotifyConnected) {
                        _spotifyFollowState.value = SpotifyFollowState.Hidden
                        return@launch
                    }

                    val artistName = page.artist.title
                    val songTitles =
                        page.sections
                            .flatMap { it.items }
                            .filterIsInstance<SongItem>()
                            .map { it.title }
                            .distinct()
                            .take(15)

                    if (artistName.isBlank() || songTitles.isEmpty()) {
                        _spotifyFollowState.value = SpotifyFollowState.Hidden
                        return@launch
                    }

                    try {
                        spotifyRepository.ensureAuthenticated()
                        val resolution =
                            SpotifyArtistResolver.resolveToSpotifyArtist(
                                channelId = artistId,
                                ytmName = artistName,
                                ytmTopSongTitles = songTitles,
                            )
                        when (resolution) {
                            is SpotifyArtistResolution.Matched -> {
                                _spotifyFollowState.value =
                                    if (resolution.saved) {
                                        SpotifyFollowState.Following(resolution.spotifyArtistId)
                                    } else {
                                        SpotifyFollowState.Follow(resolution.spotifyArtistId)
                                    }
                            }

                            SpotifyArtistResolution.NoMatch -> {
                                _spotifyFollowState.value = SpotifyFollowState.Hidden
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        _spotifyFollowState.value = SpotifyFollowState.Hidden
                    }
                }
        }

        fun followSpotifyArtist() {
            val currentState = _spotifyFollowState.value
            val targetArtistId =
                when (currentState) {
                    is SpotifyFollowState.Follow -> currentState.spotifyArtistId
                    else -> return
                }

            _spotifyFollowState.value = SpotifyFollowState.Following(targetArtistId)
            viewModelScope.launch {
                spotifyRepository
                    .followArtist(targetArtistId)
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        _spotifyFollowState.value = SpotifyFollowState.Follow(targetArtistId)
                        eventChannel.send(ArtistEvent.ShowMessage(R.string.spotify_follow_failed))
                    }
            }
        }

        fun unfollowSpotifyArtist() {
            val currentState = _spotifyFollowState.value
            val targetArtistId =
                when (currentState) {
                    is SpotifyFollowState.Following -> currentState.spotifyArtistId
                    else -> return
                }

            _spotifyFollowState.value = SpotifyFollowState.Follow(targetArtistId)
            viewModelScope.launch {
                spotifyRepository
                    .unfollowArtist(targetArtistId)
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        _spotifyFollowState.value = SpotifyFollowState.Following(targetArtistId)
                        eventChannel.send(ArtistEvent.ShowMessage(R.string.spotify_unfollow_failed))
                    }
            }
        }

        fun onAction(action: ArtistAction) {
            when (action) {
                ArtistAction.Share -> eventChannel.trySend(ArtistEvent.Share(artistShareLink()))
                ArtistAction.CopyLink -> eventChannel.trySend(ArtistEvent.CopyLink(artistShareLink()))
                ArtistAction.ToggleBlock -> toggleBlocked()
            }
        }

        private fun toggleBlocked() {
            if (blockJob?.isActive == true) return

            val pageArtist = artistPage?.artist
            val localArtist = libraryArtist.value?.artist
            val artistName = pageArtist?.title ?: localArtist?.name ?: return
            val currentlyBlocked = (blockState.value as? ArtistBlockState.Success)?.isBlocked == true

            blockJob =
                viewModelScope.launch {
                    try {
                        setArtistBlocked(
                            ArtistBlockRequest(
                                id = artistId,
                                name = artistName,
                                channelId = pageArtist?.channelId ?: localArtist?.channelId,
                                thumbnailUrl = pageArtist?.thumbnail ?: localArtist?.thumbnailUrl,
                                blocked = !currentlyBlocked,
                            ),
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (throwable: Throwable) {
                        reportException(throwable)
                        eventChannel.send(ArtistEvent.ShowMessage(R.string.error_unknown))
                    }
                }
        }

        private fun artistShareLink(): String = artistPage?.artist?.shareLink ?: "https://music.youtube.com/channel/$artistId"
    }




