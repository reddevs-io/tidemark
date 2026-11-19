// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import androidx.compose.ui.unit.dp
import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.SeriesPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SparklineWindowsTest {
    private fun month(year: Int, month: Int) = ChartWindow(ChartRange.MONTH, LocalDate.of(year, month, 1))

    private fun day(date: LocalDate, value: Double?) =
        SeriesPoint(date.atStartOfDay(), date.plusDays(1).atStartOfDay(), value)

    @Test
    fun sevenDaysInsideOneMonthNeedOneWindow() {
        val today = LocalDate.of(2026, 11, 19)
        assertEquals(listOf(month(2026, 11)), SparklineWindows.windows(SparklineWindows.firstDay(Metric.STEPS, today), today))
    }

    @Test
    fun sevenDaysAcrossAMonthAndYearBoundary() {
        val today = LocalDate.of(2027, 1, 3)
        assertEquals(LocalDate.of(2026, 12, 28), SparklineWindows.firstDay(Metric.SLEEP, today))
        assertEquals(
            listOf(month(2026, 12), month(2027, 1)),
            SparklineWindows.windows(SparklineWindows.firstDay(Metric.SLEEP, today), today),
        )
    }

    @Test
    fun weightUsesThirtyDays() {
        val today = LocalDate.of(2026, 3, 1)
        assertEquals(30, SparklineWindows.days(Metric.WEIGHT))
        assertEquals(LocalDate.of(2026, 1, 31), SparklineWindows.firstDay(Metric.WEIGHT, today))
        assertEquals(
            listOf(month(2026, 1), month(2026, 2), month(2026, 3)),
            SparklineWindows.windows(SparklineWindows.firstDay(Metric.WEIGHT, today), today),
        )
    }

    @Test
    fun densifyFillsMissingDaysAndDropsOutOfRangeBuckets() {
        val from = LocalDate.of(2026, 11, 13)
        val to = LocalDate.of(2026, 11, 19)
        val points = listOf(
            day(LocalDate.of(2026, 11, 12), 1.0),
            day(LocalDate.of(2026, 11, 13), 2.0),
            day(LocalDate.of(2026, 11, 15), null),
            day(LocalDate.of(2026, 11, 16), 4.0),
            day(LocalDate.of(2026, 11, 19), 7.0),
            day(LocalDate.of(2026, 11, 20), 8.0),
        )
        assertEquals(
            SparklineData(from, listOf(2.0, null, null, 4.0, null, null, 7.0)),
            SparklineWindows.densify(points, from, to),
        )
    }

    @Test
    fun noonToNoonSleepBucketsBelongToTheMorning() {
        val morning = LocalDate.of(2026, 11, 19)
        val bucket = SeriesPoint(morning.minusDays(1).atTime(12, 0), morning.atTime(12, 0), 27000.0)
        assertEquals(morning, SparklineWindows.bucketDay(bucket))
        assertEquals(27000.0, SparklineWindows.densify(listOf(bucket), morning, morning).values.single())
    }

    @Test
    fun sparklineHasDataOnlyWithAPositiveValue() {
        assertFalse(SparklineData(LocalDate.EPOCH, listOf(null, 0.0)).hasData)
        assertTrue(SparklineData(LocalDate.EPOCH, listOf(null, 3.0)).hasData)
    }

    @Test
    fun tileColumnsFollowWidthAndFontScale() {
        val phone = 360.dp - 32.dp
        assertEquals(2, tileColumns(phone, 1f))
        assertEquals(1, tileColumns(phone, 1.3f))
        assertEquals(1, tileColumns(phone, 2f))
        assertEquals(2, tileColumns(phone, 0.85f))
        assertEquals(3, tileColumns(840.dp - 32.dp, 1f))
    }
}
