package moe.rgsekai.sekaitune.shared.data.canvas

/**
 * Just enough protobuf wire format for the canvaz-cache fallback: length-delimited fields out, and in. Everything
 * else on the wire (varints, fixed32/64) is skipped, never modelled.
 */
internal object CanvasProto {

    /** One length-delimited field: tag, varint length, payload. */
    fun field(number: Int, payload: ByteArray): ByteArray =
        varint(((number shl 3) or 2).toLong()) + varint(payload.size.toLong()) + payload

    fun string(number: Int, value: String): ByteArray = field(number, value.encodeToByteArray())

    /** The length-delimited fields of [bytes] as (number, payload), in order. Throws if the message is malformed. */
    fun fields(bytes: ByteArray): List<Pair<Int, ByteArray>> {
        val out = mutableListOf<Pair<Int, ByteArray>>()
        var pos = 0
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(pos < bytes.size && shift < 64) { "truncated varint" }
                val b = bytes[pos++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
        while (pos < bytes.size) {
            val tag = varint().toInt()
            if (tag == 0) break
            when (tag and 7) {
                0 -> varint()
                1 -> pos += 8
                2 -> {
                    val len = varint().toInt()
                    require(len >= 0 && pos + len <= bytes.size) { "bad length" }
                    out += (tag ushr 3) to bytes.copyOfRange(pos, pos + len)
                    pos += len
                }
                5 -> pos += 4
                else -> error("unsupported wire type ${tag and 7}")
            }
        }
        return out
    }

    private fun varint(value: Long): ByteArray {
        var v = value
        val out = mutableListOf<Byte>()
        while (v and 0x7F.inv().toLong() != 0L) {
            out += ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
        out += v.toByte()
        return out.toByteArray()
    }
}
