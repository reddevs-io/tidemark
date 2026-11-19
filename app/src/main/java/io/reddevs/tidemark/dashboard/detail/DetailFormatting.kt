// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.detail

import androidx.annotation.StringRes
import io.reddevs.tidemark.R
import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.SeriesPoint
import io.reddevs.tidemark.data.model.SleepStage
import io.reddevs.tidemark.format.DateStyle
import io.reddevs.tidemark.format.MeasurementFormatter
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

/**
 * Converts a metric's SI values into the numbers a chart axis plots, and back into labels. Every
 * label goes through [MeasurementFormatter], so units follow the settings.
 *
 * Distance and mass conversions are linear, so the display factor is the converted value of one
 * SI unit. Sleep plots hours.
 */
internal class ChartScale(private val metric: Metric, private val formatter: MeasurementFormatter) {
    private val factor: Double = when (metric) {
        Metric.DISTANCE -> formatter.toDisplayDistance(1.0)
        Metric.WEIGHT -> formatter.toDisplayMass(1.0)
        Metric.SLEEP -> 1.0 / SECONDS_PER_HOUR
        Metric.STEPS, Metric.ACTIVE_CALORIES, Metric.RESTING_HEART_RATE -> 1.0
    }

    fun toChart(si: Double): Double = si * factor

    fun toSi(chart: Double): Double = if (factor == 0.0) chart else chart / factor

    /** Axis and value label for a plotted (display unit) value. */
    fun label(chart: Double): String = value(toSi(chart))

    /** Label for an SI value. */
    fun value(si: Double): String = when (metric) {
        Metric.STEPS -> formatter.steps(si.roundToLong())
        Metric.SLEEP -> formatter.duration(Duration.ofSeconds(si.roundToLong()))
        else -> formatter.metricValue(metric, si)
    }

    private companion object {
        const val SECONDS_PER_HOUR = 3600.0
    }
}

/** Short x-axis labels and longer list labels for chart buckets. */
internal class BucketLabels(private val formatter: MeasurementFormatter, private val zone: ZoneId) {
    private val weekday = DateTimeFormatter.ofPattern("EEE", formatter.locale)
    private val dayOfMonth = DateTimeFormatter.ofPattern("d", formatter.locale)
    private val monthShort = DateTimeFormatter.ofPattern("LLL", formatter.locale)
    private val monthLong = DateTimeFormatter.ofPattern("LLLL yyyy", formatter.locale)

    fun axis(point: SeriesPoint, range: ChartRange): String = when (range) {
        ChartRange.DAY -> time(point.start)
        ChartRange.WEEK -> weekday.format(DetailWindows.bucketDay(point))
        ChartRange.MONTH -> dayOfMonth.format(DetailWindows.bucketDay(point))
        ChartRange.YEAR -> monthShort.format(point.start)
    }

    fun full(point: SeriesPoint, range: ChartRange): String = when (range) {
        ChartRange.DAY -> time(point.start)
        ChartRange.WEEK, ChartRange.MONTH -> formatter.date(DetailWindows.bucketDay(point), DateStyle.MEDIUM)
        ChartRange.YEAR -> monthLong.format(point.start).replaceFirstChar { it.titlecase(formatter.locale) }
    }

    fun time(local: LocalDateTime): String = formatter.time(local.atZone(zone).toInstant())
}

/** How many buckets apart x-axis labels sit, so they don't overlap on a phone. */
internal fun axisLabelSpacing(range: ChartRange): Int = when (range) {
    ChartRange.DAY -> 6
    ChartRange.WEEK -> 1
    ChartRange.MONTH -> 7
    ChartRange.YEAR -> 2
}

@StringRes
internal fun metricTitle(metric: Metric): Int = when (metric) {
    Metric.STEPS -> R.string.detail_title_steps
    Metric.ACTIVE_CALORIES -> R.string.detail_title_active_calories
    Metric.DISTANCE -> R.string.detail_title_distance
    Metric.SLEEP -> R.string.detail_title_sleep
    Metric.RESTING_HEART_RATE -> R.string.detail_title_resting_heart_rate
    Metric.WEIGHT -> R.string.detail_title_weight
}

@StringRes
internal fun rangeName(range: ChartRange): Int = when (range) {
    ChartRange.DAY -> R.string.detail_range_day
    ChartRange.WEEK -> R.string.detail_range_week
    ChartRange.MONTH -> R.string.detail_range_month
    ChartRange.YEAR -> R.string.detail_range_year
}

@StringRes
internal fun statLabel(kind: StatKind): Int = when (kind) {
    StatKind.TOTAL -> R.string.detail_stat_total
    StatKind.DAILY_AVERAGE -> R.string.detail_stat_daily_average
    StatKind.AVERAGE -> R.string.detail_stat_average
    StatKind.NIGHTLY_AVERAGE -> R.string.detail_stat_nightly_average
    StatKind.RESTING -> R.string.detail_stat_resting
    StatKind.MIN -> R.string.detail_stat_min
    StatKind.MAX -> R.string.detail_stat_max
}

@StringRes
internal fun stageName(stage: SleepStage): Int = when (stage) {
    SleepStage.AWAKE -> R.string.detail_stage_awake
    SleepStage.LIGHT -> R.string.detail_stage_light
    SleepStage.DEEP -> R.string.detail_stage_deep
    SleepStage.REM -> R.string.detail_stage_rem
    SleepStage.SLEEPING -> R.string.detail_stage_sleeping
    SleepStage.OUT_OF_BED -> R.string.detail_stage_out_of_bed
    SleepStage.AWAKE_IN_BED -> R.string.detail_stage_awake_in_bed
    SleepStage.UNKNOWN -> R.string.detail_stage_unknown
}
