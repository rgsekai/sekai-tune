/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.widget

import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.Preferences

enum class WidgetShortcutType {
    LIKED_SONGS,
    PLAYLIST,
    SONG,
    EMPTY,
}

@Immutable
data class WidgetShortcutItem(
    val title: String,
    val subtitle: String = "",
    val artPathOrUrl: String? = null,
    val type: WidgetShortcutType = WidgetShortcutType.EMPTY,
    val targetId: String = "",
) {
    companion object {
        val Empty = WidgetShortcutItem(
            title = "",
            subtitle = "",
            artPathOrUrl = null,
            type = WidgetShortcutType.EMPTY,
            targetId = "",
        )
    }
}

@Immutable
data class WidgetQueueItem(
    val title: String,
    val artist: String,
    val artPathOrUrl: String?,
    val durationText: String,
    val mediaId: String,
)

@Immutable
data class WidgetShortcutsSnapshot(
    val shortcut1: WidgetShortcutItem,
    val shortcut2: WidgetShortcutItem,
    val shortcut3: WidgetShortcutItem,
    val shortcut4: WidgetShortcutItem,
    val shortcut5: WidgetShortcutItem = WidgetShortcutItem.Empty,
    val shortcut6: WidgetShortcutItem = WidgetShortcutItem.Empty,
    val shortcut7: WidgetShortcutItem = WidgetShortcutItem.Empty,
    val shortcut8: WidgetShortcutItem = WidgetShortcutItem.Empty,
    val queueItems: List<WidgetQueueItem>,
) {
    companion object {
        val Empty = WidgetShortcutsSnapshot(
            shortcut1 = WidgetShortcutItem(
                title = "Liked Songs",
                subtitle = "Playlist",
                type = WidgetShortcutType.LIKED_SONGS,
                targetId = "LP_LIKED",
            ),
            shortcut2 = WidgetShortcutItem.Empty,
            shortcut3 = WidgetShortcutItem.Empty,
            shortcut4 = WidgetShortcutItem.Empty,
            shortcut5 = WidgetShortcutItem.Empty,
            shortcut6 = WidgetShortcutItem.Empty,
            shortcut7 = WidgetShortcutItem.Empty,
            shortcut8 = WidgetShortcutItem.Empty,
            queueItems = emptyList(),
        )
    }
}

