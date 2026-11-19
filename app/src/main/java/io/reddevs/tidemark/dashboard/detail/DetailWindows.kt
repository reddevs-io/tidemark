// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.detail

import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.data.model.SeriesPoint
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** The local dates a window covers: [start] inclusive, [endExclusive] exclusive. */
internal data class WindowBounds(val start: LocalDate, val endExclusive: LocalDate)

/** Where a tap on a chart bucket leads. */
sealed interface DrillTarget {
    /** Open the explorer on one record type and day. */
    data class Records(val type: RecordType, val day: LocalDate) : DrillTarget

    /** Switch the detail screen to another window (year view to month view). */
    data class Window(val window: ChartWindow) : DrillTarget
}

/** Pure window arithmetic for the detail screen. "Today" and the locale come from the caller. */
internal object DetailWindows {

    fun bounds(window: ChartWindow, locale: Locale): WindowBounds {
        val anchor = window.anchor
        return when (window.range) {
            ChartRange.DAY -> WindowBounds(anchor, anchor.plusDays(1))
            ChartRange.WEEK -> {
                val first = anchor.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))
                WindowBounds(first, first.plusWeeks(1))
            }
            ChartRange.MONTH -> {
                val first = anchor.withDayOfMonth(1)
                WindowBounds(first, first.plusMonths(1))
            }
            ChartRange.YEAR -> {
                val first = anchor.withDayOfYear(1)
                WindowBounds(first, first.plusYears(1))
            }
        }
    }

    /** True when the following window starts on or before [today]. */
    fun canGoNext(window: ChartWindow, today: LocalDate, locale: Locale): Boolean =
        !bounds(window, locale).endExclusive.isAfter(today)

    /** Moves the anchor by [steps] windows, never past [today]. */
    fun shift(window: ChartWindow, steps: Long, today: LocalDate): ChartWindow {
        val moved = when (window.range) {
            ChartRange.DAY -> window.anchor.plusDays(steps)
            ChartRange.WEEK -> window.anchor.plusWeeks(steps)
            ChartRange.MONTH -> window.anchor.plusMonths(steps)
            ChartRange.YEAR -> window.anchor.plusYears(steps)
        }
        return window.copy(anchor = minOf(moved, today))
    }

    /** Switching range keeps the anchor day, so a week view of last March becomes the month of last March. */
    fun switchRange(window: ChartWindow, range: ChartRange, today: LocalDate): ChartWindow =
        ChartWindow(range, minOf(window.anchor, today))

    /**
     * The calendar day a daily bucket stands for. Sleep buckets run noon to noon and belong to the
     * morning they end on; the midpoint of the bucket gives that day for both kinds of bucket.
     */
    fun bucketDay(point: SeriesPoint): LocalDate {
        val half = Duration.between(point.start, point.end).dividedBy(2)
        return point.start.plus(half).toLocalDate()
    }

    /**
     * What tapping [point] does. In the year view it opens that month; otherwise it opens the
     * explorer on the metric's record type and the bucket's day.
     */
    fun drillTarget(metric: Metric, window: ChartWindow, point: SeriesPoint, today: LocalDate): DrillTarget =
        when (window.range) {
            ChartRange.YEAR -> DrillTarget.Window(
                ChartWindow(ChartRange.MONTH, minOf(point.start.toLocalDate().withDayOfMonth(1), today)),
            )
            ChartRange.DAY -> DrillTarget.Records(dayViewRecordType(metric), window.anchor)
            ChartRange.WEEK, ChartRange.MONTH -> DrillTarget.Records(metric.recordType, bucketDay(point))
        }

    /**
     * The record type the day view plots. The resting heart rate day view draws HeartRate samples,
     * so its records are heart rate records.
     */
    fun dayViewRecordType(metric: Metric): RecordType =
        if (metric == Metric.RESTING_HEART_RATE) RecordType.HEART_RATE else metric.recordType
}
