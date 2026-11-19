// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class ExplorerRangeTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 19)

    private fun firstDay(range: ExplorerRange) = range.bounds(today, zone).start.atZone(zone).toLocalDate()

    @Test
    fun rangesCoverWholeLocalDaysEndingToday() {
        assertEquals(today, firstDay(ExplorerRange.Today))
        assertEquals(today.minusDays(6), firstDay(ExplorerRange.Last7Days))
        // 30 local days, even though this window crosses the October DST change (721 hours).
        assertEquals(today.minusDays(29), firstDay(ExplorerRange.Last30Days))
        listOf(ExplorerRange.Today, ExplorerRange.Last7Days, ExplorerRange.Last30Days).forEach {
            val bounds = it.bounds(today, zone)
            assertEquals(today.plusDays(1).atStartOfDay(zone).toInstant(), bounds.end)
            assertEquals(0, bounds.start.atZone(zone).toLocalTime().toSecondOfDay())
        }
    }

    @Test
    fun customRangeIncludesItsLastDay() {
        val range = ExplorerRange.Custom(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3))
        val bounds = range.bounds(today, zone)

        assertEquals(LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant(), bounds.start)
        assertEquals(LocalDate.of(2026, 1, 4).atStartOfDay(zone).toInstant(), bounds.end)
    }

    @Test
    fun dayFollowsDaylightSavingChanges() {
        val bounds = ExplorerRange.Day(LocalDate.of(2026, 10, 25)).bounds(today, zone)

        assertEquals(Duration.ofHours(25), Duration.between(bounds.start, bounds.end))
    }

    @Test(expected = IllegalArgumentException::class)
    fun customRangeMustNotRunBackwards() {
        ExplorerRange.Custom(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 1))
    }
}
