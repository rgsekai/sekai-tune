/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.webdav

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

data class ParsedAudioTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val pictureBytes: ByteArray? = null,
    val pictureMimeType: String? = null,
)

object WebDavTagReader {

    private val ISO_8859_1 = StandardCharsets.ISO_8859_1
    private val UTF_8 = StandardCharsets.UTF_8
    private val UTF_16 = StandardCharsets.UTF_16
    private val UTF_16BE = StandardCharsets.UTF_16BE

    /**
     * Attempts to parse tags from audio file header bytes (typically first 64 KB).
     * Dispatches based on file extension or magic header bytes.
     */
    fun parse(bytes: ByteArray, extension: String? = null): ParsedAudioTags? {
        if (bytes.size < 8) return null

        val ext = extension?.lowercase(Locale.US)?.trim()

        return when {
            bytes.startsWith("ID3") -> parseId3v2(bytes)
            bytes.startsWith("fLaC") -> parseFlac(bytes)
            bytes.startsWith("OggS") -> parseOgg(bytes)
            isMp4OrM4a(bytes, ext) -> parseMp4(bytes)
            ext == "mp3" -> parseId3v2(bytes)
            ext == "flac" -> parseFlac(bytes)
            ext in setOf("ogg", "opus") -> parseOgg(bytes)
            ext in setOf("m4a", "mp4", "aac") -> parseMp4(bytes)
            else -> null
        }
    }

    private fun ByteArray.startsWith(prefix: String): Boolean {
        val prefixBytes = prefix.toByteArray(StandardCharsets.US_ASCII)
        if (size < prefixBytes.size) return false
        for (i in prefixBytes.indices) {
            if (this[i] != prefixBytes[i]) return false
        }
        return true
    }

    private fun isMp4OrM4a(bytes: ByteArray, ext: String?): Boolean {
        if (ext in setOf("m4a", "mp4", "aac")) return true
        if (bytes.size < 12) return false
        val boxType = String(bytes, 4, 4, StandardCharsets.US_ASCII)
        return boxType == "ftyp" || boxType == "moov"
    }

    // ==========================================
    // ID3v2 Parser (v2.3 and v2.4)
    // ==========================================

    fun parseId3v2(bytes: ByteArray): ParsedAudioTags? {
        if (bytes.size < 10 || !bytes.startsWith("ID3")) return null

        val version = bytes[3].toInt() and 0xFF // 3 = v2.3, 4 = v2.4
        if (version != 3 && version != 4) return null

        val flags = bytes[5].toInt() and 0xFF
        val isTagUnsync = (flags and 0x80) != 0
        val hasExtendedHeader = (flags and 0x40) != 0

        // 4-byte synchsafe integer size
        val tagSize = synchsafeToInt(bytes, 6)
        val limit = (10 + tagSize).coerceAtMost(bytes.size)

        var offset = 10
        if (hasExtendedHeader && offset + 4 <= limit) {
            val extHeaderSize = if (version == 4) {
                synchsafeToInt(bytes, offset)
            } else {
                readInt32BE(bytes, offset)
            }
            offset += extHeaderSize.coerceAtLeast(4)
        }

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArtist: String? = null
        var trackNumber: Int? = null
        var discNumber: Int? = null
        var pictureBytes: ByteArray? = null
        var pictureMime: String? = null

        while (offset + 10 <= limit) {
            val frameId = String(bytes, offset, 4, StandardCharsets.US_ASCII)
            if (frameId.any { it !in 'A'..'Z' && it !in '0'..'9' }) {
                // End of frames (padding)
                break
            }

            val frameFormatFlags = bytes[offset + 9].toInt() and 0xFF
            val isFrameUnsync = (version == 4 && (frameFormatFlags and 0x02) != 0)
            val isUnsync = isTagUnsync || isFrameUnsync

            val frameSize = if (version == 4) {
                synchsafeToInt(bytes, offset + 4)
            } else {
                readInt32BE(bytes, offset + 4)
            }

            offset += 10
            if (frameSize <= 0 || offset + frameSize > limit) break

            when (frameId) {
                "TIT2" -> title = readId3Text(bytes, offset, frameSize)
                "TPE1" -> artist = readId3Text(bytes, offset, frameSize)
                "TALB" -> album = readId3Text(bytes, offset, frameSize)
                "TPE2" -> albumArtist = readId3Text(bytes, offset, frameSize)
                "TRCK" -> trackNumber = parseNumber(readId3Text(bytes, offset, frameSize))
                "TPOS" -> discNumber = parseNumber(readId3Text(bytes, offset, frameSize))
                "APIC" -> {
                    if (pictureBytes == null) {
                        val parsedPic = parseApicFrame(bytes, offset, frameSize, isUnsync)
                        if (parsedPic != null) {
                            pictureBytes = parsedPic.first
                            pictureMime = parsedPic.second
                        }
                    }
                }
            }

            offset += frameSize
        }

        if (title == null && artist == null && album == null && pictureBytes == null) {
            return null
        }

        return ParsedAudioTags(
            title = title?.trim()?.takeIf(String::isNotBlank),
            artist = artist?.trim()?.takeIf(String::isNotBlank),
            album = album?.trim()?.takeIf(String::isNotBlank),
            albumArtist = albumArtist?.trim()?.takeIf(String::isNotBlank),
            trackNumber = trackNumber,
            discNumber = discNumber,
            pictureBytes = pictureBytes,
            pictureMimeType = pictureMime,
        )
    }

