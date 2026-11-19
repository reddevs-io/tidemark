// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data.model

import androidx.annotation.Keep
import java.time.LocalDate
import java.time.LocalDateTime

// @Keep: used as a navigation argument; R8 must not obfuscate its serializer.
@Keep
enum class ChartRange { DAY, WEEK, MONTH, YEAR }

/** The day, week, month or year that contains [anchor]. Weeks start on the locale's first day. */
data class ChartWindow(val range: ChartRange, val anchor: LocalDate)

/**
 * One chart bucket in local time. Values are SI: meters, kilocalories, kilograms, bpm, step
 * counts, and seconds for sleep. [value] is null when the bucket has no data.
 */
data class SeriesPoint(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val value: Double?,
    val min: Double? = null,
    val max: Double? = null,
)

/** Bucketed values for one metric over one window, with summary statistics over buckets that have data. */
data class Series(
    val metric: Metric,
    val window: ChartWindow,
    val points: List<SeriesPoint>,
    val total: Double?,
    val average: Double?,
    val min: Double?,
    val max: Double?,
)
