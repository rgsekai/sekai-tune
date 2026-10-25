/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources.webdav

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object WebDavArtworkCache {

    private const val FOLDER_NAME = "webdav_art"
    private const val MAX_DIMENSION = 600
    private const val JPEG_QUALITY = 85
    private const val MAX_CACHE_SIZE_BYTES = 100L * 1024 * 1024 // 100 MB
    private const val TARGET_PRUNE_SIZE_BYTES = 80L * 1024 * 1024 // 80 MB

    fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, FOLDER_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Saves embedded picture bytes to disk as a resized JPEG (<sha1>.jpg).
     * Returns the file URI string ("file://...") or null if invalid.
     */
    fun saveArtwork(context: Context, pictureBytes: ByteArray): String? {
        if (pictureBytes.isEmpty()) return null

        return try {
            val sha1Hex = sha1(pictureBytes)
            val dir = getCacheDir(context)
            val targetFile = File(dir, "$sha1Hex.jpg")

            if (targetFile.exists() && targetFile.length() > 0L) {
                targetFile.setLastModified(System.currentTimeMillis())
                return "file://${targetFile.absolutePath}"
            }

            val originalBitmap = BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size)
                ?: return null

            val width = originalBitmap.width
            val height = originalBitmap.height

            val scaledBitmap = if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                val ratio = width.toFloat() / height.toFloat()
                val (targetW, targetH) = if (ratio > 1f) {
                    MAX_DIMENSION to (MAX_DIMENSION / ratio).toInt().coerceAtLeast(1)
                } else {
                    (MAX_DIMENSION * ratio).toInt().coerceAtLeast(1) to MAX_DIMENSION
                }
                Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            } else {
                originalBitmap
            }

            val tempFile = File(dir, "$sha1Hex.tmp_${System.currentTimeMillis()}")
            FileOutputStream(tempFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.flush()
            }

            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
            originalBitmap.recycle()

            if (tempFile.renameTo(targetFile)) {
                pruneCacheIfNeeded(dir)
                "file://${targetFile.absolutePath}"
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Throwable) {
            Timber.w(e, "Failed to save WebDAV embedded artwork")
            null
        }
    }

    fun pruneCacheIfNeeded(dir: File) {
        try {
            val files = dir.listFiles { file -> file.isFile && file.extension == "jpg" } ?: return
            var totalSize = files.sumOf { it.length() }
            if (totalSize > MAX_CACHE_SIZE_BYTES) {
                val sorted = files.sortedBy { it.lastModified() }
                for (file in sorted) {
                    val len = file.length()
                    if (file.delete()) {
                        totalSize -= len
                        if (totalSize <= TARGET_PRUNE_SIZE_BYTES) {
                            break
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Timber.w(e, "Failed during WebDAV artwork cache pruning")
        }
    }

    fun clearCache(context: Context) {
        try {
            val dir = File(context.cacheDir, FOLDER_NAME)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (e: Throwable) {
            Timber.w(e, "Failed to clear WebDAV artwork cache")
        }
    }

    private fun sha1(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }
}