    private fun readId3Text(bytes: ByteArray, offset: Int, length: Int): String? {
        if (length <= 1) return null
        val encoding = bytes[offset].toInt() and 0xFF
        val textBytes = bytes.copyOfRange(offset + 1, offset + length)
        val charset = id3Charset(encoding)
        return try {
            String(textBytes, charset).trimEnd('\u0000', ' ')
        } catch (_: Exception) {
            null
        }
    }

    private fun parseApicFrame(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        isUnsynchronised: Boolean = false,
    ): Pair<ByteArray, String>? {
        if (length < 5) return null
        var pos = offset
        val encoding = bytes[pos++].toInt() and 0xFF

        // MIME type (null-terminated ISO-8859-1)
        val mimeStart = pos
        while (pos < offset + length && bytes[pos] != 0.toByte()) {
            pos++
        }
        val mimeType = String(bytes, mimeStart, pos - mimeStart, ISO_8859_1).trim().ifBlank { "image/jpeg" }
        pos++ // skip null byte

        if (pos >= offset + length) return null
        val pictureType = bytes[pos++].toInt() and 0xFF // 3 = front cover

        // Description (null-terminated string using `encoding`)
        if (encoding == 1 || encoding == 2) {
            // 2-byte null terminator (step by 1 byte to handle any byte alignment)
            while (pos + 1 < offset + length) {
                if (bytes[pos] == 0.toByte() && bytes[pos + 1] == 0.toByte()) {
                    pos += 2
                    break
                }
                pos++
            }
        } else {
            // 1-byte null terminator
            while (pos < offset + length && bytes[pos] != 0.toByte()) {
                pos++
            }
            pos++
        }

        // Locate actual start of image data (JPEG, PNG, WebP) within the frame
        val magicOffset = findImageMagicOffset(bytes, offset, offset + length)
        val picStart = if (magicOffset >= offset && magicOffset in pos..(pos + 8)) {
            magicOffset
        } else if (magicOffset >= offset && (pos >= offset + length || magicOffset > pos)) {
            magicOffset
        } else {
            pos
        }

        if (picStart >= offset + length) return null
        val rawPicBytes = bytes.copyOfRange(picStart, offset + length)
        val picBytes = if (isUnsynchronised) deUnsynchronise(rawPicBytes) else rawPicBytes
        if (picBytes.isEmpty()) return null
        return picBytes to mimeType
    }

