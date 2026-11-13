/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.innertube.models

import kotlinx.serialization.Serializable
import moe.rgsekai.sekaitune.innertube.models.response.CommentsResponse

@Serializable
data class CommentsHeaderRenderer(
    val countText: Runs? = null,
    val titleText: Runs? = null,
    val sortMenu: SortMenu? = null,
    val commentsDisabledRenderer: CommentsDisabledRenderer? = null,
) {
    @Serializable
    data class SortMenu(
        val sortFilterSubMenuRenderer: SortFilterSubMenuRenderer? = null,
    ) {
        @Serializable
        data class SortFilterSubMenuRenderer(
            val subMenuItems: List<SubMenuItem>? = null,
        ) {
            @Serializable
            data class SubMenuItem(
                val title: String? = null,
                val selected: Boolean? = null,
                val continuation: Continuation? = null,
                val subtitle: String? = null,
            )
        }
    }

    @Serializable
    data class CommentsDisabledRenderer(
        val message: Runs? = null,
    )
}

@Serializable
data class CommentThreadRenderer(
    val comment: CommentWrapper? = null,
    val commentViewModel: CommentViewModelWrapper? = null,
    val replies: CommentReplies? = null,
    val renderingPriority: String? = null,
) {
    @Serializable
    data class CommentWrapper(
        val commentRenderer: CommentRenderer? = null,
    )

    @Serializable
    data class CommentViewModelWrapper(
        val commentViewModel: InnerCommentViewModel? = null,
    ) {
        @Serializable
        data class InnerCommentViewModel(
            val commentKey: String? = null,
            val commentId: String? = null,
            val sharedKey: String? = null,
            val toolbarStateKey: String? = null,
        )
    }

    @Serializable
    data class CommentReplies(
        val commentRepliesRenderer: CommentRepliesRenderer? = null,
    ) {
        @Serializable
        data class CommentRepliesRenderer(
            val targetId: String? = null,
            val subThreads: List<CommentsResponse.ContinuationItem>? = null,
        )
    }
}

@Serializable
data class CommentRenderer(
    val commentId: String? = null,
    val authorText: SimpleText? = null,
    val authorThumbnail: Thumbnails? = null,
    val authorEndpoint: NavigationEndpoint? = null,
    val contentText: Runs? = null,
    val publishedTimeText: Runs? = null,
    val voteCount: SimpleText? = null,
    val replyCount: Int? = null,
    val isLiked: Boolean? = null,
    val pinnedCommentBadge: PinnedCommentBadge? = null,
    val actionButtons: CommentActionButtons? = null,
) {
    @Serializable
    data class SimpleText(
        val simpleText: String? = null,
    )

    @Serializable
    data class Thumbnails(
        val thumbnails: List<Thumbnail>? = null,
    )

    @Serializable
    data class Thumbnail(
        val url: String,
        val width: Int? = null,
        val height: Int? = null,
    )

    @Serializable
    data class PinnedCommentBadge(
        val pinnedCommentBadgeRenderer: PinnedCommentBadgeRenderer? = null,
    ) {
        @Serializable
        data class PinnedCommentBadgeRenderer(
            val label: Runs? = null,
        )
    }

    @Serializable
    data class CommentActionButtons(
        val commentActionButtonsRenderer: CommentActionButtonsRenderer? = null,
    ) {
        @Serializable
        data class CommentActionButtonsRenderer(
            val creatorHeart: CreatorHeart? = null,
        ) {
            @Serializable
            data class CreatorHeart(
                val creatorHeartRenderer: CreatorHeartRenderer? = null,
            ) {
                @Serializable
                data class CreatorHeartRenderer(
                    val creatorThumbnail: Thumbnails? = null,
                    val isHearted: Boolean? = null,
                )
            }
        }
    }
}

data class CommentItem(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val authorChannelId: String?,
    val text: String,
    val textRuns: List<Run>? = null,
    val timeAgo: String,
    val likeCount: String?,
    val replyCount: Int,
    val isPinned: Boolean,
    val isHearted: Boolean,
    val isEdited: Boolean,
    val repliesContinuation: String? = null,
    val isReply: Boolean = false,
)

