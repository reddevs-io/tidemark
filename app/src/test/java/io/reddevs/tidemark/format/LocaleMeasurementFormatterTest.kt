// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.format

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.settings.UnitPreference
import io.reddevs.tidemark.settings.UnitPreference.IMPERIAL
import io.reddevs.tidemark.settings.UnitPreference.METRIC
import io.reddevs.tidemark.settings.UnitPreference.SYSTEM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * Expected strings come from ICU and CLDR data in Robolectric's SDK 34 runtime. Spaces are
 * normalized because ICU uses no-break (U+00A0), narrow no-break (U+202F) and thin (U+2009)
 * spaces that vary between ICU versions; [frenchGroupingSeparatorIsANoBreakSpace] checks one.
 */
@RunWith(AndroidJUnit4::class)
class LocaleMeasurementFormatterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Thursday 19 November 2026, 21:05 in Paris. */
    private val clock = Clock.fixed(Instant.parse("2026-11-19T20:05:00Z"), ZoneId.of("Europe/Paris"))
    private val today = LocalDate.of(2026, 11, 19)

    private fun formatter(tag: String, unit: UnitPreference = SYSTEM, clock: Clock = this.clock) =
        LocaleMeasurementFormatter(context, Locale.forLanguageTag(tag), unit, clock)

    private fun String.normalized() = replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2009', ' ')

    private fun assertFormats(expected: String, actual: String, message: String) =
        assertEquals(message, expected, actual.normalized())

    private val locales = listOf("en-US", "en-GB", "fr-FR", "fr-CA")

    @Test
    fun distanceFollowsLocaleAndOverride() {
        val expected = mapOf(
            "en-US" to listOf("7.7 mi", "12.3 km", "7.7 mi"),
            "en-GB" to listOf("7.7 mi", "12.3 km", "7.7 mi"),
            "fr-FR" to listOf("12,3 km", "12,3 km", "7,7 mi"),
            "fr-CA" to listOf("12,3 km", "12,3 km", "7,7 mi"),
        )
        for (tag in locales) {
            UnitPreference.entries.forEachIndexed { i, unit ->
                assertFormats(expected.getValue(tag)[i], formatter(tag, unit).distance(12_345.0), "$tag $unit")
            }
        }
    }

    @Test
    fun shortDistancesKeepTwoDecimalsAndLongOnesNone() {
        assertFormats("0.35 km", formatter("en-GB", METRIC).distance(350.0), "short metric")
        assertFormats("0,22 mi", formatter("fr-FR", IMPERIAL).distance(350.0), "short imperial")
        assertFormats("123 km", formatter("en-GB", METRIC).distance(123_400.0), "long")
        assertFormats("0 km", formatter("en-US", METRIC).distance(0.0), "zero")
    }

    @Test
    fun massFollowsLocaleAndOverride() {
        val expected = mapOf(
            "en-US" to listOf("159.8 lb", "72.5 kg", "159.8 lb"),
            "en-GB" to listOf("72.5 kg", "72.5 kg", "159.8 lb"),
            "fr-FR" to listOf("72,5 kg", "72,5 kg", "159,8 lb"),
            "fr-CA" to listOf("72,5 kg", "72,5 kg", "159,8 lb"),
        )
        for (tag in locales) {
            UnitPreference.entries.forEachIndexed { i, unit ->
                assertFormats(expected.getValue(tag)[i], formatter(tag, unit).mass(72.48), "$tag $unit")
            }
        }
    }

    @Test
    fun energyIsAlwaysKilocalories() {
        for (tag in locales) {
            for (unit in UnitPreference.entries) {
                assertFormats("412 kcal", formatter(tag, unit).energy(412.4), "$tag $unit")
            }
        }
        assertFormats("1,250 kcal", formatter("en-US", IMPERIAL).energy(1_250.0), "grouping en")
        assertFormats("1 250 kcal", formatter("fr-FR").energy(1_250.0), "grouping fr")
    }

    @Test
    fun stepsUseTheLocaleGroupingSeparator() {
        val expected = mapOf("en-US" to "12,345", "en-GB" to "12,345", "fr-FR" to "12 345", "fr-CA" to "12 345")
        for (tag in locales) {
            for (unit in UnitPreference.entries) {
                assertFormats(expected.getValue(tag), formatter(tag, unit).steps(12_345), "$tag $unit")
            }
        }
        assertFormats("1,234,567", formatter("en-US").steps(1_234_567), "millions")
        assertFormats("845", formatter("fr-FR").steps(845), "no grouping below 1000")
    }

    @Test
    fun frenchGroupingSeparatorIsANoBreakSpace() {
        // A plain space would let "12 345" wrap across two lines.
        val grouped = formatter("fr-FR").steps(12_345)
        assertEquals(5, grouped.count { it.isDigit() })
        val separator = grouped.single { !it.isDigit() }
        assertTrue("separator was U+%04X".format(separator.code), separator == '\u202F' || separator == '\u00A0')
    }

    @Test
    fun durationsReadHoursAndMinutes() {
        for (tag in locales) {
            for (unit in UnitPreference.entries) {
                val f = formatter(tag, unit)
                assertFormats("7 h 32 min", f.duration(Duration.ofHours(7).plusMinutes(32)), "$tag $unit")
                assertFormats("45 min", f.duration(Duration.ofMinutes(45)), "$tag minutes only")
                assertFormats("8 h", f.duration(Duration.ofHours(8)), "$tag whole hours")
                assertFormats("0 min", f.duration(Duration.ZERO), "$tag zero")
            }
        }
    }

    @Test
    fun durationsRoundToTheNearestMinuteAndClampNegatives() {
        val f = formatter("en-US")
        assertFormats("7 h 32 min", f.duration(Duration.ofSeconds(7 * 3600 + 31 * 60 + 30)), "rounds up at 30 s")
        assertFormats("7 h 31 min", f.duration(Duration.ofSeconds(7 * 3600 + 31 * 60 + 29)), "rounds down")
        assertFormats("0 min", f.duration(Duration.ofMinutes(-5)), "negative")
    }

    @Test
    fun heartRateIsBeatsPerMinute() {
        for (tag in locales) {
            assertFormats("62 bpm", formatter(tag).heartRate(61.6), tag)
        }
    }

    @Test
    fun metricValueUsesTheMatchingFormatAndSiInput() {
        val f = formatter("en-US", METRIC)
        assertFormats("12,345", f.metricValue(Metric.STEPS, 12_345.0), "steps")
        assertFormats("412 kcal", f.metricValue(Metric.ACTIVE_CALORIES, 412.0), "calories")
        assertFormats("12.3 km", f.metricValue(Metric.DISTANCE, 12_345.0), "distance in meters")
        assertFormats("7 h 32 min", f.metricValue(Metric.SLEEP, 27_120.0), "sleep in seconds")
        assertFormats("58 bpm", f.metricValue(Metric.RESTING_HEART_RATE, 58.0), "resting HR")
        assertFormats("72.5 kg", f.metricValue(Metric.WEIGHT, 72.48), "weight in kg")
    }

    @Test
    fun unitLabelsAndAxisConversionsMatchTheUnitSystem() {
        data class Case(val tag: String, val unit: UnitPreference, val distance: String, val mass: String)
        val cases = listOf(
            Case("en-US", SYSTEM, "mi", "lb"),
            Case("en-GB", SYSTEM, "mi", "kg"),
            Case("fr-FR", SYSTEM, "km", "kg"),
            Case("fr-CA", SYSTEM, "km", "kg"),
            Case("en-US", METRIC, "km", "kg"),
            Case("fr-FR", IMPERIAL, "mi", "lb"),
        )
        for (case in cases) {
            val f = formatter(case.tag, case.unit)
            assertEquals(case.toString(), case.distance, f.distanceUnitLabel())
            assertEquals(case.toString(), case.mass, f.massUnitLabel())
            val km = case.distance == "km"
            assertEquals(case.toString(), if (km) 1.0 else 1.0 / 1.609344, f.toDisplayDistance(1_000.0), 1e-9)
            val kg = case.mass == "kg"
            assertEquals(case.toString(), if (kg) 1.0 else 2.20462262, f.toDisplayMass(1.0), 1e-6)
        }
    }

    @Test
    fun datesInEveryStyle() {
        val date = LocalDate.of(2026, 11, 18)
        val expected = mapOf(
            "en-US" to listOf("11/18/26", "Nov 18, 2026", "November 18, 2026", "Nov 18"),
            "en-GB" to listOf("18/11/2026", "18 Nov 2026", "18 November 2026", "18 Nov"),
            "fr-FR" to listOf("18/11/2026", "18 nov. 2026", "18 novembre 2026", "18 nov."),
            "fr-CA" to listOf("2026-11-18", "18 nov. 2026", "18 novembre 2026", "18 nov."),
        )
        for (tag in locales) {
            val f = formatter(tag)
            DateStyle.entries.forEachIndexed { i, style ->
                assertFormats(expected.getValue(tag)[i], f.date(date, style), "$tag $style")
            }
            assertFormats(expected.getValue(tag)[1], f.date(date), "$tag default is MEDIUM")
        }
    }

    @Test
    fun timesFollowTheLocaleHourCycleInTheClockZone() {
        val now = clock.instant()
        val expected = mapOf(
            "en-US" to ("9:05 PM" to "Nov 19, 2026, 9:05 PM"),
            "en-GB" to ("21:05" to "19 Nov 2026, 21:05"),
            "fr-FR" to ("21:05" to "19 nov. 2026, 21:05"),
            "fr-CA" to ("21 h 05" to "19 nov. 2026, 21 h 05"),
        )
        for (tag in locales) {
            val f = formatter(tag)
            assertFormats(expected.getValue(tag).first, f.time(now), "$tag time")
            assertFormats(expected.getValue(tag).second, f.dateTime(now), "$tag dateTime")
        }
    }

    @Test
    fun timesFollowTheDevice24HourSetting() {
        val now = clock.instant()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        assertFormats("21:05", formatter("en-US").time(now), "24-hour en-US")
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        assertFormats("9:05 PM", formatter("fr-FR").time(now).uppercase(), "12-hour fr-FR")
    }

    @Test
    fun timesWorkWithAFixedOffsetZone() {
        val now = Instant.parse("2026-11-19T20:05:00Z")
        val utc = Clock.fixed(now, ZoneOffset.UTC)
        val plusTwo = Clock.fixed(now, ZoneOffset.ofHours(2))
        assertFormats("20:05", formatter("fr-FR", clock = utc).time(now), "UTC")
        assertFormats("22:05", formatter("fr-FR", clock = plusTwo).time(now), "+02:00")
    }

    @Test
    fun dayRangeLabels() {
        val expected = mapOf(
            "en-US" to listOf("Today", "Yesterday", "Mon, Nov 2", "Mon, Mar 3, 2025"),
            "en-GB" to listOf("Today", "Yesterday", "Mon, 2 Nov", "Mon, 3 Mar 2025"),
            "fr-FR" to listOf("Aujourd'hui", "Hier", "lun. 2 nov.", "lun. 3 mars 2025"),
            "fr-CA" to listOf("Aujourd'hui", "Hier", "lun. 2 nov.", "lun. 3 mars 2025"),
        )
        val days = listOf(today, today.minusDays(1), LocalDate.of(2026, 11, 2), LocalDate.of(2025, 3, 3))
        for (tag in locales) {
            val f = formatter(tag)
            days.forEachIndexed { i, day ->
                assertFormats(expected.getValue(tag)[i], f.rangeLabel(ChartWindow(ChartRange.DAY, day)), "$tag $day")
            }
        }
    }

    @Test
    fun weekRangeLabelsStartOnTheLocaleFirstDay() {
        // 18 November 2026 is a Wednesday. The US and Canada start weeks on Sunday, the UK and France on Monday.
        val wednesday = LocalDate.of(2026, 11, 18)
        val expected = mapOf(
            "en-US" to listOf("Nov 15 – 21, 2026", "Nov 29 – Dec 5, 2026", "Dec 27, 2026 – Jan 2, 2027"),
            "en-GB" to listOf("16–22 Nov 2026", "30 Nov – 6 Dec 2026", "28 Dec 2026 – 3 Jan 2027"),
            "fr-FR" to listOf("16–22 nov. 2026", "30 nov. – 6 déc. 2026", "28 déc. 2026 – 3 janv. 2027"),
            "fr-CA" to listOf("15–21 nov. 2026", "29 nov. – 5 déc. 2026", "27 déc. 2026 – 2 janv. 2027"),
        )
        val anchors = listOf(wednesday, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31))
        for (tag in locales) {
            val f = formatter(tag)
            anchors.forEachIndexed { i, anchor ->
                assertFormats(expected.getValue(tag)[i], f.rangeLabel(ChartWindow(ChartRange.WEEK, anchor)), "$tag $anchor")
            }
        }
    }

    @Test
    fun monthAndYearRangeLabels() {
        val expected = mapOf(
            "en-US" to "November 2026",
            "en-GB" to "November 2026",
            "fr-FR" to "novembre 2026",
            "fr-CA" to "novembre 2026",
        )
        for (tag in locales) {
            val f = formatter(tag)
            assertFormats(expected.getValue(tag), f.rangeLabel(ChartWindow(ChartRange.MONTH, LocalDate.of(2026, 11, 30))), "$tag month")
            assertFormats("2026", f.rangeLabel(ChartWindow(ChartRange.YEAR, LocalDate.of(2026, 1, 1))), "$tag year")
        }
    }

    @Test
    fun everyRangeHasALabelUnderEveryPreference() {
        for (tag in locales) {
            for (unit in UnitPreference.entries) {
                for (range in ChartRange.entries) {
                    val label = formatter(tag, unit).rangeLabel(ChartWindow(range, today.minusDays(40)))
                    assertTrue("$tag $unit $range", label.isNotBlank())
                }
            }
        }
    }
}
