/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.constants.LyricsRomanizeChineseKey
import moe.rgsekai.sekaitune.constants.LyricsRomanizeHindiKey
import moe.rgsekai.sekaitune.constants.LyricsRomanizeJapaneseKey
import moe.rgsekai.sekaitune.constants.LyricsRomanizeKoreanKey
import moe.rgsekai.sekaitune.constants.LyricsRomanizeOtherLanguagesKey
import moe.rgsekai.sekaitune.db.entities.LyricsEntity
import moe.rgsekai.sekaitune.lyrics.LyricsEntry
import moe.rgsekai.sekaitune.lyrics.LyricsRomanizationPreferences
import moe.rgsekai.sekaitune.lyrics.LyricsUtils
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.utils.rememberPreference
import moe.rgsekai.sekaitune.utils.reportException

@Composable
fun OneLineLyrics(
    mediaMetadata: MediaMetadata?,
    position: Long,
    sliderPosition: Long?,
    lyricsSyncOffset: Int,
    showOneLineLyrics: Boolean,
    onShowLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    textAlign: TextAlign = TextAlign.Start,
) {
    if (!showOneLineLyrics || mediaMetadata == null) return

    val playerConnection = LocalPlayerConnection.current ?: return
    val currentLyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)

    val lyrics =
        remember(currentLyricsEntity, mediaMetadata.id) {
            currentLyricsEntity
                ?.takeIf { it.id == mediaMetadata.id }
                ?.lyrics
        }

    val isSynced =
        remember(lyrics) {
            lyrics != null &&
                lyrics != LyricsEntity.LYRICS_NOT_FOUND &&
                (LyricsUtils.isLineSyncedLrc(lyrics) || LyricsUtils.isTtml(lyrics))
        }

    if (!isSynced || lyrics == null) return

    val isTtmlLyrics = remember(lyrics) { LyricsUtils.isTtml(lyrics) }
    val leadMs = if (isTtmlLyrics) 0L else 300L

    val (romanizeChinese) = rememberPreference(LyricsRomanizeChineseKey, defaultValue = true)
    val (romanizeHindi) = rememberPreference(LyricsRomanizeHindiKey, defaultValue = true)
    val (romanizeJapanese) = rememberPreference(LyricsRomanizeJapaneseKey, defaultValue = true)
    val (romanizeKorean) = rememberPreference(LyricsRomanizeKoreanKey, defaultValue = true)
    val (romanizeOtherLanguages) = rememberPreference(LyricsRomanizeOtherLanguagesKey, defaultValue = true)

    val romanizationPreferences =
        remember(
            romanizeJapanese,
            romanizeKorean,
            romanizeChinese,
            romanizeHindi,
            romanizeOtherLanguages,
        ) {
            LyricsRomanizationPreferences(
                romanizeJapanese = romanizeJapanese,
                romanizeKorean = romanizeKorean,
                romanizeChinese = romanizeChinese,
                romanizeHindi = romanizeHindi,
                romanizeOther = romanizeOtherLanguages,
            )
        }

    val lines: List<LyricsEntry> =
        remember(lyrics, mediaMetadata.duration) {
            when {
                LyricsUtils.isTtml(lyrics) -> {
                    listOf(LyricsEntry.HEAD_LYRICS_ENTRY) +
                        LyricsUtils.parseTtml(lyrics, mediaMetadata.duration)
                }

                LyricsUtils.isLineSyncedLrc(lyrics) -> {
                    listOf(LyricsEntry.HEAD_LYRICS_ENTRY) +
                        LyricsUtils.parseLyrics(lyrics)
                }

                else -> emptyList()
            }
        }

    val romanizedLines = remember(lines, romanizationPreferences) {
        mutableStateMapOf<Int, String>()
    }

    LaunchedEffect(lines, romanizationPreferences) {
        romanizedLines.clear()
        if (!romanizationPreferences.isEnabled) return@LaunchedEffect

        val toRomanize: List<Pair<Int, LyricsEntry>> =
            lines.mapIndexedNotNull { index, entry ->
                if (entry == LyricsEntry.HEAD_LYRICS_ENTRY) return@mapIndexedNotNull null
                val hasProviderRomanization =
                    LyricsUtils.providedRomanizedTextForEntry(entry, romanizationPreferences) != null
                if (hasProviderRomanization || LyricsUtils.shouldRomanizeLyricsLine(entry.text, romanizationPreferences)) {
                    Pair(index, entry)
                } else {
                    null
                }
            }
        if (toRomanize.isEmpty()) return@LaunchedEffect

        coroutineScope {
            val jobs =
                toRomanize.map { pair ->
                    val index = pair.first
                    val entry = pair.second
                    async {
                        val romanizedText: String? =
                            try {
                                LyricsUtils.providedRomanizedTextForEntry(entry, romanizationPreferences)
                                    ?: LyricsUtils.romanizeLyricsLine(entry.text, romanizationPreferences)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                reportException(e)
                                null
                            }
                        Pair(index, romanizedText)
                    }
                }
            jobs.awaitAll().forEach { (index, text) ->
                if (!text.isNullOrBlank()) {
                    romanizedLines[index] = text
                }
            }
        }
    }

    val currentLineIndex by remember(lines, lyricsSyncOffset, leadMs) {
        derivedStateOf {
            if (lines.isEmpty()) -1
            else {
                val effectivePosition = (sliderPosition ?: position) + lyricsSyncOffset.toLong()
                LyricsUtils.findCurrentLineIndex(lines, effectivePosition, leadMs)
            }
        }
    }

    val currentLineText =
        remember(lines, currentLineIndex, romanizedLines.toMap()) {
            if (currentLineIndex < 0 || currentLineIndex >= lines.size) {
                null
            } else {
                val entry = lines[currentLineIndex]
                if (entry == LyricsEntry.HEAD_LYRICS_ENTRY) {
                    null
                } else {
                    val romanized = romanizedLines[currentLineIndex]
                    val rawText = if (!romanized.isNullOrBlank()) romanized else entry.text
                    rawText.trim().takeIf { it.isNotBlank() }
                }
            }
        }

    val hasActiveLine = currentLineText != null

    AnimatedVisibility(
        visible = hasActiveLine,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(200)),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onShowLyrics,
                    ),
            contentAlignment = when (textAlign) {
                TextAlign.Center -> Alignment.Center
                TextAlign.End -> Alignment.CenterEnd
                else -> Alignment.CenterStart
            },
        ) {
            AnimatedContent(
                targetState = currentLineText.orEmpty(),
                transitionSpec = {
                    val enter = slideInVertically(animationSpec = tween(250)) { it / 3 } + fadeIn(animationSpec = tween(250))
                    val exit = slideOutVertically(animationSpec = tween(250)) { -it / 3 } + fadeOut(animationSpec = tween(200))
                    enter togetherWith exit
                },
                label = "oneLineLyricsTransition",
            ) { targetText ->
                Text(
                    text = targetText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.85f),
                            offset = Offset(0f, 2f),
                            blurRadius = 8f,
                        ),
                    ),
                    color = textColor,
                    textAlign = textAlign,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
