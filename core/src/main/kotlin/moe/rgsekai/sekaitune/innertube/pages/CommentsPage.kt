/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.innertube.pages

import moe.rgsekai.sekaitune.innertube.models.CommentItem
import moe.rgsekai.sekaitune.innertube.models.CommentRenderer
import moe.rgsekai.sekaitune.innertube.models.CommentThreadRenderer
import moe.rgsekai.sekaitune.innertube.models.CommentsHeaderRenderer
import moe.rgsekai.sekaitune.innertube.models.Run
import moe.rgsekai.sekaitune.innertube.models.getContinuation
import moe.rgsekai.sekaitune.innertube.models.response.CommentsResponse

data class CommentSortOption(
    val title: String,
    val selected: Boolean,
    val continuation: String,
)

data class CommentsPage(
    val headerCountText: String? = null,
    val sortOptions: List<CommentSortOption> = emptyList(),
    val comments: List<CommentItem> = emptyList(),
    val continuation: String? = null,
    val isCommentsDisabled: Boolean = false,
)

object CommentsPageParser {
    fun fromCommentsResponse(response: CommentsResponse): CommentsPage {
        val endpoints = response.onResponseReceivedEndpoints.orEmpty()
        val allItems = mutableListOf<CommentsResponse.ContinuationItem>()

        for (ep in endpoints) {
            ep.reloadContinuationItemsCommand?.continuationItems?.let(allItems::addAll)
            ep.appendContinuationItemsAction?.continuationItems?.let(allItems::addAll)
        }

        var headerCountText: String? = null
        val sortOptions = mutableListOf<CommentSortOption>()
        var isCommentsDisabled = false
        var continuation: String? = null

        val entityMutations = response.frameworkUpdates?.entityBatchUpdate?.mutations.orEmpty()
        val entityPayloadByCommentId = mutableMapOf<String, CommentsResponse.FrameworkUpdates.EntityBatchUpdate.Mutation.MutationPayload.CommentEntityPayload>()
        val entityPayloadByEntityKey = mutableMapOf<String, CommentsResponse.FrameworkUpdates.EntityBatchUpdate.Mutation.MutationPayload.CommentEntityPayload>()

        for (m in entityMutations) {
            val p = m.payload?.commentEntityPayload ?: continue
            val id = p.properties?.commentId
            if (!id.isNullOrBlank()) {
                entityPayloadByCommentId[id] = p
            }
            if (!m.entityKey.isNullOrBlank()) {
                entityPayloadByEntityKey[m.entityKey] = p
            }
        }

        val parsedComments = mutableListOf<CommentItem>()

        for (item in allItems) {
            item.commentsHeaderRenderer?.let { header ->
                if (header.commentsDisabledRenderer != null) {
                    isCommentsDisabled = true
                }
                header.countText?.runs?.firstOrNull()?.text?.let { headerCountText = it }
                header.sortMenu?.sortFilterSubMenuRenderer?.subMenuItems?.forEach { subItem ->
                    val title = subItem.title.orEmpty()
                    val token = subItem.continuation?.reloadContinuationData?.continuation
                        ?: subItem.continuation?.nextContinuationData?.continuation
                    if (title.isNotBlank() && !token.isNullOrBlank()) {
                        sortOptions.add(
                            CommentSortOption(
                                title = title,
                                selected = subItem.selected == true,
                                continuation = token,
                            ),
                        )
                    }
                }
            }

            item.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token?.let {
                continuation = it
            }

            item.commentThreadRenderer?.let { thread ->
                parseCommentThread(thread, entityPayloadByCommentId, entityPayloadByEntityKey)?.let {
                    parsedComments.add(it)
                }
            }
        }

        return CommentsPage(
            headerCountText = headerCountText,
            sortOptions = sortOptions,
            comments = parsedComments,
            continuation = continuation,
            isCommentsDisabled = isCommentsDisabled,
        )
    }

