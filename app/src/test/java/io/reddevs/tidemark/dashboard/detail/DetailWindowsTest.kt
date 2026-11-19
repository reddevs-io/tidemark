// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.detail

import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.data.model.SeriesPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class DetailWindowsTest {
    // A Thursday.
    private val today = LocalDate.of(2026, 11, 19)

    @Test
    fun weekBoundsFollowTheLocaleFirstDay() {
        val window = ChartWindow(ChartRange.WEEK, today)
        assertEquals(
            WindowBounds(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 22)),
            DetailWindows.bounds(window, Locale.US),
        )
        assertEquals(
            WindowBounds(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 23)),
            DetailWindows.bounds(window, Locale.FRANCE),
        )
    }

    @Test
    fun dayMonthAndYearBounds() {
        assertEquals(WindowBounds(today, today.plusDays(1)), DetailWindows.bounds(ChartWindow(ChartRange.DAY, today), Locale.UK))
        assertEquals(
            WindowBounds(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)),
            DetailWindows.bounds(ChartWindow(ChartRange.MONTH, today), Locale.UK),
        )
        assertEquals(
            WindowBounds(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)),
            DetailWindows.bounds(ChartWindow(ChartRange.YEAR, today), Locale.UK),
        )
    }

    @Test
    fun nextIsDisabledForAnyWindowContainingToday() {
        ChartRange.entries.forEach { range ->
            assertFalse(range.name, DetailWindows.canGoNext(ChartWindow(range, today), today, Locale.UK))
            val previous = DetailWindows.shift(ChartWindow(range, today), -1, today)
            assertTrue(range.name, DetailWindows.canGoNext(previous, today, Locale.UK))
        }
    }

    @Test
    fun yesterdayCanGoForwardToToday() {
        val yesterday = ChartWindow(ChartRange.DAY, today.minusDays(1))
        assertTrue(DetailWindows.canGoNext(yesterday, today, Locale.UK))
        assertEquals(ChartWindow(ChartRange.DAY, today), DetailWindows.shift(yesterday, 1, today))
    }

    @Test
    fun shiftNeverMovesPastToday() {
        val lastMonth = ChartWindow(ChartRange.MONTH, LocalDate.of(2026, 10, 31))
        // 31 October plus one month is 30 November, which is after today.
        assertEquals(today, DetailWindows.shift(lastMonth, 1, today).anchor)
    }

    @Test
    fun switchingRangeKeepsTheAnchorDay() {
        val march = ChartWindow(ChartRange.WEEK, LocalDate.of(2025, 3, 12))
        assertEquals(ChartWindow(ChartRange.MONTH, LocalDate.of(2025, 3, 12)), DetailWindows.switchRange(march, ChartRange.MONTH, today))
        assertEquals(ChartWindow(ChartRange.YEAR, LocalDate.of(2025, 3, 12)), DetailWindows.switchRange(march, ChartRange.YEAR, today))
    }

    @Test
    fun noonToNoonSleepBucketBelongsToItsMorning() {
        val bucket = SeriesPoint(
            LocalDate.of(2026, 11, 16).atTime(12, 0),
            LocalDate.of(2026, 11, 17).atTime(12, 0),
            value = 27000.0,
        )
        assertEquals(LocalDate.of(2026, 11, 17), DetailWindows.bucketDay(bucket))
        val day = SeriesPoint(LocalDate.of(2026, 11, 17).atStartOfDay(), LocalDate.of(2026, 11, 18).atStartOfDay(), 1.0)
        assertEquals(LocalDate.of(2026, 11, 17), DetailWindows.bucketDay(day))
    }

    @Test
    fun yearBucketOpensThatMonth() {
        val year = ChartWindow(ChartRange.YEAR, today)
        val march = SeriesPoint(LocalDate.of(2026, 3, 1).atStartOfDay(), LocalDate.of(2026, 4, 1).atStartOfDay(), 8000.0)
        assertEquals(
            DrillTarget.Window(ChartWindow(ChartRange.MONTH, LocalDate.of(2026, 3, 1))),
            DetailWindows.drillTarget(Metric.STEPS, year, march, today),
        )
    }

    @Test
    fun dayAndWeekBucketsOpenRecords() {
        val hour = SeriesPoint(today.atTime(14, 0), today.atTime(15, 0), 900.0)
        assertEquals(
            DrillTarget.Records(RecordType.DISTANCE, today),
            DetailWindows.drillTarget(Metric.DISTANCE, ChartWindow(ChartRange.DAY, today), hour, today),
        )
        assertEquals(
            DrillTarget.Records(RecordType.HEART_RATE, today),
            DetailWindows.drillTarget(Metric.RESTING_HEART_RATE, ChartWindow(ChartRange.DAY, today), hour, today),
        )
        val tuesday = SeriesPoint(LocalDate.of(2026, 11, 17).atStartOfDay(), LocalDate.of(2026, 11, 18).atStartOfDay(), 70.0)
        assertEquals(
            DrillTarget.Records(RecordType.WEIGHT, LocalDate.of(2026, 11, 17)),
            DetailWindows.drillTarget(Metric.WEIGHT, ChartWindow(ChartRange.WEEK, today), tuesday, today),
        )
    }
}
