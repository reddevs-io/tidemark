// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.format

import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.Metric
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

enum class DateStyle { SHORT, MEDIUM, LONG, DAY_MONTH }

/**
 * Turns SI values and times into display text for one locale and unit choice. Take the current
 * instance from AppContainer.formatter, which is rebuilt when the locale or unit preference changes.
 *
 * Frozen contract: changes go through the primary session (plan section 7).
 */
interface MeasurementFormatter {
    val locale: Locale

    fun steps(count: Long): String

    fun distance(meters: Double): String

    fun energy(kilocalories: Double): String

    fun mass(kilograms: Double): String

    fun heartRate(bpm: Double): String

    fun duration(duration: Duration): String

    /** Formats a [Metric] value given in the SI unit the repository uses (sleep in seconds). */
    fun metricValue(metric: Metric, value: Double): String

    fun date(date: LocalDate, style: DateStyle = DateStyle.MEDIUM): String

    fun time(instant: Instant): String

    fun dateTime(instant: Instant): String

    /** For example "Today", "10 to 16 Nov 2026", "November 2026", "2026". */
    fun rangeLabel(window: ChartWindow): String

    fun distanceUnitLabel(): String

    fun massUnitLabel(): String

    /** Meters converted to the display unit (km or mi) for chart axes. */
    fun toDisplayDistance(meters: Double): Double

    /** Kilograms converted to the display unit (kg or lb) for chart axes. */
    fun toDisplayMass(kilograms: Double): Double
}
