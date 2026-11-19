// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.format

import android.content.Context
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.settings.UnitPreference
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * [MeasurementFormatter] built on ICU and java.time.
 *
 * T-001 stub: metric units only, minimal localization. T-004 owns this file and implements the
 * unit-system rules from plan section 3.
 */
@Suppress("UNUSED_PARAMETER")
class LocaleMeasurementFormatter(
    context: Context,
    override val locale: Locale,
    private val unitPreference: UnitPreference,
) : MeasurementFormatter {

    private val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    private val zone get() = ZoneId.systemDefault()

    override fun steps(count: Long): String = NumberFormat.getIntegerInstance(locale).format(count)

    override fun distance(meters: Double): String = "${number.format(meters / 1000)} km"

    override fun energy(kilocalories: Double): String = "${number.format(kilocalories)} kcal"

    override fun mass(kilograms: Double): String = "${number.format(kilograms)} kg"

    override fun heartRate(bpm: Double): String = "${number.format(bpm)} bpm"

    override fun duration(duration: Duration): String = "${duration.toHours()} h ${duration.toMinutes() % 60} min"

    override fun metricValue(metric: Metric, value: Double): String = when (metric) {
        Metric.STEPS -> steps(value.toLong())
        Metric.ACTIVE_CALORIES -> energy(value)
        Metric.DISTANCE -> distance(value)
        Metric.SLEEP -> duration(Duration.ofSeconds(value.toLong()))
        Metric.RESTING_HEART_RATE -> heartRate(value)
        Metric.WEIGHT -> mass(value)
    }

    override fun date(date: LocalDate, style: DateStyle): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

    override fun time(instant: Instant): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone))

    override fun dateTime(instant: Instant): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
            .format(instant.atZone(zone))

    override fun rangeLabel(window: ChartWindow): String = date(window.anchor)

    override fun distanceUnitLabel(): String = "km"

    override fun massUnitLabel(): String = "kg"

    override fun toDisplayDistance(meters: Double): Double = meters / 1000

    override fun toDisplayMass(kilograms: Double): Double = kilograms
}
