// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import io.reddevs.tidemark.data.model.RecordType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val NOON = java.time.LocalTime.NOON

/** A half-open time window [start, end) for a record query. */
data class TimeBounds(val start: Instant, val end: Instant)

/** The date ranges the record list offers. Every range covers whole local days. */
sealed interface ExplorerRange {
    /** First and last local day covered, both inclusive. */
    fun days(today: LocalDate): Pair<LocalDate, LocalDate>

    /**
     * The query window for [type]. Sleep sessions follow the dashboard rule that a night belongs to
     * the morning it ends, so their days run noon to noon: "Today" shows last night, and a sleep
     * drill-down for a morning finds the session that started the evening before.
     */
    fun bounds(today: LocalDate, zone: ZoneId, type: RecordType? = null): TimeBounds {
        val (first, last) = days(today)
        return if (type == RecordType.SLEEP_SESSION) {
            TimeBounds(
                first.minusDays(1).atTime(NOON).atZone(zone).toInstant(),
                last.atTime(NOON).atZone(zone).toInstant(),
            )
        } else {
            TimeBounds(first.atStartOfDay(zone).toInstant(), last.plusDays(1).atStartOfDay(zone).toInstant())
        }
    }

    data object Today : ExplorerRange {
        override fun days(today: LocalDate) = today to today
    }

    data object Last7Days : ExplorerRange {
        override fun days(today: LocalDate) = today.minusDays(6) to today
    }

    data object Last30Days : ExplorerRange {
        override fun days(today: LocalDate) = today.minusDays(29) to today
    }

    /** A range picked in the date range picker. */
    data class Custom(val first: LocalDate, val last: LocalDate) : ExplorerRange {
        init {
            require(!last.isBefore(first)) { "Custom range ends before it starts" }
        }

        override fun days(today: LocalDate) = first to last
    }

    /** One day, as passed by a drill-down from a chart. */
    data class Day(val date: LocalDate) : ExplorerRange {
        override fun days(today: LocalDate) = date to date
    }
}
