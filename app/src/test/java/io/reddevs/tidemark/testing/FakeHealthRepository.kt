// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.testing

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import io.reddevs.tidemark.data.HealthAccessException
import io.reddevs.tidemark.data.HealthRepository
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
 * In-memory [HealthRepository] for ViewModel tests. Configure the public properties, then read
 * [calls] to check how often each method ran.
 *
 * It enforces read permissions like Health Connect does: a call for a type whose permission is not
 * in [granted] throws [HealthAccessException.PermissionMissing]. Shared fixture owned by T-001;
 * wrap or subclass it in your own test folder instead of editing it.
 */
open class FakeHealthRepository : HealthRepository {

    enum class Call {
        HISTORY_FEATURE, GRANTED, HISTORY_ACCESS, TODAY_TOTAL, SERIES, LAST_NIGHT, SLEEP_NIGHT,
        LATEST, HEART_RATE, DATA_ORIGINS, READ_PAGE, READ_RECORD,
    }

    var availability: HcAvailability = HcAvailability.AVAILABLE
    var historyFeatureAvailable: Boolean = true
    var granted: Set<String> = RecordType.readPermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    val todayTotals: MutableMap<Metric, Double> = mutableMapOf()
    var seriesProvider: (Metric, ChartWindow) -> Series = { metric, window -> emptySeries(metric, window) }
    var lastNight: SleepNight? = null
    val sleepNights: MutableMap<LocalDate, SleepNight> = mutableMapOf()
    val latestValues: MutableMap<Metric, LatestValue> = mutableMapOf()
    val heartRateByDay: MutableMap<LocalDate, List<HeartRateSample>> = mutableMapOf()

    /** Raw records per type, in any order. readPage sorts them by time. */
    val records: MutableMap<RecordType, List<Record>> = mutableMapOf()

    /** When set for a call, that call throws the exception instead of returning data. */
    val failures: MutableMap<Call, HealthAccessException> = mutableMapOf()

    val calls: MutableMap<Call, Int> = mutableMapOf()

    override fun availability(): HcAvailability = availability

    override suspend fun historyFeatureAvailable(): Boolean = record(Call.HISTORY_FEATURE) { historyFeatureAvailable }

    override fun permissionsToRequest(includeHistory: Boolean): Set<String> =
        if (includeHistory) {
            RecordType.readPermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        } else {
            RecordType.readPermissions
        }

    override suspend fun grantedPermissions(): Set<String> = record(Call.GRANTED) { granted }

    override suspend fun historyAccess(): HistoryAccess = record(Call.HISTORY_ACCESS) {
        when {
            !historyFeatureAvailable -> HistoryAccess.UNAVAILABLE
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted -> HistoryAccess.GRANTED
            else -> HistoryAccess.NOT_GRANTED
        }
    }

    override suspend fun todayTotal(metric: Metric, today: LocalDate): Double? =
        record(Call.TODAY_TOTAL, metric.recordType) { todayTotals[metric] }

    override suspend fun series(metric: Metric, window: ChartWindow): Series =
        record(Call.SERIES, metric.recordType) { seriesProvider(metric, window) }

    override suspend fun lastNightSleep(now: ZonedDateTime): SleepNight? =
        record(Call.LAST_NIGHT, RecordType.SLEEP_SESSION) { lastNight }

    override suspend fun sleepNight(morning: LocalDate): SleepNight? =
        record(Call.SLEEP_NIGHT, RecordType.SLEEP_SESSION) { sleepNights[morning] }

    override suspend fun latest(metric: Metric): LatestValue? =
        record(Call.LATEST, metric.recordType) { latestValues[metric] }

    override suspend fun heartRateSamples(day: LocalDate): List<HeartRateSample> =
        record(Call.HEART_RATE, RecordType.HEART_RATE) { heartRateByDay[day].orEmpty() }

    override suspend fun dataOrigins(type: RecordType, start: Instant, end: Instant): Set<DataSource> =
        record(Call.DATA_ORIGINS, type) {
            inRange(type, start, end).mapTo(linkedSetOf()) { DataSource(it.metadata.dataOrigin.packageName) }
        }

    override suspend fun readPage(
        type: RecordType,
        start: Instant,
        end: Instant,
        source: DataSource?,
        pageToken: String?,
        pageSize: Int,
    ): RecordPage = record(Call.READ_PAGE, type) {
        val matching = inRange(type, start, end)
            .filter { source == null || it.metadata.dataOrigin.packageName == source.packageName }
        val offset = pageToken?.toInt() ?: 0
        val page = matching.drop(offset).take(pageSize)
        val next = offset + page.size
        RecordPage(page, nextPageToken = if (next < matching.size) next.toString() else null)
    }

    override suspend fun readRecord(type: RecordType, id: String): Record? =
        record(Call.READ_RECORD, type) { records[type].orEmpty().firstOrNull { it.metadata.id == id } }

    private fun inRange(type: RecordType, start: Instant, end: Instant): List<Record> =
        records[type].orEmpty()
            .filter { val t = timeOf(it); !t.isBefore(start) && t.isBefore(end) }
            .sortedBy(::timeOf)

    private inline fun <T> record(call: Call, type: RecordType? = null, block: () -> T): T {
        calls[call] = (calls[call] ?: 0) + 1
        failures[call]?.let { throw it }
        if (type != null && type.readPermission !in granted) throw HealthAccessException.PermissionMissing(type)
        return block()
    }

    companion object {
        fun emptySeries(metric: Metric, window: ChartWindow) =
            Series(metric, window, points = emptyList(), total = null, average = null, min = null, max = null)

        /** Start time for interval records, time for instant records. (The base interfaces are internal.) */
        fun timeOf(record: Record): Instant = when (record) {
            is StepsRecord -> record.startTime
            is DistanceRecord -> record.startTime
            is ActiveCaloriesBurnedRecord -> record.startTime
            is ExerciseSessionRecord -> record.startTime
            is SleepSessionRecord -> record.startTime
            is HeartRateRecord -> record.startTime
            is RestingHeartRateRecord -> record.time
            is WeightRecord -> record.time
            else -> error("Unsupported record ${record::class}")
        }
    }
}
