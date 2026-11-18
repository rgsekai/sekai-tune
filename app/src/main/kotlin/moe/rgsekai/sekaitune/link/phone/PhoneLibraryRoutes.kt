// PhoneLibraryRoutes.kt

package moe.rgsekai.sekaitune.link.phone

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.offline.Download
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.link.LinkRequest
import moe.rgsekai.sekaitune.link.LinkResponse
import moe.rgsekai.sekaitune.link.LinkRows
import moe.rgsekai.sekaitune.playback.DownloadUtil
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.zip.GZIPOutputStream

// what a paired tv can ask the phone's library for: a copy of it, the song files it keeps, and the plays and likes made on the tv
internal class PhoneLibraryRoutes(
    private val context: Context,
    private val database: MusicDatabase,
    private val downloadUtil: DownloadUtil,
) {
    // the whole library database, copied under its lock and squeezed on the way out
    fun snapshot(): LinkResponse {
        val work = File(context.cacheDir, "link_snapshot_${System.nanoTime()}.db")
        val live = database.openHelper.writableDatabase
        snapshotDatabase(File(requireNotNull(live.path)), work, begin = live::beginTransaction, end = live::endTransaction)
        // sign-in state and the phone's own searches stay on the phone
        SQLiteDatabase.openDatabase(work.path, null, SQLiteDatabase.OPEN_READWRITE).use { copy ->
            runCatching { copy.execSQL("DELETE FROM search_history") }
            runCatching { copy.execSQL("VACUUM") }
        }
        return LinkResponse.stream(SnapshotType) { out ->
            try {
                GZIPOutputStream(out, 64 * 1024).use { zip -> work.inputStream().use { it.copyTo(zip, 64 * 1024) } }
            } finally {
                work.delete()
            }
        }
    }

    // every song the phone can play without the internet: its finished downloads and its own song files
    fun files(): JSONArray {
        val out = JSONArray()
        val cache = downloadUtil.downloadCache
        val formats = mimeTypes()
        downloadUtil.downloads.value.values
            .filter { it.state == Download.STATE_COMPLETED }
            .forEach { download ->
                val id = download.request.id
                val length = ContentMetadata.getContentLength(cache.getContentMetadata(id))
                if (length <= 0 || !cache.isCached(id, 0, length)) return@forEach
                out.put(JSONObject().put("id", id).put("bytes", length).put("mime", formats[id] ?: "audio/mp4").put("local", false))
            }
        localFiles().forEach { (id, uri) ->
            val length = lengthOf(uri) ?: return@forEach
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "audio/mpeg"
            out.put(JSONObject().put("id", id).put("bytes", length).put("mime", mime).put("local", true))
        }
        return out
    }

    // one song's bytes, in whatever range the tv's player asks for
    fun file(request: LinkRequest, songId: String): LinkResponse {
        localFiles()[songId]?.let { uri ->
            val length = lengthOf(uri) ?: return LinkResponse.status(404)
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "audio/mpeg"
            return LinkResponse.ranged(request, length, mime) { start, count, out -> copyLocal(uri, start, count, out) }
        }
        val cache = downloadUtil.downloadCache
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(songId))
        if (length <= 0 || !cache.isCached(songId, 0, length)) return LinkResponse.status(404)
        val mime = mimeTypes()[songId] ?: "audio/mp4"
        return LinkResponse.ranged(request, length, mime) { start, count, out ->
            // no upstream: only what's already downloaded is read, nothing goes to youtube from here
            val source = CacheDataSource(cache, null)
            try {
                source.open(DataSpec.Builder().setUri(Uri.parse(songId)).setKey(songId).setPosition(start).setLength(count).build())
                val buffer = ByteArray(64 * 1024)
                var left = count
                while (left > 0) {
                    val read = source.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    left -= read
                }
            } finally {
                source.close()
            }
        }
    }

    // plays, likes and library adds made on the tv, folded into this phone's library so they count here too
    fun applyChanges(changes: JSONObject) {
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            // the songs the tv talks about, so a play of something new to this phone has a row to point at
            val rows = changes.optJSONObject("rows") ?: JSONObject()
            for (table in ParentTables) LinkRows.write(db, table, rows.optJSONArray(table), SQLiteDatabase.CONFLICT_IGNORE)
            val plays = changes.optJSONArray("plays") ?: JSONArray()
            LinkRows.write(db, "event", plays, SQLiteDatabase.CONFLICT_IGNORE, skip = setOf("id"))
            for (i in 0 until plays.length()) {
                val play = plays.optJSONObject(i) ?: continue
                db.execSQL("UPDATE song SET totalPlayTime = totalPlayTime + ? WHERE id = ?", arrayOf<Any?>(play.optLong("playTime"), play.optString("songId")))
            }
            val now = System.currentTimeMillis()
            changes.optJSONArray("liked").forEachId { db.execSQL("UPDATE song SET liked = 1, likedDate = ? WHERE id = ? AND liked = 0", arrayOf<Any?>(now, it)) }
            changes.optJSONArray("unliked").forEachId { db.execSQL("UPDATE song SET liked = 0, likedDate = NULL WHERE id = ?", arrayOf<Any?>(it)) }
            changes.optJSONArray("added").forEachId { db.execSQL("UPDATE song SET inLibrary = ? WHERE id = ? AND inLibrary IS NULL", arrayOf<Any?>(now, it)) }
            changes.optJSONArray("removed").forEachId { db.execSQL("UPDATE song SET inLibrary = NULL WHERE id = ?", arrayOf<Any?>(it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        database.refreshVersionsAsync()
    }

    // a short sum of what a tv shows from this library: likes, library songs, saved albums and artists, playlists and their order; plays leave it alone
    fun mark(): String {
        val db = database.openHelper.readableDatabase
        var sum = 1_125_899_906_842_597L
        fun add(sql: String) {
            db.query(sql).use { cursor ->
                while (cursor.moveToNext()) {
                    for (i in 0 until cursor.columnCount) sum = 31 * sum + (cursor.getString(i)?.hashCode() ?: 0)
                }
            }
        }
        add("SELECT id, likedDate FROM song WHERE liked = 1 ORDER BY id")
        add("SELECT id FROM song WHERE inLibrary IS NOT NULL ORDER BY id")
        add("SELECT id FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY id")
        add("SELECT id FROM artist WHERE bookmarkedAt IS NOT NULL ORDER BY id")
        add("SELECT id, name, bookmarkedAt FROM playlist ORDER BY id")
        add("SELECT playlistId, songId, position FROM playlist_song_map ORDER BY playlistId, position")
        return java.lang.Long.toHexString(sum)
    }

    private fun mimeTypes(): Map<String, String> =
        database.openHelper.readableDatabase.query("SELECT id, mimeType FROM format").use { cursor ->
            buildMap { while (cursor.moveToNext()) cursor.getString(1)?.let { put(cursor.getString(0), it) } }
        }

    private fun localFiles(): Map<String, Uri> =
        database.openHelper.readableDatabase.query("SELECT id, localUri FROM song WHERE localUri IS NOT NULL").use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), Uri.parse(cursor.getString(1))) }
        }

    private fun lengthOf(uri: Uri): Long? = runCatching {
        if (uri.scheme == "file" || uri.scheme == null) return File(requireNotNull(uri.path)).length().takeIf { it > 0 }
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { length -> length > 0 } }
    }.getOrNull()

    private fun copyLocal(uri: Uri, start: Long, count: Long, out: OutputStream) {
        val descriptor = if (uri.scheme == "file" || uri.scheme == null) null else context.contentResolver.openFileDescriptor(uri, "r")
        val input = descriptor?.let { FileInputStream(it.fileDescriptor) } ?: FileInputStream(File(requireNotNull(uri.path)))
        input.use { stream ->
            stream.channel.position(start)
            val buffer = ByteArray(64 * 1024)
            var left = count
            while (left > 0) {
                val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (read < 0) break
                out.write(buffer, 0, read)
                left -= read
            }
        }
        descriptor?.close()
    }

    private inline fun JSONArray?.forEachId(action: (String) -> Unit) {
        this ?: return
        for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let(action)
    }

    companion object {
        const val SnapshotType = "application/x-sekaitune-library+gzip"

        // parents before the maps that point at them, so nothing is turned away by a foreign key
        val ParentTables = listOf("song", "artist", "album", "song_artist_map", "song_album_map", "album_artist_map")
    }
}

