/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.viewmodels

import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.innertube.models.CommentItem
import moe.rgsekai.sekaitune.innertube.models.WatchEndpoint
import moe.rgsekai.sekaitune.innertube.pages.CommentSortOption
import moe.rgsekai.sekaitune.innertube.pages.CommentsPage
import javax.inject.Inject

data class CommentsUiState(
    val currentVideoId: String? = null,
    val headerCountText: String? = null,
    val sortOptions: List<CommentSortOption> = emptyList(),
    val selectedSortIndex: Int = 0,
    val comments: List<CommentItem> = emptyList(),
    val repliesByCommentId: Map<String, List<CommentItem>> = emptyMap(),
    val expandedRepliesCommentIds: Set<String> = emptySet(),
    val loadingRepliesCommentIds: Set<String> = emptySet(),
    val continuationToken: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isDisabled: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CommentsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    // In-memory LRU cache for 10 videos (videoId -> CommentsPage)
    private val memoryCache = object : LruCache<String, CommentsPage>(10) {}

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    fun loadCommentsForVideo(
        primaryVideoId: String,
        fallbackVideoId: String? = null,
        initialContinuationToken: String? = null,
        forceRefresh: Boolean = false,
    ) {
        if (!forceRefresh && _uiState.value.currentVideoId == primaryVideoId && (_uiState.value.comments.isNotEmpty() || _uiState.value.isDisabled)) {
            return
        }

        loadJob?.cancel()

        // Check in-memory cache first for instant reopen
        val cached = if (!forceRefresh) memoryCache.get(primaryVideoId) else null
        if (cached != null) {
            _uiState.update {
                it.copy(
                    currentVideoId = primaryVideoId,
                    headerCountText = cached.headerCountText,
                    sortOptions = cached.sortOptions,
                    selectedSortIndex = cached.sortOptions.indexOfFirst { opt -> opt.selected }.coerceAtLeast(0),
                    comments = cached.comments,
                    repliesByCommentId = emptyMap(),
                    expandedRepliesCommentIds = emptySet(),
                    loadingRepliesCommentIds = emptySet(),
                    continuationToken = cached.continuation,
                    isLoading = false,
                    isLoadingMore = false,
                    isDisabled = cached.isCommentsDisabled,
                    error = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                currentVideoId = primaryVideoId,
                isLoading = true,
                error = null,
                isDisabled = false,
                comments = emptyList(),
                repliesByCommentId = emptyMap(),
                expandedRepliesCommentIds = emptySet(),
                loadingRepliesCommentIds = emptySet(),
            )
        }

        loadJob = viewModelScope.launch {
            try {
                // If we don't have a reload continuation token for this video, query next to get it
                val reloadToken = initialContinuationToken ?: resolveReloadToken(primaryVideoId)

                if (reloadToken != null) {
                    val commentsResult = YouTube.getComments(reloadToken).getOrNull()
                    if (commentsResult != null && !commentsResult.isCommentsDisabled) {
                        applyLoadedComments(primaryVideoId, commentsResult)
                        return@launch
                    }
                }

                // If primary video comments are disabled or missing, try fallback video if available and different
                if (!fallbackVideoId.isNullOrBlank() && fallbackVideoId != primaryVideoId) {
                    val fallbackToken = resolveReloadToken(fallbackVideoId)
                    if (fallbackToken != null) {
                        val fallbackResult = YouTube.getComments(fallbackToken).getOrNull()
                        if (fallbackResult != null && !fallbackResult.isCommentsDisabled) {
                            applyLoadedComments(primaryVideoId, fallbackResult)
                            return@launch
                        }
                    }
                }

                // If both attempts returned disabled or no comments
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isDisabled = true,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage ?: "Failed to load comments",
                    )
                }
            }
        }
    }

    private suspend fun resolveReloadToken(videoId: String): String? {
        val nextResult = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()
        return nextResult?.commentsContinuation
    }

    private fun applyLoadedComments(videoId: String, page: CommentsPage) {
        memoryCache.put(videoId, page)
        _uiState.update {
            it.copy(
                headerCountText = page.headerCountText,
                sortOptions = page.sortOptions,
                selectedSortIndex = page.sortOptions.indexOfFirst { opt -> opt.selected }.coerceAtLeast(0),
                comments = page.comments,
                continuationToken = page.continuation,
                isLoading = false,
                isDisabled = page.isCommentsDisabled,
                error = null,
            )
        }
    }

    fun selectSort(optionIndex: Int) {
        val state = _uiState.value
        val option = state.sortOptions.getOrNull(optionIndex) ?: return
        if (state.selectedSortIndex == optionIndex && state.comments.isNotEmpty()) return

        loadJob?.cancel()
        _uiState.update {
            it.copy(
                selectedSortIndex = optionIndex,
                isLoading = true,
                error = null,
                comments = emptyList(),
                repliesByCommentId = emptyMap(),
                expandedRepliesCommentIds = emptySet(),
            )
        }

        loadJob = viewModelScope.launch {
            try {
                val page = YouTube.getComments(option.continuation).getOrThrow()
                _uiState.update {
                    it.copy(
                        headerCountText = page.headerCountText ?: it.headerCountText,
                        comments = page.comments,
                        continuationToken = page.continuation,
                        isLoading = false,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage ?: "Failed to sort comments",
                    )
                }
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        val token = state.continuationToken ?: return
        if (state.isLoading || state.isLoadingMore) return

        loadMoreJob?.cancel()
        _uiState.update { it.copy(isLoadingMore = true) }

        loadMoreJob = viewModelScope.launch {
            try {
                val page = YouTube.getComments(token).getOrThrow()
                val existingIds = _uiState.value.comments.map { it.id }.toSet()
                val newComments = page.comments.filter { it.id !in existingIds }

                _uiState.update { current ->
                    current.copy(
                        comments = current.comments + newComments,
                        continuationToken = page.continuation,
                        isLoadingMore = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun toggleReplies(comment: CommentItem) {
        val commentId = comment.id
        val isExpanded = commentId in _uiState.value.expandedRepliesCommentIds

        if (isExpanded) {
            _uiState.update {
                it.copy(expandedRepliesCommentIds = it.expandedRepliesCommentIds - commentId)
            }
            return
        }

        // Check if we already loaded the replies
        if (_uiState.value.repliesByCommentId.containsKey(commentId)) {
            _uiState.update {
                it.copy(expandedRepliesCommentIds = it.expandedRepliesCommentIds + commentId)
            }
            return
        }

        val token = comment.repliesContinuation ?: return
        _uiState.update {
            it.copy(loadingRepliesCommentIds = it.loadingRepliesCommentIds + commentId)
        }

        viewModelScope.launch {
            try {
                val (replies, _) = YouTube.getCommentReplies(token).getOrThrow()
                _uiState.update { current ->
                    current.copy(
                        loadingRepliesCommentIds = current.loadingRepliesCommentIds - commentId,
                        expandedRepliesCommentIds = current.expandedRepliesCommentIds + commentId,
                        repliesByCommentId = current.repliesByCommentId + (commentId to replies),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loadingRepliesCommentIds = it.loadingRepliesCommentIds - commentId)
                }
            }
        }
    }
}

