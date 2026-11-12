/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.models

import androidx.compose.runtime.Immutable
import moe.rgsekai.sekaitune.db.entities.Song
import moe.rgsekai.sekaitune.innertube.models.AlbumItem

@Immutable
sealed interface HeroCardItem {
    val id: String
    val title: String
    val artistNames: String
    val thumbnailUrl: String?

    data class QuickPick(
        val song: Song,
    ) : HeroCardItem {
        override val id: String get() = song.id
        override val title: String get() = song.song.title
        override val artistNames: String get() = song.artists.joinToString { it.name }
        override val thumbnailUrl: String? get() = song.song.thumbnailUrl
    }

    data class NewRelease(
        val album: AlbumItem,
    ) : HeroCardItem {
        override val id: String get() = album.id
        override val title: String get() = album.title
        override val artistNames: String get() = album.artists.orEmpty().joinToString { it.name }
        override val thumbnailUrl: String? get() = album.thumbnail
    }
}

