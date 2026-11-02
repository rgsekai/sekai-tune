package moe.rgsekai.sekaitune.shared.data.stats

import moe.rgsekai.sekaitune.shared.platform.currentTimeMillis
import moe.rgsekai.sekaitune.shared.platform.localOffsetMillis

/**
 * A calendar day, in the device's own time zone.
 *
 * The listening history is bucketed by local calendar month and day, and `java.time`
 * is not there on iOS; this is the small slice of it the stats actually use.
 */
data class CivilDate(val year: Int, val month: Int, val day: Int) : Comparable<CivilDate> {
    override fun compareTo(other: CivilDate): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    /** Days since 1970-01-01 (proleptic Gregorian, Hinnant's algorithm). */
    val epochDay: Long
        get() {
            val y = (if (month <= 2) year - 1 else year).toLong()
            val era = y.floorDiv(400L)
            val yoe = y - era * 400
            val doy = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146097 + doe - 719468
        }

    /** 1 = Monday .. 7 = Sunday. */
    val dayOfWeek: Int get() = (epochDay + 3).mod(7L).toInt() + 1

    /** The Monday of this date's week. */
    fun mondayOfWeek(): CivilDate = ofEpochDay(epochDay - (dayOfWeek - 1))

    override fun toString(): String = "${year.toString().padStart(4, '0')}-${two(month)}-${two(day)}"

    companion object {
        fun ofEpochDay(epochDay: Long): CivilDate {
            val z = epochDay + 719468
            val era = z.floorDiv(146097L)
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
            return CivilDate(y, m, d)
        }

        /** Today, by the device clock and zone. */
        fun today(): CivilDate = ofEpochMillis(currentTimeMillis())

        fun ofEpochMillis(epochMs: Long): CivilDate =
            ofEpochDay((epochMs + localOffsetMillis(epochMs)).floorDiv(86_400_000L))

        /** Strict `YYYY-MM-DD`; null for anything else, so a stray key is skipped, not thrown on. */
        fun parse(text: String): CivilDate? {
            val parts = text.split('-')
            if (parts.size != 3 || parts[0].length != 4 || parts[1].length != 2 || parts[2].length != 2) return null
            val y = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val d = parts[2].toIntOrNull() ?: return null
            if (m !in 1..12 || d !in 1..CivilMonth(y, m).length) return null
            return CivilDate(y, m, d)
        }
    }
}

/** A calendar month; its string form (`YYYY-MM`) is the bucket's file name. */
data class CivilMonth(val year: Int, val month: Int) : Comparable<CivilMonth> {
    override fun compareTo(other: CivilMonth): Int = compareValuesBy(this, other, { it.year }, { it.month })

    val length: Int
        get() = when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

    fun atDay(day: Int) = CivilDate(year, month, day)
    fun atEndOfMonth() = CivilDate(year, month, length)

    override fun toString(): String = "${year.toString().padStart(4, '0')}-${two(month)}"

    companion object {
        fun of(date: CivilDate) = CivilMonth(date.year, date.month)

        fun parse(text: String): CivilMonth? {
            val parts = text.split('-')
            if (parts.size != 2 || parts[0].length != 4 || parts[1].length != 2) return null
            val y = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            return if (m in 1..12) CivilMonth(y, m) else null
        }
    }
}

internal fun two(value: Int): String = value.toString().padStart(2, '0')