internal fun Preferences.toWidgetShortcutsSnapshot(): WidgetShortcutsSnapshot {
    fun readItem(
        titleKey: Preferences.Key<String>,
        subKey: Preferences.Key<String>,
        artKey: Preferences.Key<String>,
        typeKey: Preferences.Key<String>,
        idKey: Preferences.Key<String>,
        defaultType: WidgetShortcutType = WidgetShortcutType.EMPTY,
    ): WidgetShortcutItem {
        val typeStr = this[typeKey]
        val type = typeStr?.let { runCatching { WidgetShortcutType.valueOf(it) }.getOrNull() } ?: defaultType
        return WidgetShortcutItem(
            title = this[titleKey].orEmpty(),
            subtitle = this[subKey].orEmpty(),
            artPathOrUrl = this[artKey],
            type = type,
            targetId = this[idKey].orEmpty(),
        )
    }

    val sc1 = readItem(
        MusicWidgetKeys.SHORTCUT_1_TITLE,
        MusicWidgetKeys.SHORTCUT_1_SUBTITLE,
        MusicWidgetKeys.SHORTCUT_1_ART,
        MusicWidgetKeys.SHORTCUT_1_TYPE,
        MusicWidgetKeys.SHORTCUT_1_ID,
        defaultType = WidgetShortcutType.LIKED_SONGS,
    ).let {
        if (it.title.isBlank()) it.copy(title = "Liked Songs", subtitle = "Playlist", type = WidgetShortcutType.LIKED_SONGS, targetId = "LP_LIKED") else it
    }

    val sc2 = readItem(
        MusicWidgetKeys.SHORTCUT_2_TITLE,
        MusicWidgetKeys.SHORTCUT_2_SUBTITLE,
        MusicWidgetKeys.SHORTCUT_2_ART,
        MusicWidgetKeys.SHORTCUT_2_TYPE,
        MusicWidgetKeys.SHORTCUT_2_ID,
    )

    val sc3 = readItem(
        MusicWidgetKeys.SHORTCUT_3_TITLE,
        MusicWidgetKeys.SHORTCUT_3_SUBTITLE,
        MusicWidgetKeys.SHORTCUT_3_ART,
        MusicWidgetKeys.SHORTCUT_3_TYPE,
        MusicWidgetKeys.SHORTCUT_3_ID,
    )

    val sc4 = readItem(
        MusicWidgetKeys.SHORTCUT_4_TITLE,
        MusicWidgetKeys.SHORTCUT_4_SUBTITLE,
        MusicWidgetKeys.SHORTCUT_4_ART,
        MusicWidgetKeys.SHORTCUT_4_TYPE,
        MusicWidgetKeys.SHORTCUT_4_ID,
    )

    val sc5 = readItem(
        MusicWidgetKeys.SHORTCUT_5_TITLE,
        MusicWidgetKeys.SHORTCUT_5_SUBTITLE,
        MusicWidgetKeys.SHORTCUT_5_ART,
        MusicWidgetKeys.SHORTCUT_5_TYPE,
        MusicWidgetKeys.SHORTCUT_5_ID,
    )

    val sc6 = readItem(
        MusicWidgetKeys.SHORTCUT_6_TITLE,
        MusicWidgetKeys.SHORTCUT_6_SUBTITLE,
        MusicWidgetKeys.SHORTCUT_6_ART,
        MusicWidgetKeys.SHORTCUT_6_TYPE,
        MusicWidgetKeys.SHORTCUT_6_ID,
    )

    val sc7 = readItem(
        MusicWidgetKeys.SHORTCUT_7_TITLE,
        MusicWidgetKeys.SHORTCUT_7_SUBTITLE,
        MusicWidgetKeys.SHORTCUT_7_ART,
        MusicWidgetKeys.SHORTCUT_7_TYPE,
        MusicWidgetKeys.SHORTCUT_7_ID,
    )

    val sc8 = readItem(
        MusicWidgetKeys.SHORTCUT_8_TITLE,
        MusicWidgetKeys.SHORTCUT_8_SUBTITLE,
        MusicWidgetKeys.SHORTCUT_8_ART,
        MusicWidgetKeys.SHORTCUT_8_TYPE,
        MusicWidgetKeys.SHORTCUT_8_ID,
    )

    val rawQueue = this[MusicWidgetKeys.QUEUE_ITEMS].orEmpty()
    val queueItems = parseWidgetQueueItems(rawQueue)

    return WidgetShortcutsSnapshot(
        shortcut1 = sc1,
        shortcut2 = sc2,
        shortcut3 = sc3,
        shortcut4 = sc4,
        shortcut5 = sc5,
        shortcut6 = sc6,
        shortcut7 = sc7,
        shortcut8 = sc8,
        queueItems = queueItems,
    )
}

internal fun serializeWidgetQueueItems(items: List<WidgetQueueItem>): String {
    return items.joinToString("\u001E") { item ->
        listOf(
            item.title.replace("\u001F", " "),
            item.artist.replace("\u001F", " "),
            item.artPathOrUrl.orEmpty().replace("\u001F", " "),
            item.durationText.replace("\u001F", " "),
            item.mediaId.replace("\u001F", " "),
        ).joinToString("\u001F")
    }
}

internal fun parseWidgetQueueItems(raw: String): List<WidgetQueueItem> {
    if (raw.isBlank()) return emptyList()
    return raw.split("\u001E").mapNotNull { entry ->
        val parts = entry.split("\u001F")
        if (parts.size >= 5) {
            WidgetQueueItem(
                title = parts[0],
                artist = parts[1],
                artPathOrUrl = parts[2].ifBlank { null },
                durationText = parts[3],
                mediaId = parts[4],
            )
        } else {
            null
        }
    }
}
