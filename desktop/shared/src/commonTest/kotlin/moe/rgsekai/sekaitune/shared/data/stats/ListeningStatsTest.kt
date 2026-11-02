package moe.rgsekai.sekaitune.shared.data.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListeningStatsTest {
    @Test
    fun civilDateMatchesKnownDays() {
        assertEquals(CivilDate(1970, 1, 1), CivilDate.ofEpochDay(0))
        assertEquals(20_000L, CivilDate.ofEpochDay(20_000).epochDay)
        // 2026-08-14 was a Friday; its week starts Monday the 10th.
        val friday = CivilDate(2026, 8, 14)
        assertEquals(5, friday.dayOfWeek)
        assertEquals(CivilDate(2026, 8, 10), friday.mondayOfWeek())
        // A week that straddles the month and year.
        assertEquals(CivilDate(2025, 12, 29), CivilDate(2026, 1, 2).mondayOfWeek())
    }

    @Test
    fun parsingIsStrict() {
        assertEquals(CivilDate(2024, 2, 29), CivilDate.parse("2024-02-29"))
        assertNull(CivilDate.parse("2025-02-29"))
        assertNull(CivilDate.parse("2026/08/14"))
        assertEquals(CivilMonth(2026, 8), CivilMonth.parse("2026-08"))
        assertEquals("2026-08", CivilMonth(2026, 8).toString())
    }

    @Test
    fun periodRanges() {
        val today = CivilDate(2026, 8, 14)
        assertEquals(CivilDate(2026, 8, 10) to today, ReplayPeriod.THIS_WEEK.range(today))
        assertEquals(CivilDate(2026, 8, 1) to CivilDate(2026, 8, 31), ReplayPeriod.THIS_MONTH.range(today))
        assertEquals(CivilDate(2026, 1, 1) to CivilDate(2026, 12, 31), ReplayPeriod.THIS_YEAR.range(today))
        assertNull(ReplayPeriod.ALL_TIME.range(today))
        assertEquals(true, ReplayPeriod.THIS_WEEK.covers(CivilMonth(2026, 8), today))
        assertEquals(false, ReplayPeriod.THIS_WEEK.covers(CivilMonth(2026, 7), today))
        assertEquals("August 2026", ReplayPeriod.THIS_MONTH.label(today))
    }
}
