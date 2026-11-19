// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import androidx.health.connect.client.records.Record
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.DataSource
import io.reddevs.tidemark.data.model.HcAvailability
import io.reddevs.tidemark.data.model.HeartRateSample
import io.reddevs.tidemark.data.model.HistoryAccess
import io.reddevs.tidemark.data.model.LatestValue
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.RecordPage
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.data.model.Series
import io.reddevs.tidemark.data.model.SleepNight
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Read-only access to Health Connect. The only place in the app that talks to HealthConnectClient.
 *
 * Suspend functions throw [HealthAccessException] and nothing else. Values are SI units (see
 * [io.reddevs.tidemark.data.model.SeriesPoint]). Dates are local to the device time zone.
 *
 * Frozen contract: changes go through the primary session (plan section 7).
 */
interface HealthRepository {
    fun availability(): HcAvailability

    suspend fun historyFeatureAvailable(): Boolean

    /** The eight read permissions, plus the history permission when [includeHistory] is true. */
    fun permissionsToRequest(includeHistory: Boolean): Set<String>

    suspend fun grantedPermissions(): Set<String>

    suspend fun historyAccess(): HistoryAccess

    /** Today's total for steps, active calories or distance; null when there is no data. */
    suspend fun todayTotal(metric: Metric, today: LocalDate): Double?

    suspend fun series(metric: Metric, window: ChartWindow): Series

    /** The longest sleep session ending between 12:00 yesterday and [now]. */
    suspend fun lastNightSleep(now: ZonedDateTime): SleepNight?

    /** The longest sleep session ending between noon the day before [morning] and noon on [morning]. */
    suspend fun sleepNight(morning: LocalDate): SleepNight?

    /** Latest weight or resting heart rate within the last 365 days. */
    suspend fun latest(metric: Metric): LatestValue?

    suspend fun heartRateSamples(day: LocalDate): List<HeartRateSample>

    suspend fun dataOrigins(type: RecordType, start: Instant, end: Instant): Set<DataSource>

    suspend fun readPage(
        type: RecordType,
        start: Instant,
        end: Instant,
        source: DataSource?,
        pageToken: String?,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): RecordPage

    suspend fun readRecord(type: RecordType, id: String): Record?

    companion object {
        const val DEFAULT_PAGE_SIZE = 100
    }
}
