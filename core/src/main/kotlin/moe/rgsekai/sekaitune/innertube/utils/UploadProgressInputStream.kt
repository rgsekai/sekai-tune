/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.innertube.utils

import java.io.FilterInputStream
import java.io.InputStream

class UploadProgressInputStream(
    input: InputStream,
    private val totalLength: Long,
    private val initialBytesRead: Long = 0L,
    private val onProgress: (Float, Long) -> Unit,
) : FilterInputStream(input) {
    private var bytesRead = initialBytesRead

    override fun read(): Int =
        super.read().also { value ->
            if (value >= 0) reportBytesRead(1)
        }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int =
        super.read(buffer, offset, length).also { count ->
            if (count > 0) reportBytesRead(count.toLong())
        }

    private fun reportBytesRead(count: Long) {
        bytesRead += count
        if (totalLength > 0) {
            onProgress((bytesRead.toFloat() / totalLength).coerceIn(0f, 1f), bytesRead)
        }
    }
}

