// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.Series
import io.reddevs.tidemark.data.model.SeriesPoint
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** How a metric aggregates over time. */
internal enum class MetricKind {
    /** Summed per bucket: steps, active calories, distance. */
    CUMULATIVE,

    /** Summed per noon-to-noon bucket, attributed to the morning it ends: sleep duration. */
    SLEEP,

    /** Averaged per bucket, with min and max: resting heart rate, weight. */
    SAMPLED,
}

internal val Metric.kind: MetricKind
    get() = when (this) {
        Metric.STEPS, Metric.ACTIVE_CALORIES, Metric.DISTANCE -> MetricKind.CUMULATIVE
        Metric.SLEEP -> MetricKind.SLEEP
        Metric.RESTING_HEART_RATE, Metric.WEIGHT -> MetricKind.SAMPLED
    }

/** A half-open interval [start, end) in local time. */
internal data class LocalSpan(val start: LocalDateTime, val end: LocalDateTime)

/** One aggregation bucket's value for a metric, in SI units. [min] and [max] only for sampled metrics. */
internal data class Sample(val value: Double, val min: Double? = null, val max: Double? = null)

/** The Health Connect aggregation call a series needs. */
internal sealed interface SeriesQuery {
    /** aggregateGroupByDuration over [start, end) in hourly slices (the day view). */
    data class Hourly(val start: Instant, val end: Instant) : SeriesQuery {
        val slice: Duration get() = Duration.ofHours(1)
    }

    /** aggregateGroupByPeriod over [start, end) in device-local time, in slices of [period]. */
    data class ByPeriod(val start: LocalDateTime, val end: LocalDateTime, val period: Period) : SeriesQuery
}

/** Pure calendar math for chart windows and buckets (plan section 3). */
internal object ChartBuckets {
    /** Sleep buckets run from noon to noon. */
    val SLEEP_DAY_START: LocalTime = LocalTime.NOON

    /** The first day of [window] and the day after its last day. Weeks start on [locale]'s first day. */
    fun days(window: ChartWindow, locale: Locale): Pair<LocalDate, LocalDate> {
        val anchor = window.anchor
        return when (window.range) {
            ChartRange.DAY -> anchor to anchor.plusDays(1)
            ChartRange.WEEK -> {
                val first = anchor.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))
                first to first.plusWeeks(1)
            }
            ChartRange.MONTH -> anchor.withDayOfMonth(1).let { it to it.plusMonths(1) }
            ChartRange.YEAR -> anchor.withDayOfYear(1).let { it to it.plusYears(1) }
        }
    }

    /** Midnight to midnight on [day]. */
    fun daySpan(day: LocalDate) = LocalSpan(day.atStartOfDay(), day.plusDays(1).atStartOfDay())

    /** Noon the day before [morning] to noon on [morning]: the sleep that belongs to [morning]. */
    fun sleepSpan(morning: LocalDate) =
        LocalSpan(morning.minusDays(1).atTime(SLEEP_DAY_START), morning.atTime(SLEEP_DAY_START))

    /** The start of every hour of the local [day]: 23, 24 or 25 of them depending on DST. */
    fun hourStarts(day: LocalDate, zone: ZoneId): List<Instant> {
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        return generateSequence(start) { it.plus(Duration.ofHours(1)) }.takeWhile { it.isBefore(end) }.toList()
    }

    /** The local days in [first, endExclusive). */
    fun dayList(first: LocalDate, endExclusive: LocalDate): List<LocalDate> =
        generateSequence(first) { it.plusDays(1) }.takeWhile { it.isBefore(endExclusive) }.toList()
}

/**
 * Turns a [ChartWindow] into one aggregation query and back into a [Series].
 *
 * Buckets (plan section 3): the day view is hourly; week and month are daily; the year is monthly.
 * Sleep buckets run noon to noon and each is reported as the calendar day of the morning it ends
 * (point start = that day at 00:00), so every daily point's `start.toLocalDate()` is its day for
 * all metrics. The year view of cumulative metrics and sleep shows each month's daily average over
 * days with data, so it queries daily buckets and folds them into months.
 */
