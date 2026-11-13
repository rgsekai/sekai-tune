/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.player

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.innertube.models.CommentItem
import moe.rgsekai.sekaitune.ui.component.GlassModalBottomSheet
import moe.rgsekai.sekaitune.viewmodels.CommentsUiState
import moe.rgsekai.sekaitune.viewmodels.CommentsViewModel
import java.util.regex.Pattern

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    videoId: String,
    fallbackVideoId: String? = null,
    initialContinuationToken: String? = null,
    viewModel: CommentsViewModel,
    sheetState: SheetState = rememberModalBottomSheetState(),
    onDismiss: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val context = LocalContext.current

    LaunchedEffect(videoId, fallbackVideoId, initialContinuationToken) {
        viewModel.loadCommentsForVideo(
            primaryVideoId = videoId,
            fallbackVideoId = fallbackVideoId,
            initialContinuationToken = initialContinuationToken,
        )
    }

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(550.dp)
                    .padding(horizontal = 20.dp),
        ) {
            // Header Row: Title, Count, and "Add comment" external link
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.comments),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    uiState.headerCountText?.let { count ->
                        Text(
                            text = count,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Add comment opens in official YouTube Music app / browser
                OutlinedButton(
                    onClick = {
                        val uri = Uri.parse("https://music.youtube.com/watch?v=$videoId")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_comment),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.comments_add),
                        fontSize = 12.sp,
                    )
                }
            }

            // Sort Filter Chips (Top vs Newest)
            if (uiState.sortOptions.isNotEmpty() && !uiState.isDisabled && uiState.error == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.sortOptions.forEachIndexed { index, option ->
                        val selected = uiState.selectedSortIndex == index
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectSort(index) },
                            label = { Text(option.title, fontSize = 12.sp) },
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                        )
                    }
                }
            }

            // Main Content Area
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }

                    uiState.isDisabled -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_comment),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.comments_disabled),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    uiState.error != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.comments_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    viewModel.loadCommentsForVideo(
                                        primaryVideoId = videoId,
                                        fallbackVideoId = fallbackVideoId,
                                        initialContinuationToken = initialContinuationToken,
                                        forceRefresh = true,
                                    )
                                },
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }

                    uiState.comments.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.comments_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        CommentsList(
                            uiState = uiState,
                            onLoadMore = { viewModel.loadNextPage() },
                            onToggleReplies = { viewModel.toggleReplies(it) },
                            onTimestampClick = { seconds ->
                                playerConnection?.player?.seekTo(seconds * 1000L)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentsList(
    uiState: CommentsUiState,
    onLoadMore: () -> Unit,
    onToggleReplies: (CommentItem) -> Unit,
    onTimestampClick: (Long) -> Unit,
) {
    val listState = rememberLazyListState()

    // Trigger next page when reaching near end
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && uiState.continuationToken != null && !uiState.isLoadingMore) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(
            items = uiState.comments,
            key = { it.id },
        ) { comment ->
            CommentRow(
                comment = comment,
                isExpanded = comment.id in uiState.expandedRepliesCommentIds,
                isLoadingReplies = comment.id in uiState.loadingRepliesCommentIds,
                replies = uiState.repliesByCommentId[comment.id].orEmpty(),
                onToggleReplies = { onToggleReplies(comment) },
                onTimestampClick = onTimestampClick,
            )
        }

        if (uiState.isLoadingMore) {
            item(key = "loading_more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommentItem,
    isExpanded: Boolean,
    isLoadingReplies: Boolean,
    replies: List<CommentItem>,
    onToggleReplies: () -> Unit,
    onTimestampClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Author Avatar
        AsyncImage(
            model = comment.authorAvatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(if (comment.isReply) 28.dp else 36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        // Comment content
        Column(modifier = Modifier.weight(1f)) {
            // Author Name, Timestamp, Edited, Pinned
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = comment.timeAgo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (comment.isEdited) {
                    Text(
                        text = stringResource(R.string.comments_edited),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                if (comment.isPinned) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(start = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.comments_pinned),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Body text with clickable timestamps & links
            FormattedCommentText(
                rawText = comment.text,
                onTimestampClick = onTimestampClick,
            )

            Spacer(Modifier.height(6.dp))

            // Actions row: Likes (display only), Creator Heart, Replies button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Like count (display only)
                val likes = comment.likeCount
                if (!likes.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.favorite),
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        Text(
                            text = likes,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Hearted by creator marker
                if (comment.isHearted) {
                    Text(
                        text = "❤️",
                        fontSize = 12.sp,
                    )
                }

                // Reply count & toggle button (only on top-level comments)
                if (!comment.isReply && (comment.replyCount > 0 || comment.repliesContinuation != null)) {
                    Text(
                        text =
                            if (isExpanded) {
                                stringResource(R.string.comments_hide_replies)
                            } else {
                                stringResource(R.string.comments_view_replies, comment.replyCount)
                            },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onToggleReplies),
                    )
                }
            }

            // Indented Replies Section
            if (!comment.isReply && isExpanded) {
                Spacer(Modifier.height(8.dp))
                if (isLoadingReplies) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp).padding(4.dp))
                } else {
                    Column(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        replies.forEach { reply ->
                            CommentRow(
                                comment = reply,
                                isExpanded = false,
                                isLoadingReplies = false,
                                replies = emptyList(),
                                onToggleReplies = {},
                                onTimestampClick = onTimestampClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val TIMESTAMP_PATTERN = Pattern.compile("(\\b(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})\\b)")

@Composable
private fun FormattedCommentText(
    rawText: String,
    onTimestampClick: (Long) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val primaryColor = MaterialTheme.colorScheme.primary

    val annotatedString = remember(rawText, primaryColor) {
        buildAnnotatedString {
            val matcher = TIMESTAMP_PATTERN.matcher(rawText)
            var lastIndex = 0

            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()

                if (start > lastIndex) {
                    append(rawText.substring(lastIndex, start))
                }

                val timeStr = matcher.group()
                val parts = timeStr.split(":").mapNotNull { it.toIntOrNull() }
                val seconds = when (parts.size) {
                    2 -> parts[0] * 60L + parts[1]
                    3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
                    else -> 0L
                }

                pushStringAnnotation(tag = "TIMESTAMP", annotation = seconds.toString())
                pushStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold))
                append(timeStr)
                pop()
                pop()

                lastIndex = end
            }

            if (lastIndex < rawText.length) {
                append(rawText.substring(lastIndex))
            }
        }
    }

    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "TIMESTAMP", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    annotation.item.toLongOrNull()?.let(onTimestampClick)
                    return@ClickableText
                }

            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    uriHandler.openUri(annotation.item)
                }
        },
    )
}