    private fun parseCommentThread(
        thread: CommentThreadRenderer,
        entitiesByCommentId: Map<String, CommentsResponse.FrameworkUpdates.EntityBatchUpdate.Mutation.MutationPayload.CommentEntityPayload>,
        entitiesByEntityKey: Map<String, CommentsResponse.FrameworkUpdates.EntityBatchUpdate.Mutation.MutationPayload.CommentEntityPayload>,
    ): CommentItem? {
        val repliesContinuation = thread.replies?.commentRepliesRenderer?.subThreads
            ?.mapNotNull { it.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token }
            ?.firstOrNull()

        // 1. Check traditional CommentRenderer
        val cr = thread.comment?.commentRenderer
        if (cr != null && !cr.commentId.isNullOrBlank()) {
            val text = cr.contentText?.runs?.joinToString("") { it.text }.orEmpty()
            val publishedRuns = cr.publishedTimeText?.runs.orEmpty()
            val timeText = publishedRuns.firstOrNull()?.text.orEmpty()
            val isEdited = publishedRuns.any { it.text.contains("edited", ignoreCase = true) }
            val isPinned = cr.pinnedCommentBadge != null || thread.renderingPriority == "RENDERING_PRIORITY_PINNED"
            val isHearted = cr.actionButtons?.commentActionButtonsRenderer?.creatorHeart?.creatorHeartRenderer?.isHearted == true

            return CommentItem(
                id = cr.commentId,
                authorName = cr.authorText?.simpleText.orEmpty(),
                authorAvatarUrl = cr.authorThumbnail?.thumbnails?.lastOrNull()?.url,
                authorChannelId = cr.authorEndpoint?.browseEndpoint?.browseId,
                text = text,
                textRuns = cr.contentText?.runs,
                timeAgo = timeText.replace("(edited)", "").trim(),
                likeCount = cr.voteCount?.simpleText,
                replyCount = cr.replyCount ?: 0,
                isPinned = isPinned,
                isHearted = isHearted,
                isEdited = isEdited,
                repliesContinuation = repliesContinuation,
            )
        }

        // 2. Check commentViewModel + frameworkUpdates entity payload
        val cvm = thread.commentViewModel?.commentViewModel ?: return null
        val entity = (cvm.commentId?.let { entitiesByCommentId[it] })
            ?: (cvm.commentKey?.let { entitiesByEntityKey[it] })
            ?: return null

        val commentId = entity.properties?.commentId ?: cvm.commentId ?: return null
        val rawTime = entity.properties?.publishedTime.orEmpty()
        val isEdited = rawTime.contains("edited", ignoreCase = true)
        val cleanTime = rawTime.replace("(edited)", "").trim()
        val isHearted = !entity.toolbar?.heartActiveTooltip.isNullOrBlank()
        val isPinned = thread.renderingPriority == "RENDERING_PRIORITY_PINNED"

        val authorName = entity.author?.displayName.orEmpty()
        val text = entity.properties?.content?.content.orEmpty()
        val repliesCount = entity.toolbar?.replyCount?.replace(",", "")?.toIntOrNull() ?: 0

        return CommentItem(
            id = commentId,
            authorName = authorName,
            authorAvatarUrl = entity.author?.avatarThumbnailUrl,
            authorChannelId = entity.author?.channelId,
            text = text,
            textRuns = listOf(Run(text = text, navigationEndpoint = null)),
            timeAgo = cleanTime,
            likeCount = entity.toolbar?.likeCountNotliked,
            replyCount = repliesCount,
            isPinned = isPinned,
            isHearted = isHearted,
            isEdited = isEdited,
            repliesContinuation = repliesContinuation,
        )
    }

    fun parseRepliesResponse(response: CommentsResponse): Pair<List<CommentItem>, String?> {
        val endpoints = response.onResponseReceivedEndpoints.orEmpty()
        val allItems = mutableListOf<CommentsResponse.ContinuationItem>()

        for (ep in endpoints) {
            ep.appendContinuationItemsAction?.continuationItems?.let(allItems::addAll)
            ep.reloadContinuationItemsCommand?.continuationItems?.let(allItems::addAll)
        }

        val entityMutations = response.frameworkUpdates?.entityBatchUpdate?.mutations.orEmpty()
        val entityPayloadByCommentId = mutableMapOf<String, CommentsResponse.FrameworkUpdates.EntityBatchUpdate.Mutation.MutationPayload.CommentEntityPayload>()
        val entityPayloadByEntityKey = mutableMapOf<String, CommentsResponse.FrameworkUpdates.EntityBatchUpdate.Mutation.MutationPayload.CommentEntityPayload>()

        for (m in entityMutations) {
            val p = m.payload?.commentEntityPayload ?: continue
            val id = p.properties?.commentId
            if (!id.isNullOrBlank()) {
                entityPayloadByCommentId[id] = p
            }
            if (!m.entityKey.isNullOrBlank()) {
                entityPayloadByEntityKey[m.entityKey] = p
            }
        }

        val replies = mutableListOf<CommentItem>()
        var nextContinuation: String? = null

        for (item in allItems) {
            item.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token?.let {
                nextContinuation = it
            }
            item.commentThreadRenderer?.let { thread ->
                parseCommentThread(thread, entityPayloadByCommentId, entityPayloadByEntityKey)?.let {
                    replies.add(it.copy(isReply = true))
                }
            }
        }

        return replies to nextContinuation
    }
}

