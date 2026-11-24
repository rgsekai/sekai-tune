/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.aod

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AodLogger {
    private const val TAG = "AodLockscreen"
    private const val FILE_NAME = "sekai_aod.log"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var cachedLogUri: Uri? = null

    @Volatile
    var screenOffTimeMs: Long = 0L

    fun recordScreenOff() {
        screenOffTimeMs = System.currentTimeMillis()
    }

    @Synchronized
    fun log(context: Context?, message: String, throwable: Throwable? = null) {
        val now = System.currentTimeMillis()
        val timestamp = dateFormat.format(Date(now))
        val logLine = "[$timestamp] $message"

        if (throwable != null) {
            Log.e(TAG, logLine, throwable)
        } else {
            Log.d(TAG, logLine)
        }

        var written = false
        // 1. Try direct POSIX write
        try {
            val directFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FILE_NAME)
            FileWriter(directFile, true).use { fw ->
                PrintWriter(fw).use { pw ->
                    pw.println(logLine)
                    throwable?.printStackTrace(pw)
                    pw.flush()
                }
            }
            written = true
        } catch (_: Exception) {
        }

        // 2. If direct write failed (e.g. Scoped Storage), use MediaStore.Downloads
        if (!written && context != null) {
            try {
                writeViaMediaStore(context, logLine, throwable)
            } catch (_: Exception) {
            }
        }
    }

    private fun writeViaMediaStore(context: Context, line: String, throwable: Throwable?) {
        val resolver = context.contentResolver
        var uri = cachedLogUri

        if (uri == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf(FILE_NAME, "${Environment.DIRECTORY_DOWNLOADS}%")
            try {
                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        cachedLogUri = uri
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (uri == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                cachedLogUri = uri
            } catch (_: Exception) {
            }
        }

        if (uri != null) {
            resolver.openOutputStream(uri!!, "wa")?.use { os ->
                PrintWriter(os).use { pw ->
                    pw.println(line)
                    throwable?.printStackTrace(pw)
                    pw.flush()
                }
            }
        }
    }
}
