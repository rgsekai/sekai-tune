/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import timber.log.Timber
import java.io.InterruptedIOException

/**
 * Fetches progressive media streams as a sequence of bounded byte ranges (~1MB)
 * instead of a single unbounded GET request.
 *
 * GoogleVideo intentionally throttles unbounded progressive GET responses to ~15 kB/s,
 * causing container sniffing and initial buffer filling to stall for ~700-800ms.
 * Bounded Range requests (e.g. Range: bytes=0-1048575) are served unthrottled at line rate
 * (>5-10 MB/s), allowing ExoPlayer to fill its initial playback buffer in under 40ms.
 */
@UnstableApi
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkBytes: Long = DEFAULT_CHUNK_BYTES,
) : DataSource {

    private var baseSpec: DataSpec? = null
    private var position = 0L
    private var bytesRemaining = 0L
    private var chunkRemaining = 0L
    private var chunkOpen = false
    private var rangeBytes = chunkBytes
    private var passthrough = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        closeChunk()
        baseSpec = dataSpec
        position = dataSpec.position

        val total = dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()
            ?: (if (dataSpec.length != C.LENGTH_UNSET.toLong() && dataSpec.length > 0L) position + dataSpec.length else null)

        if (total == null) {
            passthrough = true
            chunkOpen = true
            try {
                return upstream.open(dataSpec)
            } catch (e: Exception) {
                if (e !is InterruptedIOException) {
                    val who = dataSpec.uri.host ?: dataSpec.uri.toString().take(120)
                    Timber.tag(TAG).w(e, "%s refused unbounded stream open", who)
                }
                throw e
            }
        }

        passthrough = false
        val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            total
        } else {
            minOf(total, position + dataSpec.length)
        }
        bytesRemaining = (end - position).coerceAtLeast(0L)
        rangeBytes = minOf(chunkBytes, DEFAULT_CHUNK_BYTES)
        if (bytesRemaining > 0) {
            openChunk()
        }
        return bytesRemaining
    }

    private fun openChunk() {
        val length = minOf(rangeBytes, bytesRemaining)
        val spec = requireNotNull(baseSpec).buildUpon()
            .setPosition(position)
            .setLength(length)
            .build()
        try {
            upstream.open(spec)
        } catch (e: Exception) {
            if (e !is InterruptedIOException) {
                Timber.tag(TAG).w(
                    e,
                    "Range %d-%d open failed for host=%s",
                    position,
                    position + length - 1,
                    spec.uri.host,
                )
            }
            throw e
        }
        chunkRemaining = length
        chunkOpen = true
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) return upstream.read(buffer, offset, length)
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        repeat(MAX_EMPTY_RANGES) {
            if (chunkRemaining == 0L) {
                closeChunk()
                openChunk()
            }
            val readLength = minOf(length.toLong(), chunkRemaining).toInt()
            val read = upstream.read(buffer, offset, readLength)
            if (read != C.RESULT_END_OF_INPUT) {
                position += read
                chunkRemaining -= read
                bytesRemaining -= read
                return read
            }
            chunkRemaining = 0L
        }
        return C.RESULT_END_OF_INPUT
    }

    private fun closeChunk() {
        if (chunkOpen) {
            runCatching { upstream.close() }
            chunkOpen = false
        }
    }

    override fun getUri(): Uri? = upstream.uri ?: baseSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        closeChunk()
        baseSpec = null
        bytesRemaining = 0L
        chunkRemaining = 0L
        passthrough = false
    }

    companion object {
        private const val TAG = "ChunkedDataSource"
        const val DEFAULT_CHUNK_BYTES = 1024 * 1024L // 1 MB chunks for unthrottled line-rate reads
        private const val MAX_EMPTY_RANGES = 3
    }

    class Factory(
        private val upstream: DataSource.Factory,
        private val chunkBytes: Long = DEFAULT_CHUNK_BYTES,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            ChunkedDataSource(upstream.createDataSource(), chunkBytes)
    }
}
