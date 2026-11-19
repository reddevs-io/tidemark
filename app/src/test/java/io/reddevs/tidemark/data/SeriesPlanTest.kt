// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.Metric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.util.Locale

/**
 * Bucket math, tested as pure functions: connect-testing's fake can't aggregate, and the request
 * getters are internal to connect-client, so the plan is checked here and the repository tests
 * only check that stubbed aggregation results flow through it.
 */
class SeriesPlanTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val fr = Locale.FRANCE
    private val us = Locale.US

    private fun plan(metric: Metric, range: ChartRange, anchor: String, locale: Locale = fr) =
        SeriesPlan.create(metric, ChartWindow(range, LocalDate.parse(anchor)), locale, paris)

    private fun ldt(text: String) = LocalDateTime.parse(text)

    @Test
    fun weekStartsOnMondayInFranceAndSundayInTheUs() {
        val window = ChartWindow(ChartRange.WEEK, LocalDate.parse("2026-10-21")) // a Wednesday
        assertEquals(LocalDate.parse("2026-10-19") to LocalDate.parse("2026-10-26"), ChartBuckets.days(window, fr))
        assertEquals(LocalDate.parse("2026-10-18") to LocalDate.parse("2026-10-25"), ChartBuckets.days(window, us))

        // A Sunday anchor belongs to the week before in France and starts the week in the US.
        val sunday = ChartWindow(ChartRange.WEEK, LocalDate.parse("2026-10-25"))
        assertEquals(LocalDate.parse("2026-10-19"), ChartBuckets.days(sunday, fr).first)
        assertEquals(LocalDate.parse("2026-10-25"), ChartBuckets.days(sunday, us).first)
    }

    @Test
    fun weekQueriesDailyLocalBuckets() {
        assertEquals(
            SeriesQuery.ByPeriod(ldt("2026-10-19T00:00"), ldt("2026-10-26T00:00"), Period.ofDays(1)),
            plan(Metric.STEPS, ChartRange.WEEK, "2026-10-21", fr).query,
        )
        assertEquals(
            SeriesQuery.ByPeriod(ldt("2026-10-18T00:00"), ldt("2026-10-25T00:00"), Period.ofDays(1)),
            plan(Metric.STEPS, ChartRange.WEEK, "2026-10-21", us).query,
        )
    }

    @Test
    fun dstWeekInParisKeepsOneBucketPerLocalDay() {
        // Clocks go back on Sunday 2026-10-25 in Paris, so that day lasts 25 hours.
        val plan = plan(Metric.STEPS, ChartRange.WEEK, "2026-10-21", fr)
        val samples = (19..25).associate { day -> ldt("2026-10-%02dT00:00".format(day)) to Sample(day * 100.0) }
        val series = plan.assembleByPeriod(samples)

        assertEquals(7, series.points.size)
        series.points.forEachIndexed { i, point ->
            assertEquals(LocalDate.parse("2026-10-19").plusDays(i.toLong()).atStartOfDay(), point.start)
            assertEquals(point.start.plusDays(1), point.end)
            assertEquals((19 + i) * 100.0, point.value)
        }
        val sunday = series.points.last()
        assertEquals(Duration.ofHours(25), Duration.between(sunday.start.atZone(paris), sunday.end.atZone(paris)))
        assertEquals((19..25).sumOf { it * 100.0 }, series.total)

        // Spring forward (Sunday 2026-03-29) opens the US week: a 23-hour first bucket.
        val spring = plan(Metric.STEPS, ChartRange.WEEK, "2026-03-31", us).assembleByPeriod(emptyMap())
        val first = spring.points.first()
        assertEquals(ldt("2026-03-29T00:00"), first.start)
        assertEquals(Duration.ofHours(23), Duration.between(first.start.atZone(paris), first.end.atZone(paris)))
    }

    @Test
    fun dayViewHasOneBucketPerRealHourAcrossDst() {
        val autumn = plan(Metric.STEPS, ChartRange.DAY, "2026-10-25")
        assertEquals(
            SeriesQuery.Hourly(Instant.parse("2026-10-24T22:00:00Z"), Instant.parse("2026-10-25T23:00:00Z")),
            autumn.query,
        )
        // The second 02:00 (after clocks go back) is 01:00 UTC.
        val series = autumn.assembleHourly(mapOf(Instant.parse("2026-10-25T01:00:00Z") to Sample(42.0)))
        assertEquals(25, series.points.size)
        assertEquals(listOf(0, 1, 2, 2, 3), series.points.take(5).map { it.start.hour })
        assertNull(series.points[2].value)
        assertEquals(42.0, series.points[3].value)
        assertEquals(42.0, series.total)
        assertEquals(42.0, series.average)

        val spring = plan(Metric.STEPS, ChartRange.DAY, "2026-03-29").assembleHourly(emptyMap())
        assertEquals(23, spring.points.size)
        assertEquals(listOf(0, 1, 3), spring.points.take(3).map { it.start.hour })
        assertNull(spring.total)

        assertEquals(24, plan(Metric.STEPS, ChartRange.DAY, "2026-11-19").assembleHourly(emptyMap()).points.size)
    }

    @Test
    fun monthHasOneDailyBucketPerDay() {
        val plan = plan(Metric.DISTANCE, ChartRange.MONTH, "2028-02-17")
        assertEquals(SeriesQuery.ByPeriod(ldt("2028-02-01T00:00"), ldt("2028-03-01T00:00"), Period.ofDays(1)), plan.query)
        val series = plan.assembleByPeriod(mapOf(ldt("2028-02-29T00:00") to Sample(5000.0)))
        assertEquals(29, series.points.size)
        assertEquals(5000.0, series.points.last().value)
    }

    @Test
    fun yearOfCumulativeMetricShowsDailyAverageOverDaysWithData() {
        val plan = plan(Metric.STEPS, ChartRange.YEAR, "2026-06-15")
        assertEquals(SeriesQuery.ByPeriod(ldt("2026-01-01T00:00"), ldt("2027-01-01T00:00"), Period.ofDays(1)), plan.query)

        val series = plan.assembleByPeriod(
            mapOf(
                ldt("2026-01-05T00:00") to Sample(1000.0),
                ldt("2026-01-06T00:00") to Sample(3000.0),
                ldt("2026-03-01T00:00") to Sample(500.0),
            ),
        )
        assertEquals(12, series.points.size)
        assertEquals(ldt("2026-01-01T00:00"), series.points[0].start)
        assertEquals(ldt("2026-02-01T00:00"), series.points[0].end)
        assertEquals(2000.0, series.points[0].value) // (1000 + 3000) / 2 days with data, not / 31
        assertNull(series.points[1].value)
        assertEquals(500.0, series.points[2].value)
        assertEquals(4500.0, series.total)
        assertEquals(1500.0, series.average) // per day with data
        assertEquals(500.0, series.min)
        assertEquals(2000.0, series.max)
    }

    @Test
    fun yearOfSampledMetricUsesMonthlyBuckets() {
        val plan = plan(Metric.WEIGHT, ChartRange.YEAR, "2026-06-15")
        assertEquals(SeriesQuery.ByPeriod(ldt("2026-01-01T00:00"), ldt("2027-01-01T00:00"), Period.ofMonths(1)), plan.query)

        val series = plan.assembleByPeriod(
            mapOf(
                ldt("2026-01-01T00:00") to Sample(70.0, 69.0, 71.0),
                ldt("2026-06-01T00:00") to Sample(72.0, 71.5, 73.0),
            ),
        )
        assertEquals(12, series.points.size)
        assertEquals(69.0, series.points[0].min)
        assertEquals(73.0, series.points[5].max)
        assertNull(series.total)
        assertEquals(71.0, series.average)
        assertEquals(69.0, series.min)
        assertEquals(73.0, series.max)
    }

    @Test
    fun sleepBucketsRunNoonToNoonAndBelongToTheMorning() {
        val plan = plan(Metric.SLEEP, ChartRange.WEEK, "2026-10-21", fr)
        assertEquals(SeriesQuery.ByPeriod(ldt("2026-10-18T12:00"), ldt("2026-10-25T12:00"), Period.ofDays(1)), plan.query)

        val eightHours = Duration.ofHours(8).seconds.toDouble()
        val series = plan.assembleByPeriod(
            mapOf(
                ldt("2026-10-18T12:00") to Sample(eightHours), // the night ending Monday the 19th
                ldt("2026-10-24T12:00") to Sample(eightHours / 2), // the night ending Sunday the 25th
            ),
        )
        assertEquals(ldt("2026-10-19T00:00"), series.points.first().start)
        assertEquals(eightHours, series.points.first().value)
        assertEquals(ldt("2026-10-25T00:00"), series.points.last().start)
        assertEquals(eightHours / 2, series.points.last().value)
        assertEquals(eightHours * 1.5, series.total)
        assertEquals(eightHours * 0.75, series.average)
    }

    @Test
    fun sleepDayViewIsOneNoonToNoonBucket() {
        val plan = plan(Metric.SLEEP, ChartRange.DAY, "2026-11-19")
        assertEquals(SeriesQuery.ByPeriod(ldt("2026-11-18T12:00"), ldt("2026-11-19T12:00"), Period.ofDays(1)), plan.query)
        val series = plan.assembleByPeriod(mapOf(ldt("2026-11-18T12:00") to Sample(27000.0)))
        assertEquals(1, series.points.size)
        assertEquals(ldt("2026-11-19T00:00"), series.points.single().start)
        assertEquals(27000.0, series.total)
    }

    @Test
    fun sleepYearAttributesNewYearsNightToJanuary() {
        val plan = plan(Metric.SLEEP, ChartRange.YEAR, "2026-06-15")
        assertEquals(SeriesQuery.ByPeriod(ldt("2025-12-31T12:00"), ldt("2026-12-31T12:00"), Period.ofDays(1)), plan.query)
        val series = plan.assembleByPeriod(mapOf(ldt("2025-12-31T12:00") to Sample(30000.0)))
        assertEquals(30000.0, series.points.first().value)
        assertEquals(30000.0, series.total)
    }

    @Test
    fun sampledDayViewIsHourlyWithMinAndMax() {
        val plan = plan(Metric.RESTING_HEART_RATE, ChartRange.DAY, "2026-11-19")
        val hour = LocalDate.parse("2026-11-19").atTime(LocalTime.of(7, 0)).atZone(paris).toInstant()
        val series = plan.assembleHourly(mapOf(hour to Sample(55.0, 52.0, 58.0)))
        assertEquals(55.0, series.points[7].value)
        assertEquals(52.0, series.min)
        assertEquals(58.0, series.max)
        assertEquals(55.0, series.average)
        assertNull(series.total)
    }
}
