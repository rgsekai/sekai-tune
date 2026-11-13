/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.innertube.models.response

import kotlinx.serialization.Serializable
import moe.rgsekai.sekaitune.innertube.models.CommentsHeaderRenderer
import moe.rgsekai.sekaitune.innertube.models.CommentThreadRenderer
import moe.rgsekai.sekaitune.innertube.models.ContinuationItemRenderer

@Serializable
data class CommentsResponse(
    val onResponseReceivedEndpoints: List<OnResponseReceivedEndpoint>? = null,
    val frameworkUpdates: FrameworkUpdates? = null,
) {
    @Serializable
    data class OnResponseReceivedEndpoint(
        val reloadContinuationItemsCommand: ContinuationItemsCommand? = null,
        val appendContinuationItemsAction: ContinuationItemsCommand? = null,
    ) {
        @Serializable
        data class ContinuationItemsCommand(
            val targetId: String? = null,
            val continuationItems: List<ContinuationItem>? = null,
        )
    }

    @Serializable
    data class ContinuationItem(
        val commentsHeaderRenderer: CommentsHeaderRenderer? = null,
        val commentThreadRenderer: CommentThreadRenderer? = null,
        val continuationItemRenderer: ContinuationItemRenderer? = null,
    )

    @Serializable
    data class FrameworkUpdates(
        val entityBatchUpdate: EntityBatchUpdate? = null,
    ) {
        @Serializable
        data class EntityBatchUpdate(
            val mutations: List<Mutation>? = null,
        ) {
            @Serializable
            data class Mutation(
                val entityKey: String? = null,
                val payload: MutationPayload? = null,
            ) {
                @Serializable
                data class MutationPayload(
                    val commentEntityPayload: CommentEntityPayload? = null,
                ) {
                    @Serializable
                    data class CommentEntityPayload(
                        val properties: CommentProperties? = null,
                        val author: CommentAuthor? = null,
                        val toolbar: CommentToolbar? = null,
                    ) {
                        @Serializable
                        data class CommentProperties(
                            val commentId: String? = null,
                            val content: CommentContent? = null,
                            val publishedTime: String? = null,
                            val replyLevel: Int? = null,
                        ) {
                            @Serializable
                            data class CommentContent(
                                val content: String? = null,
                            )
                        }

                        @Serializable
                        data class CommentAuthor(
                            val channelId: String? = null,
                            val displayName: String? = null,
                            val avatarThumbnailUrl: String? = null,
                            val isVerified: Boolean? = null,
                            val isCreator: Boolean? = null,
                            val isArtist: Boolean? = null,
                        )

                        @Serializable
                        data class CommentToolbar(
                            val likeCountLiked: String? = null,
                            val likeCountNotliked: String? = null,
                            val replyCount: String? = null,
                            val heartActiveTooltip: String? = null,
                        )
                    }
                }
            }
        }
    }
}

