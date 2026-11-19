// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.detail

import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.HeartRateSample
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.data.model.Series
import io.reddevs.tidemark.data.model.SleepNight

data class MetricDetailUiState(
    val metric: Metric,
    val window: ChartWindow,
    val canGoNext: Boolean,
    val content: DetailContent,
)

enum class DetailError { PERMISSION_MISSING, RATE_LIMITED, UNAVAILABLE, GENERIC }

/** What the chart area shows. Values stay in SI units; the UI converts them through the formatter. */
sealed interface DetailContent {
    data object Loading : DetailContent

    data class Error(val error: DetailError, val type: RecordType? = null) : DetailContent

    /** The window has no data at all. */
    data object Empty : DetailContent

    /** Bucketed values: bars for cumulative metrics and sleep, lines for resting heart rate and weight. */
    data class Buckets(val series: Series, val style: ChartStyle) : DetailContent

    /** Day view of sleep: the night that ends on the window's morning. */
    data class SleepDay(val night: SleepNight) : DetailContent

    /**
     * Day view of resting heart rate: that day's HeartRate samples with the resting value as a
     * reference line. [samplesPermissionMissing] is true when only the HeartRate permission is
     * missing, so the resting value still shows.
     */
    data class HeartRateDay(
        val samples: List<HeartRateSample>,
        val resting: Double?,
        val samplesPermissionMissing: Boolean,
    ) : DetailContent
}

enum class ChartStyle { BARS, LINES }

internal fun chartStyle(metric: Metric): ChartStyle = when (metric) {
    Metric.STEPS, Metric.ACTIVE_CALORIES, Metric.DISTANCE, Metric.SLEEP -> ChartStyle.BARS
    Metric.RESTING_HEART_RATE, Metric.WEIGHT -> ChartStyle.LINES
}

enum class StatKind { TOTAL, DAILY_AVERAGE, AVERAGE, NIGHTLY_AVERAGE, RESTING, MIN, MAX }

/** One entry of the summary row, in the metric's SI unit. */
data class SummaryStat(val kind: StatKind, val value: Double)

/**
 * The summary row for a bucketed series. Cumulative metrics show a total except in the year view,
 * whose buckets are daily averages per month (plan section 3). Sleep and point-in-time metrics show
 * averages.
 */
internal fun summaryStats(metric: Metric, range: ChartRange, series: Series): List<SummaryStat> {
    val stats = when (metric) {
        Metric.STEPS, Metric.ACTIVE_CALORIES, Metric.DISTANCE -> when (range) {
            ChartRange.DAY -> listOf(StatKind.TOTAL to series.total, StatKind.MAX to series.max)
            ChartRange.WEEK, ChartRange.MONTH -> listOf(
                StatKind.TOTAL to series.total,
                StatKind.DAILY_AVERAGE to series.average,
                StatKind.MIN to series.min,
                StatKind.MAX to series.max,
            )
            ChartRange.YEAR -> listOf(
                StatKind.DAILY_AVERAGE to series.average,
                StatKind.MIN to series.min,
                StatKind.MAX to series.max,
            )
        }
        Metric.SLEEP -> listOf(
            StatKind.NIGHTLY_AVERAGE to series.average,
            StatKind.MIN to series.min,
            StatKind.MAX to series.max,
        )
        Metric.RESTING_HEART_RATE, Metric.WEIGHT -> listOf(
            StatKind.AVERAGE to series.average,
            StatKind.MIN to series.min,
            StatKind.MAX to series.max,
        )
    }
    return stats.mapNotNull { (kind, value) -> value?.let { SummaryStat(kind, it) } }
}

/** Summary for the resting heart rate day view: the resting value, then the samples' range. */
internal fun heartRateDayStats(content: DetailContent.HeartRateDay): List<SummaryStat> = buildList {
    content.resting?.let { add(SummaryStat(StatKind.RESTING, it)) }
    content.samples.minOfOrNull { it.bpm }?.let { add(SummaryStat(StatKind.MIN, it.toDouble())) }
    content.samples.maxOfOrNull { it.bpm }?.let { add(SummaryStat(StatKind.MAX, it.toDouble())) }
}
