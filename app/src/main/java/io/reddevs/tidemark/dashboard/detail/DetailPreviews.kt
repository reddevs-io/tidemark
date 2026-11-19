// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.detail

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.DataSource
import io.reddevs.tidemark.data.model.HeartRateSample
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.Series
import io.reddevs.tidemark.data.model.SeriesPoint
import io.reddevs.tidemark.data.model.SleepNight
import io.reddevs.tidemark.data.model.SleepStage
import io.reddevs.tidemark.data.model.SleepStageSpan
import io.reddevs.tidemark.format.LocaleMeasurementFormatter
import io.reddevs.tidemark.settings.UnitPreference
import io.reddevs.tidemark.ui.theme.TidemarkTheme
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.sin

/** Deterministic sample data for previews and UI tests. */
internal object DetailSamples {
    val today: LocalDate = LocalDate.of(2026, 11, 19)
    val zone: ZoneId = ZoneOffset.UTC

    fun state(metric: Metric, range: ChartRange): MetricDetailUiState {
        val window = ChartWindow(range, today)
        val content = when {
            range == ChartRange.DAY && metric == Metric.SLEEP -> DetailContent.SleepDay(night())
            range == ChartRange.DAY && metric == Metric.RESTING_HEART_RATE ->
                DetailContent.HeartRateDay(heartRate(today), resting = 57.0, samplesPermissionMissing = false)
            else -> DetailContent.Buckets(series(metric, window), chartStyle(metric))
        }
        return MetricDetailUiState(metric, window, canGoNext = false, content = content)
    }

    /** Buckets matching the plan's rules: hourly, daily (sleep noon to noon), or monthly. */
    fun series(metric: Metric, window: ChartWindow, locale: Locale = Locale.UK): Series {
        val bounds = DetailWindows.bounds(window, locale)
        val buckets: List<Pair<LocalDateTime, LocalDateTime>> = when (window.range) {
            ChartRange.DAY -> (0 until 24).map { h ->
                bounds.start.atStartOfDay().plusHours(h.toLong()) to bounds.start.atStartOfDay().plusHours(h + 1L)
            }
            ChartRange.WEEK, ChartRange.MONTH -> generateSequence(bounds.start) { it.plusDays(1) }
                .takeWhile { it.isBefore(bounds.endExclusive) }
                .map { day ->
                    if (metric == Metric.SLEEP) {
                        day.minusDays(1).atTime(12, 0) to day.atTime(12, 0)
                    } else {
                        day.atStartOfDay() to day.plusDays(1).atStartOfDay()
                    }
                }.toList()
            ChartRange.YEAR -> (0 until 12).map { m ->
                val first = bounds.start.plusMonths(m.toLong())
                first.atStartOfDay() to first.plusMonths(1).atStartOfDay()
            }
        }
        val points = buckets.mapIndexed { i, (start, end) ->
            val future = start.toLocalDate().isAfter(today)
            val wave = sin(i * 0.9)
            val value = when {
                future -> null
                window.range == ChartRange.DAY && metric in setOf(Metric.WEIGHT, Metric.RESTING_HEART_RATE) ->
                    if (i == 7) base(metric) else null
                window.range == ChartRange.DAY && (i < 6 || i > 22) -> 0.0
                window.range == ChartRange.DAY -> base(metric) / 14 * (1 + wave * 0.6)
                else -> base(metric) * (1 + wave * spread(metric))
            }
            val band = if (value != null && metric in setOf(Metric.WEIGHT, Metric.RESTING_HEART_RATE)) {
                value * 0.98 to value * 1.02
            } else {
                null
            }
            SeriesPoint(start, end, value, band?.first, band?.second)
        }
        val values = points.mapNotNull { it.value }
        return Series(
            metric = metric,
            window = window,
            points = points,
            total = values.sum(),
            average = values.average(),
            min = points.mapNotNull { it.min ?: it.value }.minOrNull(),
            max = points.mapNotNull { it.max ?: it.value }.maxOrNull(),
        )
    }

