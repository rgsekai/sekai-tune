/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.download

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PausedDeviceDownload(
    val songId: String,
    val title: String,
    val artist: String,
    val bytesWritten: Long,
    val totalBytes: Long,
    val progress: Int,
    val format: String,
    val dateModified: Long = System.currentTimeMillis(),
)

@Singleton
class PausedDeviceDownloadStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs: SharedPreferences =
            context.getSharedPreferences("paused_device_downloads_prefs", Context.MODE_PRIVATE)

        fun observePaused(): Flow<List<PausedDeviceDownload>> =
            callbackFlow {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    trySend(getAll())
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                trySend(getAll())
                awaitClose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

        fun getAll(): List<PausedDeviceDownload> {
            val jsonStr = prefs.getString(KEY_PAUSED_LIST, null) ?: return emptyList()
            return try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<PausedDeviceDownload>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        PausedDeviceDownload(
                            songId = obj.getString("songId"),
                            title = obj.getString("title"),
                            artist = obj.getString("artist"),
                            bytesWritten = obj.optLong("bytesWritten", 0L),
                            totalBytes = obj.optLong("totalBytes", 0L),
                            progress = obj.optInt("progress", 0),
                            format = obj.optString("format", "mp3"),
                            dateModified = obj.optLong("dateModified", System.currentTimeMillis()),
                        )
                    )
                }
                list
            } catch (e: Exception) {
                Timber.e(e, "Error parsing paused device downloads JSON")
                emptyList()
            }
        }

        fun get(songId: String): PausedDeviceDownload? =
            getAll().firstOrNull { it.songId == songId }

        @Synchronized
        fun savePaused(
            songId: String,
            title: String,
            artist: String,
            bytesWritten: Long,
            totalBytes: Long,
            progress: Int,
            format: String = "mp3",
        ) {
            val current = getAll().toMutableList()
            current.removeAll { it.songId == songId }
            current.add(
                PausedDeviceDownload(
                    songId = songId,
                    title = title,
                    artist = artist,
                    bytesWritten = bytesWritten,
                    totalBytes = totalBytes,
                    progress = progress,
                    format = format,
                    dateModified = System.currentTimeMillis(),
                )
            )
            persist(current)
        }

        @Synchronized
        fun remove(songId: String) {
            val current = getAll().toMutableList()
            if (current.removeAll { it.songId == songId }) {
                persist(current)
            }
        }

        private fun persist(list: List<PausedDeviceDownload>) {
            try {
                val array = JSONArray()
                for (item in list) {
                    val obj = JSONObject().apply {
                        put("songId", item.songId)
                        put("title", item.title)
                        put("artist", item.artist)
                        put("bytesWritten", item.bytesWritten)
                        put("totalBytes", item.totalBytes)
                        put("progress", item.progress)
                        put("format", item.format)
                        put("dateModified", item.dateModified)
                    }
                    array.put(obj)
                }
                prefs.edit().putString(KEY_PAUSED_LIST, array.toString()).apply()
            } catch (e: Exception) {
                Timber.e(e, "Error persisting paused device downloads")
            }
        }

        companion object {
            private const val KEY_PAUSED_LIST = "paused_device_downloads_json"

            fun getPartFile(context: Context, songId: String): File {
                val dir = File(context.cacheDir, "downloads").apply { if (!exists()) mkdirs() }
                return File(dir, "dl_src_${songId}.part")
            }
        }
    }
