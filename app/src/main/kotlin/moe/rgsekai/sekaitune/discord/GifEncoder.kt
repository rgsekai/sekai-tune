/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.discord

import java.io.ByteArrayOutputStream

internal class GifEncoder(
    private val width: Int,
    private val height: Int,
    palette: IntArray,
) {
    private val out = ByteArrayOutputStream()

    init {
        require(palette.size <= Transparent) { "a GIF palette holds 255 colours plus see-through" }
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(width)
        writeShort(height)
        out.write(0xF7)
        out.write(Transparent)
        out.write(0)
        for (i in 0 until 256) {
            val rgb = palette.getOrElse(i) { 0 }
            out.write(rgb shr 16 and 0xFF)
            out.write(rgb shr 8 and 0xFF)
            out.write(rgb and 0xFF)
        }
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
    }

    fun addFrame(indexes: ByteArray, delayCentis: Int) {
        require(indexes.size == width * height)
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x09))
        writeShort(delayCentis)
        out.write(Transparent)
        out.write(0)
        out.write(0x2C)
        writeShort(0)
        writeShort(0)
        writeShort(width)
        writeShort(height)
        out.write(0)
        out.write(MinCodeSize)
        writeSubBlocks(LzwPacker().pack(indexes))
    }

    fun finish(): ByteArray {
        out.write(0x3B)
        return out.toByteArray()
    }

    private fun writeShort(value: Int) {
        out.write(value and 0xFF)
        out.write(value shr 8 and 0xFF)
    }

    private fun writeSubBlocks(data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val length = minOf(255, data.size - offset)
            out.write(length)
            out.write(data, offset, length)
            offset += length
        }
        out.write(0)
    }

    private class LzwPacker {
        private val clearCode = 1 shl MinCodeSize
        private val endCode = clearCode + 1
        private val dictionary = HashMap<Int, Int>(MaxCodes * 2)
        private var nextCode = clearCode + 2
        private var codeBits = MinCodeSize + 1
        private var maxCode = (1 shl codeBits) - 1
        private var resetPending = false

        private val bytes = ByteArrayOutputStream()
        private var bitBuffer = 0
        private var bitCount = 0

        fun pack(indexes: ByteArray): ByteArray {
            write(clearCode)
            var prefix = indexes[0].toInt() and 0xFF
            for (i in 1 until indexes.size) {
                val next = indexes[i].toInt() and 0xFF
                val key = (prefix shl 8) or next
                val known = dictionary[key]
                if (known != null) {
                    prefix = known
                    continue
                }
                write(prefix)
                prefix = next
                if (nextCode < MaxCodes) {
                    dictionary[key] = nextCode++
                } else {
                    dictionary.clear()
                    nextCode = clearCode + 2
                    resetPending = true
                    write(clearCode)
                }
            }
            write(prefix)
            write(endCode)
            if (bitCount > 0) bytes.write(bitBuffer and 0xFF)
            return bytes.toByteArray()
        }

        private fun write(code: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += codeBits
            while (bitCount >= 8) {
                bytes.write(bitBuffer and 0xFF)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
            if (resetPending) {
                codeBits = MinCodeSize + 1
                maxCode = (1 shl codeBits) - 1
                resetPending = false
            } else if (nextCode > maxCode) {
                codeBits++
                maxCode = if (codeBits == MaxBits) MaxCodes else (1 shl codeBits) - 1
            }
        }
    }

    companion object {
        const val Transparent = 255
        private const val MinCodeSize = 8
        private const val MaxBits = 12
        private const val MaxCodes = 1 shl MaxBits
    }
}

