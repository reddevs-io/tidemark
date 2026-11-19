// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.SeriesPoint
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Bucket math for the tile sparklines: a rolling run of days ending today, built from month
 * windows (daily buckets). Month windows don't depend on the locale's first day of the week, and
 * a 7-day run needs a second call only in the first six days of a month.
 */
internal object SparklineWindows {

    fun days(metric: Metric): Int = if (metric == Metric.WEIGHT) 30 else 7

    fun firstDay(metric: Metric, today: LocalDate): LocalDate = today.minusDays(days(metric) - 1L)

    /** The month windows that together cover [from] to [to], oldest first. */
    fun windows(from: LocalDate, to: LocalDate): List<ChartWindow> {
        require(!to.isBefore(from)) { "to ($to) is before from ($from)" }
        val windows = mutableListOf<ChartWindow>()
        var month = YearMonth.from(from)
        val last = YearMonth.from(to)
        while (!month.isAfter(last)) {
            windows += ChartWindow(ChartRange.MONTH, month.atDay(1))
            month = month.plusMonths(1)
        }
        return windows
    }

    /**
     * One value per day from [from] to [to]. A bucket belongs to the day of its midpoint, which
     * puts both midnight-to-midnight buckets and noon-to-noon sleep buckets on the right day.
     */
    fun densify(points: List<SeriesPoint>, from: LocalDate, to: LocalDate): SparklineData {
        val byDay = points.filter { it.value != null }.associateBy { bucketDay(it) }
        val count = ChronoUnit.DAYS.between(from, to).toInt() + 1
        val values = List(count) { index -> byDay[from.plusDays(index.toLong())]?.value }
        return SparklineData(from, values)
    }

    fun bucketDay(point: SeriesPoint): LocalDate {
        val half = Duration.between(point.start, point.end).dividedBy(2)
        return point.start.plus(half).toLocalDate()
    }
}