    private fun base(metric: Metric) = when (metric) {
        Metric.STEPS -> 8200.0
        Metric.ACTIVE_CALORIES -> 420.0
        Metric.DISTANCE -> 6100.0
        Metric.SLEEP -> 7.2 * 3600
        Metric.RESTING_HEART_RATE -> 58.0
        Metric.WEIGHT -> 72.4
    }

    private fun spread(metric: Metric) = when (metric) {
        Metric.RESTING_HEART_RATE -> 0.06
        Metric.WEIGHT -> 0.01
        Metric.SLEEP -> 0.12
        else -> 0.35
    }

    /** A night with every stage kind and two gaps no stage covers. */
    fun night(morning: LocalDate = today): SleepNight {
        val start = morning.minusDays(1).atTime(23, 5).atZone(zone).toInstant()
        val plan = listOf(
            SleepStage.AWAKE_IN_BED to 12L,
            SleepStage.LIGHT to 40L,
            SleepStage.DEEP to 55L,
            SleepStage.LIGHT to 30L,
            SleepStage.REM to 25L,
            null to 10L, // gap
            SleepStage.SLEEPING to 35L,
            SleepStage.AWAKE to 6L,
            SleepStage.OUT_OF_BED to 4L,
            SleepStage.LIGHT to 50L,
            SleepStage.DEEP to 30L,
            SleepStage.REM to 40L,
            SleepStage.UNKNOWN to 15L,
            null to 8L, // gap
            SleepStage.LIGHT to 35L,
            SleepStage.AWAKE to 10L,
        )
        var t = start
        val spans = buildList {
            plan.forEach { (stage, minutes) ->
                val end = t.plus(Duration.ofMinutes(minutes))
                if (stage != null) add(SleepStageSpan(t, end, stage))
                t = end
            }
        }
        return SleepNight("night-1", start, t, spans, DataSource("com.example.sleep"))
    }

    fun heartRate(day: LocalDate): List<HeartRateSample> {
        val start = day.atStartOfDay(zone).toInstant()
        return (0 until 24 * 12).map { i ->
            val minute = i * 5L
            val bpm = 62 + 20 * sin(minute / 180.0) + if (minute in 1080..1140) 50 else 0
            HeartRateSample(start.plus(Duration.ofMinutes(minute)), bpm.toLong())
        }
    }
}

internal class DetailPreviewProvider : PreviewParameterProvider<Pair<Metric, ChartRange>> {
    override val values: Sequence<Pair<Metric, ChartRange>> =
        Metric.entries.asSequence().flatMap { m -> ChartRange.entries.asSequence().map { m to it } }

    override fun getDisplayName(index: Int): String =
        values.elementAt(index).let { (m, r) -> "${m.name} ${r.name}" }
}

@Preview(name = "Light", showBackground = true, heightDp = 900)
@Preview(name = "Dark", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MetricDetailPreview(@PreviewParameter(DetailPreviewProvider::class) params: Pair<Metric, ChartRange>) {
    PreviewFrame { formatter ->
        MetricDetailScreen(DetailSamples.state(params.first, params.second), formatter, DetailSamples.zone, DetailActions())
    }
}

@Preview(name = "Hypnogram, French", showBackground = true, heightDp = 900, locale = "fr")
@Preview(name = "Hypnogram, 200% font", showBackground = true, heightDp = 1400, fontScale = 2f)
@Composable
private fun HypnogramPreview() {
    PreviewFrame { formatter ->
        MetricDetailScreen(DetailSamples.state(Metric.SLEEP, ChartRange.DAY), formatter, DetailSamples.zone, DetailActions())
    }
}

@Preview(name = "Empty and error", showBackground = true)
@Composable
private fun EmptyPreview() {
    PreviewFrame { formatter ->
        MetricDetailScreen(
            DetailSamples.state(Metric.STEPS, ChartRange.WEEK).copy(content = DetailContent.Error(DetailError.RATE_LIMITED)),
            formatter,
            DetailSamples.zone,
            DetailActions(),
        )
    }
}

@Composable
private fun PreviewFrame(content: @Composable (LocaleMeasurementFormatter) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    TidemarkTheme(dynamicColor = false) {
        content(LocaleMeasurementFormatter(context, locale, UnitPreference.SYSTEM))
    }
}
