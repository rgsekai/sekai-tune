/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "webdav_song",
    indices = [
        Index(value = ["album"]),
        Index(value = ["artist"]),
    ],
)
data class WebDavSongEntity(
    @PrimaryKey val path: String,
    val serverId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long? = null,
    val etag: String? = null,
    val lastModified: Long? = null,
    val contentLength: Long = 0L,
    val artUrl: String? = null,
    val scannedAt: Long = System.currentTimeMillis(),
    val tagsRead: Boolean = false,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    @androidx.room.ColumnInfo(name = "art_checked", defaultValue = "0") val artChecked: Boolean = false,
    @androidx.room.ColumnInfo(name = "matched_catalog_id") val matchedCatalogId: String? = null,
)