    private fun findImageMagicOffset(bytes: ByteArray, start: Int, end: Int): Int {
        val safeEnd = (end - 3).coerceAtMost(bytes.size - 3)
        for (i in start until safeEnd) {
            // JPEG: FF D8 FF
            if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD8.toByte() && bytes[i + 2] == 0xFF.toByte()) {
                return i
            }
            // PNG: 89 50 4E 47
            if (bytes[i] == 0x89.toByte() && bytes[i + 1] == 0x50.toByte() && bytes[i + 2] == 0x4E.toByte() && bytes[i + 3] == 0x47.toByte()) {
                return i
            }
            // WebP: RIFF
            if (bytes[i] == 0x52.toByte() && bytes[i + 1] == 0x49.toByte() && bytes[i + 2] == 0x46.toByte() && bytes[i + 3] == 0x46.toByte()) {
                return i
            }
        }
        return -1
    }

    fun deUnsynchronise(data: ByteArray): ByteArray {
        var outLen = 0
        for (i in data.indices) {
            if (i > 0 && data[i - 1] == 0xFF.toByte() && data[i] == 0x00.toByte()) {
                continue
            }
            outLen++
        }
        if (outLen == data.size) return data
        val out = ByteArray(outLen)
        var w = 0
        for (i in data.indices) {
            if (i > 0 && data[i - 1] == 0xFF.toByte() && data[i] == 0x00.toByte()) {
                continue
            }
            out[w++] = data[i]
        }
        return out
    }

    /**
     * Determines whether the audio file header indicates a larger tag block (e.g. embedded cover art)
     * beyond the initial read window, and returns the total byte size to fetch (capped at maxCapBytes).
     */
    fun determineRequiredHeaderSize(
        bytes: ByteArray,
        extension: String? = null,
        maxCapBytes: Long = 3L * 1024 * 1024, // 3 MB cap
    ): Long? {
        val ext = extension?.lowercase(Locale.US)?.trim()

        // 1. ID3v2 (mp3)
        if (bytes.startsWith("ID3") || ext == "mp3") {
            if (bytes.size >= 10 && bytes.startsWith("ID3")) {
                val tagSize = synchsafeToInt(bytes, 6)
                val totalTagSize = 10L + tagSize
                if (totalTagSize > bytes.size) {
                    return totalTagSize.coerceAtMost(maxCapBytes)
                }
            }
            return null
        }

        // 2. FLAC
        if (bytes.startsWith("fLaC") || ext == "flac") {
            if (bytes.size >= 4 && bytes.startsWith("fLaC")) {
                var offset = 4
                while (offset + 4 <= bytes.size) {
                    val header = bytes[offset].toInt() and 0xFF
                    val isLast = (header and 0x80) != 0
                    val length = readInt24BE(bytes, offset + 1)
                    offset += 4
                    if (offset + length > bytes.size) {
                        val needed = (offset + length).toLong()
                        return needed.coerceAtMost(maxCapBytes)
                    }
                    offset += length
                    if (isLast) break
                }
            }
            return null
        }

        // 3. MP4 / M4A
        if (isMp4OrM4a(bytes, ext)) {
            var pos = 0
            val limit = bytes.size
            while (pos + 8 <= limit) {
                val size = readInt32BE(bytes, pos)
                val type = String(bytes, pos + 4, 4, ISO_8859_1)
                val actualSize = when (size) {
                    1 -> if (pos + 16 <= limit) readInt32BE(bytes, pos + 12).toLong() else 0L
                    0 -> 0L
                    else -> size.toLong()
                }
                if (actualSize <= 0) break
                if (type == "moov") {
                    val needed = (pos + actualSize)
                    if (needed > bytes.size) {
                        return needed.coerceAtMost(maxCapBytes)
                    }
                    return null
                }
                pos += actualSize.toInt()
            }
            return null
        }

        return null
    }

    private fun id3Charset(encoding: Int): Charset = when (encoding) {
        1 -> UTF_16
        2 -> UTF_16BE
        3 -> UTF_8
        else -> ISO_8859_1
    }

    private fun synchsafeToInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun readInt32BE(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun readInt24BE(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset + 2].toInt() and 0xFF)
    }

    private fun readInt32LE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    // ==========================================
    // FLAC / Vorbis Comment Parser
    // ==========================================

    fun parseFlac(bytes: ByteArray): ParsedAudioTags? {
        if (bytes.size < 4 || !bytes.startsWith("fLaC")) return null

        var offset = 4
        var vorbisTags: ParsedAudioTags? = null
        var pictureBytes: ByteArray? = null
        var pictureMime: String? = null

        while (offset + 4 <= bytes.size) {
            val header = bytes[offset].toInt() and 0xFF
            val isLast = (header and 0x80) != 0
            val blockType = header and 0x7F
            val length = readInt24BE(bytes, offset + 1)
            offset += 4

            if (offset + length > bytes.size) {
                // Partial block read
                break
            }

            when (blockType) {
                4 -> {
                    // VORBIS_COMMENT block
                    vorbisTags = parseVorbisCommentPayload(bytes, offset, length)
                }
                6 -> {
                    // PICTURE block
                    if (pictureBytes == null) {
                        val pic = parseFlacPicturePayload(bytes, offset, length)
                        if (pic != null) {
                            pictureBytes = pic.first
                            pictureMime = pic.second
                        }
                    }
                }
            }

            offset += length
            if (isLast) break
        }

        if (vorbisTags == null && pictureBytes == null) return null

        return ParsedAudioTags(
            title = vorbisTags?.title,
            artist = vorbisTags?.artist,
            album = vorbisTags?.album,
            albumArtist = vorbisTags?.albumArtist,
            trackNumber = vorbisTags?.trackNumber,
            discNumber = vorbisTags?.discNumber,
            pictureBytes = pictureBytes ?: vorbisTags?.pictureBytes,
            pictureMimeType = pictureMime ?: vorbisTags?.pictureMimeType,
        )
    }

    fun parseVorbisCommentPayload(bytes: ByteArray, offset: Int, length: Int): ParsedAudioTags? {
        val limit = offset + length
        var pos = offset

        if (pos + 4 > limit) return null
        val vendorLength = readInt32LE(bytes, pos)
        pos += 4
        if (pos + vendorLength > limit) return null
        pos += vendorLength

        if (pos + 4 > limit) return null
        val commentCount = readInt32LE(bytes, pos)
        pos += 4

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArtist: String? = null
        var trackNumber: Int? = null
        var discNumber: Int? = null
        var pictureBytes: ByteArray? = null
        var pictureMime: String? = null

        for (i in 0 until commentCount) {
            if (pos + 4 > limit) break
            val commentLength = readInt32LE(bytes, pos)
            pos += 4
            if (commentLength < 0 || pos + commentLength > limit) break

            val commentStr = String(bytes, pos, commentLength, UTF_8)
            pos += commentLength

            val eqIdx = commentStr.indexOf('=')
            if (eqIdx > 0) {
                val key = commentStr.substring(0, eqIdx).uppercase(Locale.US).trim()
                val value = commentStr.substring(eqIdx + 1).trim()
                when (key) {
                    "TITLE" -> if (title == null) title = value
                    "ARTIST" -> if (artist == null) artist = value
                    "ALBUM" -> if (album == null) album = value
                    "ALBUMARTIST", "ALBUM ARTIST" -> if (albumArtist == null) albumArtist = value
                    "TRACKNUMBER", "TRACK" -> if (trackNumber == null) trackNumber = parseNumber(value)
                    "DISCNUMBER", "DISC" -> if (discNumber == null) discNumber = parseNumber(value)
                    "METADATA_BLOCK_PICTURE" -> {
                        if (pictureBytes == null) {
                            try {
                                val decoded = Base64.getDecoder().decode(value)
                                val pic = parseFlacPicturePayload(decoded, 0, decoded.size)
                                if (pic != null) {
                                    pictureBytes = pic.first
                                    pictureMime = pic.second
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
        }

        return ParsedAudioTags(
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            trackNumber = trackNumber,
            discNumber = discNumber,
            pictureBytes = pictureBytes,
            pictureMimeType = pictureMime,
        )
    }

    private fun parseFlacPicturePayload(bytes: ByteArray, offset: Int, length: Int): Pair<ByteArray, String>? {
        if (length < 32) return null
        var pos = offset
        val type = readInt32BE(bytes, pos)
        pos += 4

        val mimeLen = readInt32BE(bytes, pos)
        pos += 4
        if (pos + mimeLen > offset + length) return null
        val mime = String(bytes, pos, mimeLen, StandardCharsets.US_ASCII)
        pos += mimeLen

        val descLen = readInt32BE(bytes, pos)
        pos += 4
        if (pos + descLen > offset + length) return null
        pos += descLen

        // width(4), height(4), depth(4), colors(4) = 16 bytes
        pos += 16
        if (pos + 4 > offset + length) return null
        val picLen = readInt32BE(bytes, pos)
        pos += 4

        if (picLen <= 0 || pos + picLen > offset + length) return null
        val picBytes = bytes.copyOfRange(pos, pos + picLen)
        return picBytes to mime.ifBlank { "image/jpeg" }
    }

    // ==========================================
    // Ogg / Opus Parser
    // ==========================================

    fun parseOgg(bytes: ByteArray): ParsedAudioTags? {
        if (bytes.size < 8) return null

        // Look for OpusTags magic: "OpusTags"
        val opusMagic = "OpusTags".toByteArray(StandardCharsets.US_ASCII)
        val opusIdx = indexOf(bytes, opusMagic)
        if (opusIdx >= 0) {
            val start = opusIdx + opusMagic.size
            return parseVorbisCommentPayload(bytes, start, bytes.size - start)
        }

        // Look for Vorbis comment: 0x03 + "vorbis"
        val vorbisMagic = byteArrayOf(0x03, 'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte())
        val vorbisIdx = indexOf(bytes, vorbisMagic)
        if (vorbisIdx >= 0) {
            val start = vorbisIdx + vorbisMagic.size
            return parseVorbisCommentPayload(bytes, start, bytes.size - start)
        }

        return null
    }

    private fun indexOf(source: ByteArray, target: ByteArray): Int {
        if (target.isEmpty() || source.size < target.size) return -1
        outer@ for (i in 0..source.size - target.size) {
            for (j in target.indices) {
                if (source[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }

    // ==========================================
    // MP4 / M4A ilst Parser
    // ==========================================

    fun parseMp4(bytes: ByteArray): ParsedAudioTags? {
        val ilstOffset = findIlstAtom(bytes) ?: findIlstAtomAnywhere(bytes) ?: return null
        return parseIlstPayload(bytes, ilstOffset.first, ilstOffset.second)
    }

    private fun findIlstAtom(bytes: ByteArray): Pair<Int, Int>? {
        // Recursive / sequential walk down moov -> udta -> meta -> ilst
        val moov = findBox(bytes, 0, bytes.size, "moov") ?: return null
        val udta = findBox(bytes, moov.first, moov.second, "udta")
        val metaParentStart = udta?.first ?: moov.first
        val metaParentLen = udta?.second ?: moov.second
        val meta = findBox(bytes, metaParentStart, metaParentLen, "meta") ?: return null

        // `meta` box has a 4-byte version/flags after header before child atoms
        val metaChildStart = meta.first + 4
        val metaChildLen = meta.second - 4
        if (metaChildLen <= 0) return null

        val ilst = findBox(bytes, metaChildStart, metaChildLen, "ilst") ?: return null
        return ilst
    }

    private fun findIlstAtomAnywhere(bytes: ByteArray): Pair<Int, Int>? {
        val moovBytes = "moov".toByteArray(ISO_8859_1)
        val idx = indexOf(bytes, moovBytes)
        if (idx < 4) return null
        val boxStart = idx - 4
        val boxLen = readInt32BE(bytes, boxStart)
        val actualLen = if (boxLen == 1 && boxStart + 16 <= bytes.size) {
            readInt32BE(bytes, boxStart + 12)
        } else if (boxLen <= 0) {
            bytes.size - boxStart
        } else {
            boxLen
        }
        val safeLen = actualLen.coerceAtMost(bytes.size - boxStart)
        val udta = findBox(bytes, boxStart + 8, safeLen - 8, "udta")
        val metaParentStart = udta?.first ?: (boxStart + 8)
        val metaParentLen = udta?.second ?: (safeLen - 8)
        val meta = findBox(bytes, metaParentStart, metaParentLen, "meta") ?: return null

        val metaChildStart = meta.first + 4
        val metaChildLen = meta.second - 4
        if (metaChildLen <= 0) return null

        return findBox(bytes, metaChildStart, metaChildLen, "ilst")
    }

    private fun findBox(bytes: ByteArray, offset: Int, length: Int, targetType: String): Pair<Int, Int>? {
        var pos = offset
        val limit = (offset + length).coerceAtMost(bytes.size)
        while (pos + 8 <= limit) {
            val size = readInt32BE(bytes, pos)
            val type = String(bytes, pos + 4, 4, ISO_8859_1)
            val headerSize = 8
            val actualSize = when (size) {
                1 -> {
                    // 64-bit size
                    if (pos + 16 > limit) return null
                    readInt32BE(bytes, pos + 12) // using lower 32 bits
                }
                0 -> limit - pos
                else -> size
            }
            if (actualSize < headerSize || pos + actualSize > limit) break

            if (type == targetType) {
                return (pos + headerSize) to (actualSize - headerSize)
            }
            pos += actualSize
        }
        return null
    }

    private fun parseIlstPayload(bytes: ByteArray, offset: Int, length: Int): ParsedAudioTags? {
        var pos = offset
        val limit = (offset + length).coerceAtMost(bytes.size)

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArtist: String? = null
        var trackNumber: Int? = null
        var discNumber: Int? = null
        var pictureBytes: ByteArray? = null
        var pictureMime: String? = null

        while (pos + 8 <= limit) {
            val itemSize = readInt32BE(bytes, pos)
            val itemType = String(bytes, pos + 4, 4, ISO_8859_1)
            if (itemSize < 8 || pos + itemSize > limit) break

            // Inside each ilst item is a 'data' box
            val dataBox = findBox(bytes, pos + 8, itemSize - 8, "data")
            if (dataBox != null) {
                // data box header is 8 bytes, followed by 4 bytes type flag, 4 bytes locale
                val dataPayloadStart = dataBox.first + 8
                val dataPayloadLen = dataBox.second - 8
                if (dataPayloadLen > 0 && dataPayloadStart + dataPayloadLen <= limit) {
                    when (itemType) {
                        "\u00a9nam" -> title = String(bytes, dataPayloadStart, dataPayloadLen, UTF_8)
                        "\u00a9ART" -> artist = String(bytes, dataPayloadStart, dataPayloadLen, UTF_8)
                        "\u00a9alb" -> album = String(bytes, dataPayloadStart, dataPayloadLen, UTF_8)
                        "aART" -> albumArtist = String(bytes, dataPayloadStart, dataPayloadLen, UTF_8)
                        "trkn" -> {
                            if (dataPayloadLen >= 4) {
                                trackNumber = readInt16BE(bytes, dataPayloadStart + 2)
                            }
                        }
                        "disk" -> {
                            if (dataPayloadLen >= 4) {
                                discNumber = readInt16BE(bytes, dataPayloadStart + 2)
                            }
                        }
                        "covr" -> {
                            if (pictureBytes == null) {
                                pictureBytes = bytes.copyOfRange(dataPayloadStart, dataPayloadStart + dataPayloadLen)
                                pictureMime = if (pictureBytes.size >= 8 && pictureBytes[0] == 0x89.toByte() && pictureBytes[1] == 'P'.code.toByte()) {
                                    "image/png"
                                } else {
                                    "image/jpeg"
                                }
                            }
                        }
                    }
                }
            }

            pos += itemSize
        }

        if (title == null && artist == null && album == null && pictureBytes == null) return null

        return ParsedAudioTags(
            title = title?.trim()?.takeIf(String::isNotBlank),
            artist = artist?.trim()?.takeIf(String::isNotBlank),
            album = album?.trim()?.takeIf(String::isNotBlank),
            albumArtist = albumArtist?.trim()?.takeIf(String::isNotBlank),
            trackNumber = trackNumber?.takeIf { it > 0 },
            discNumber = discNumber?.takeIf { it > 0 },
            pictureBytes = pictureBytes,
            pictureMimeType = pictureMime,
        )
    }

    private fun readInt16BE(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun parseNumber(str: String?): Int? {
        if (str.isNullOrBlank()) return null
        val numPart = str.split('/', '-', ' ', limit = 2)[0].trim()
        return numPart.toIntOrNull()
    }
}
