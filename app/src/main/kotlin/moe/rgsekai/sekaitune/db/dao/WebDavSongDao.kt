/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import moe.rgsekai.sekaitune.db.entities.WebDavSongEntity

@Dao
interface WebDavSongDao {

    @Upsert
    suspend fun upsert(song: WebDavSongEntity)

    @Upsert
    suspend fun upsertAll(songs: List<WebDavSongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<WebDavSongEntity>)

    @Query("DELETE FROM webdav_song WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)

    @Query("SELECT * FROM webdav_song ORDER BY title COLLATE NOCASE ASC")
    fun getAll(): Flow<List<WebDavSongEntity>>

    @Query("SELECT * FROM webdav_song WHERE album = :album ORDER BY title COLLATE NOCASE ASC")
    fun getByAlbum(album: String): Flow<List<WebDavSongEntity>>

    @Query("SELECT * FROM webdav_song WHERE artist = :artist ORDER BY title COLLATE NOCASE ASC")
    fun getByArtist(artist: String): Flow<List<WebDavSongEntity>>

    @Query("SELECT COUNT(*) FROM webdav_song")
    fun count(): Flow<Int>

    @Query("SELECT artUrl FROM webdav_song WHERE artUrl IS NOT NULL AND artUrl != '' LIMIT 1")
    fun getFirstArtUrl(): Flow<String?>

    @Query("DELETE FROM webdav_song")
    suspend fun deleteAll()

    @Query("SELECT * FROM webdav_song")
    suspend fun getAllSnapshot(): List<WebDavSongEntity>

    @Query("SELECT * FROM webdav_song WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): WebDavSongEntity?

    @Query("UPDATE webdav_song SET durationMs = :durationMs WHERE path = :path AND durationMs IS NULL")
    suspend fun updateDurationIfNull(path: String, durationMs: Long): Int

    @Query("SELECT * FROM webdav_song WHERE tagsRead = 0 OR (art_checked = 0 AND (artUrl IS NULL OR artUrl = '')) LIMIT :limit")
    suspend fun getSongsWithoutTags(limit: Int = 200): List<WebDavSongEntity>

    @Query("UPDATE webdav_song SET title = :title, artist = :artist, album = :album, albumArtist = :albumArtist, trackNumber = :trackNumber, discNumber = :discNumber, artUrl = COALESCE(:artUrl, artUrl), tagsRead = 1, art_checked = 1 WHERE path = :path")
    suspend fun updateTags(
        path: String,
        title: String,
        artist: String,
        album: String,
        albumArtist: String?,
        trackNumber: Int?,
        discNumber: Int?,
        artUrl: String?,
    )

    @Query("UPDATE webdav_song SET tagsRead = 1, art_checked = 1 WHERE path = :path")
    suspend fun markTagsRead(path: String)

    @Query("UPDATE webdav_song SET art_checked = 0 WHERE artUrl IS NULL OR artUrl = ''")
    suspend fun resetUncheckedArt()

    @Query("UPDATE webdav_song SET matched_catalog_id = :catalogId WHERE path = :path")
    suspend fun updateMatchedCatalogId(path: String, catalogId: String?)

    @Query("UPDATE webdav_song SET matched_catalog_id = NULL WHERE matched_catalog_id = :sentinel")
    suspend fun clearFailedMatches(sentinel: String = "__NO_MATCH__")

    @Query("SELECT * FROM webdav_song WHERE tagsRead = 1 AND durationMs IS NOT NULL")
    suspend fun getCandidatesForMatching(): List<WebDavSongEntity>

    @Query("SELECT * FROM webdav_song WHERE tagsRead = 1 AND durationMs BETWEEN :minDurationMs AND :maxDurationMs")
    suspend fun getCandidatesByDuration(minDurationMs: Long, maxDurationMs: Long): List<WebDavSongEntity>

    @Query("SELECT * FROM webdav_song WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE ASC LIMIT :limit")
    fun search(query: String, limit: Int = 20): Flow<List<WebDavSongEntity>>
}
