// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.detail

import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.HeartRateSample
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.Series
import io.reddevs.tidemark.format.DateStyle
import io.reddevs.tidemark.format.MeasurementFormatter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

class DetailSummaryTest {
    private val window = ChartWindow(ChartRange.WEEK, LocalDate.of(2026, 11, 19))
    private val series = Series(Metric.STEPS, window, emptyList(), total = 70.0, average = 10.0, min = 2.0, max = 20.0)

    @Test
    fun cumulativeMetricsShowTotalsExceptInTheYearView() {
        assertEquals(
            listOf(StatKind.TOTAL, StatKind.DAILY_AVERAGE, StatKind.MIN, StatKind.MAX),
            summaryStats(Metric.STEPS, ChartRange.WEEK, series).map { it.kind },
        )
        assertEquals(
            listOf(StatKind.DAILY_AVERAGE, StatKind.MIN, StatKind.MAX),
            summaryStats(Metric.DISTANCE, ChartRange.YEAR, series).map { it.kind },
        )
        assertEquals(listOf(StatKind.TOTAL, StatKind.MAX), summaryStats(Metric.ACTIVE_CALORIES, ChartRange.DAY, series).map { it.kind })
    }

    @Test
    fun sleepAndPointMetricsShowAverages() {
        assertEquals(
            listOf(StatKind.NIGHTLY_AVERAGE, StatKind.MIN, StatKind.MAX),
            summaryStats(Metric.SLEEP, ChartRange.MONTH, series).map { it.kind },
        )
        assertEquals(
            listOf(StatKind.AVERAGE, StatKind.MIN, StatKind.MAX),
            summaryStats(Metric.WEIGHT, ChartRange.WEEK, series).map { it.kind },
        )
    }

    @Test
    fun missingValuesAreLeftOut() {
        val sparse = series.copy(total = null, min = null)
        assertEquals(listOf(StatKind.DAILY_AVERAGE, StatKind.MAX), summaryStats(Metric.STEPS, ChartRange.WEEK, sparse).map { it.kind })
    }

    @Test
    fun heartRateDayStatsComeFromRestingValueAndSamples() {
        val samples = listOf(HeartRateSample(Instant.EPOCH, 90), HeartRateSample(Instant.EPOCH, 55))
        assertEquals(
            listOf(SummaryStat(StatKind.RESTING, 58.0), SummaryStat(StatKind.MIN, 55.0), SummaryStat(StatKind.MAX, 90.0)),
            heartRateDayStats(DetailContent.HeartRateDay(samples, 58.0, false)),
        )
    }

    @Test
    fun chartScaleUsesTheFormatterUnits() {
        val imperial = ImperialFormatter()
        val distance = ChartScale(Metric.DISTANCE, imperial)
        assertEquals(1.0, distance.toChart(1609.344), 1e-9)
        assertEquals("1.0 mi", distance.label(1.0))
        assertEquals(1609.344, distance.toSi(1.0), 1e-9)

        val weight = ChartScale(Metric.WEIGHT, imperial)
        assertEquals(220.462, weight.toChart(100.0), 1e-3)
        assertEquals("100.0 kg as lb", weight.label(220.462))

        val sleep = ChartScale(Metric.SLEEP, imperial)
        assertEquals(7.5, sleep.toChart(27000.0), 1e-9)
        assertEquals("PT7H30M", sleep.label(7.5))
    }

    @Test
    fun niceCeilRoundsUpToRoundNumbers() {
        assertEquals(1.0, niceCeil(0.7), 1e-9)
        assertEquals(2500.0, niceCeil(2100.0), 1e-9)
        assertEquals(5000.0, niceCeil(3000.0), 1e-9)
        assertEquals(10.0, niceCeil(10.0), 1e-9)
    }

    /** Records which formatter call produced each label. */
    private class ImperialFormatter : MeasurementFormatter {
        override val locale: Locale = Locale.US
        override fun steps(count: Long) = "$count"
        override fun distance(meters: Double) = "%.1f mi".format(Locale.US, meters / 1609.344)
        override fun energy(kilocalories: Double) = "$kilocalories kcal"
        override fun mass(kilograms: Double) = "%.1f kg as lb".format(Locale.US, kilograms)
        override fun heartRate(bpm: Double) = "$bpm bpm"
        override fun duration(duration: Duration) = duration.toString()
        override fun metricValue(metric: Metric, value: Double) = when (metric) {
            Metric.DISTANCE -> distance(value)
            Metric.WEIGHT -> mass(value)
            else -> value.toString()
        }
        override fun date(date: LocalDate, style: DateStyle) = date.toString()
        override fun time(instant: Instant) = instant.toString()
        override fun dateTime(instant: Instant) = instant.toString()
        override fun rangeLabel(window: ChartWindow) = window.toString()
        override fun distanceUnitLabel() = "mi"
        override fun massUnitLabel() = "lb"
        override fun toDisplayDistance(meters: Double) = meters / 1609.344
        override fun toDisplayMass(kilograms: Double) = kilograms * 2.20462
    }
}