internal class SeriesPlan private constructor(
    val metric: Metric,
    val window: ChartWindow,
    val query: SeriesQuery,
    private val zone: ZoneId,
    private val firstDay: LocalDate,
    private val endDay: LocalDate,
) {
    private val kind = metric.kind

    /** Builds the series from hourly results keyed by slice start (for [SeriesQuery.Hourly]). */
    fun assembleHourly(samples: Map<Instant, Sample>): Series {
        val points = ChartBuckets.hourStarts(window.anchor, zone).map { hour ->
            point(hour.atZone(zone).toLocalDateTime(), hour.plus(Duration.ofHours(1)).atZone(zone).toLocalDateTime(), samples[hour])
        }
        return summarize(points, base = points)
    }

    /** Builds the series from period results keyed by their local start time (for [SeriesQuery.ByPeriod]). */
    fun assembleByPeriod(samples: Map<LocalDateTime, Sample>): Series {
        if (kind == MetricKind.SAMPLED && window.range == ChartRange.YEAR) {
            val points = months().map { month ->
                val start = month.atDay(1).atStartOfDay()
                point(start, month.plusMonths(1).atDay(1).atStartOfDay(), samples[start])
            }
            return summarize(points, base = points)
        }
        val daily = ChartBuckets.dayList(firstDay, endDay).map { day ->
            val span = ChartBuckets.daySpan(day)
            point(span.start, span.end, samples[bucketKey(day)])
        }
        if (window.range != ChartRange.YEAR) return summarize(daily, base = daily)

        val byMonth = daily.groupBy { YearMonth.from(it.start) }
        val points = months().map { month ->
            val withData = byMonth[month].orEmpty().mapNotNull { it.value }
            point(
                month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay(),
                if (withData.isEmpty()) null else Sample(withData.average()),
            )
        }
        return summarize(points, base = daily)
    }

    /** The local start time Health Connect reports for the daily bucket of [day]. */
    private fun bucketKey(day: LocalDate): LocalDateTime =
        if (kind == MetricKind.SLEEP) ChartBuckets.sleepSpan(day).start else day.atStartOfDay()

    private fun months(): List<YearMonth> =
        generateSequence(YearMonth.from(firstDay)) { it.plusMonths(1) }.takeWhile { it.atDay(1).isBefore(endDay) }.toList()

    private fun point(start: LocalDateTime, end: LocalDateTime, sample: Sample?) =
        SeriesPoint(start, end, sample?.value, sample?.min, sample?.max)

    /**
     * Summary over buckets with data. Cumulative metrics and sleep: total over the window, average
     * per [base] bucket with data (per hour in the day view, per day otherwise), min and max over the
     * displayed points. Sampled metrics: no total, the mean of the points' averages, and the lowest
     * min and highest max.
     */
    private fun summarize(points: List<SeriesPoint>, base: List<SeriesPoint>): Series {
        val values = points.mapNotNull { it.value }
        return when (kind) {
            MetricKind.CUMULATIVE, MetricKind.SLEEP -> {
                val baseValues = base.mapNotNull { it.value }
                val total = if (baseValues.isEmpty()) null else baseValues.sum()
                Series(
                    metric, window, points,
                    total = total,
                    average = total?.let { it / baseValues.size },
                    min = values.minOrNull(),
                    max = values.maxOrNull(),
                )
            }
            MetricKind.SAMPLED -> Series(
                metric, window, points,
                total = null,
                average = if (values.isEmpty()) null else values.average(),
                min = points.mapNotNull { it.min ?: it.value }.minOrNull(),
                max = points.mapNotNull { it.max ?: it.value }.maxOrNull(),
            )
        }
    }

    companion object {
        fun create(metric: Metric, window: ChartWindow, locale: Locale, zone: ZoneId): SeriesPlan {
            val (first, end) = ChartBuckets.days(window, locale)
            val kind = metric.kind
            val query = when {
                window.range == ChartRange.DAY && kind != MetricKind.SLEEP ->
                    SeriesQuery.Hourly(first.atStartOfDay(zone).toInstant(), end.atStartOfDay(zone).toInstant())
                window.range == ChartRange.YEAR && kind == MetricKind.SAMPLED ->
                    SeriesQuery.ByPeriod(first.atStartOfDay(), end.atStartOfDay(), Period.ofMonths(1))
                kind == MetricKind.SLEEP ->
                    SeriesQuery.ByPeriod(ChartBuckets.sleepSpan(first).start, ChartBuckets.sleepSpan(end).start, Period.ofDays(1))
                else -> SeriesQuery.ByPeriod(first.atStartOfDay(), end.atStartOfDay(), Period.ofDays(1))
            }
            return SeriesPlan(metric, window, query, zone, first, end)
        }
    }
}
