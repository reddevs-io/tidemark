// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.reflect.KClass

/**
 * [HealthRepository] backed by HealthConnectClient: the only class in the app that talks to it.
 *
 * Charts and totals use aggregates, so Health Connect deduplicates overlapping writers by the
 * priority order the user set. Sleep, latest values, heart rate samples and the explorer read raw
 * records. Every call maps its failures through [healthCall], so one denied type never fails the
 * others. Dates and buckets follow the device time zone and locale at call time.
 *
 * The internal constructor exists for tests: it takes the SDK status, the client factory, a clock,
 * the zone and locale, and whether the provider is installable from Play.
 */
class HealthConnectHealthRepository internal constructor(
    private val sdkStatus: () -> Int,
    private val clientFactory: () -> HealthConnectClient,
    private val clock: Clock,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale,
    private val providerInstallable: () -> Boolean,
) : HealthRepository {

    constructor(context: Context) : this(
        sdkStatus = { HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE) },
        clientFactory = { HealthConnectClient.getOrCreate(context, PROVIDER_PACKAGE) },
        clock = Clock.systemUTC(),
        zone = { ZoneId.systemDefault() },
        locale = { Locale.getDefault() },
        // Health Connect ships as a Play app on Android 9 to 13 and is built in from Android 14.
        providerInstallable = { Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE },
    )

    @Volatile
    private var cachedClient: HealthConnectClient? = null

    /** Creates the client on first use, and only once Health Connect reports itself available. */
    private fun client(): HealthConnectClient =
        cachedClient ?: synchronized(this) {
            cachedClient ?: run {
                if (availability() != HcAvailability.AVAILABLE) throw HealthAccessException.Unavailable()
                val created = try {
                    clientFactory()
                } catch (e: IllegalStateException) {
                    throw HealthAccessException.Unavailable(e)
                } catch (e: UnsupportedOperationException) {
                    throw HealthAccessException.Unavailable(e)
                }
                cachedClient = created
                created
            }
        }

    override fun availability(): HcAvailability =
        when (sdkStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.UPDATE_REQUIRED
            else -> if (providerInstallable()) HcAvailability.NOT_INSTALLED else HcAvailability.UNSUPPORTED
        }

    override suspend fun historyFeatureAvailable(): Boolean = isHistoryFeatureAvailable()

    // getFeatureStatus is a local version check, so a failure just means "not available".
    private fun isHistoryFeatureAvailable(): Boolean =
        availability() == HcAvailability.AVAILABLE &&
            try {
                client().features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
                    HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
            } catch (e: Exception) {
                false
            }

    override fun permissionsToRequest(includeHistory: Boolean): Set<String> =
        if (includeHistory && isHistoryFeatureAvailable()) {
            RecordType.readPermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        } else {
            RecordType.readPermissions
        }

    override suspend fun grantedPermissions(): Set<String> =
        if (availability() != HcAvailability.AVAILABLE) {
            emptySet()
        } else {
            healthCall(null) { client().permissionController.getGrantedPermissions() }
        }

    override suspend fun historyAccess(): HistoryAccess = when {
        !isHistoryFeatureAvailable() -> HistoryAccess.UNAVAILABLE
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in grantedPermissions() -> HistoryAccess.GRANTED
        else -> HistoryAccess.NOT_GRANTED
    }

    override suspend fun todayTotal(metric: Metric, today: LocalDate): Double? {
        val span = when (metric.kind) {
            MetricKind.CUMULATIVE -> ChartBuckets.daySpan(today)
            MetricKind.SLEEP -> ChartBuckets.sleepSpan(today)
            MetricKind.SAMPLED -> return null
        }
        return healthCall(metric.recordType) {
            val result = client().aggregate(
                AggregateRequest(MetricAggregates.metricsFor(metric), TimeRangeFilter.between(span.start, span.end)),
            )
            MetricAggregates.read(metric, result)?.value
        }
    }

    override suspend fun series(metric: Metric, window: ChartWindow): Series {
        val plan = SeriesPlan.create(metric, window, locale(), zone())
        val metrics = MetricAggregates.metricsFor(metric)
        return healthCall(metric.recordType) {
            when (val query = plan.query) {
                is SeriesQuery.Hourly -> {
                    val groups = client().aggregateGroupByDuration(
                        AggregateGroupByDurationRequest(metrics, TimeRangeFilter.between(query.start, query.end), query.slice),
                    )
                    plan.assembleHourly(
                        groups.mapNotNull { group -> MetricAggregates.read(metric, group.result)?.let { group.startTime to it } }.toMap(),
                    )
                }
                is SeriesQuery.ByPeriod -> {
                    val groups = client().aggregateGroupByPeriod(
                        AggregateGroupByPeriodRequest(metrics, TimeRangeFilter.between(query.start, query.end), query.period),
                    )
                    plan.assembleByPeriod(
                        groups.mapNotNull { group -> MetricAggregates.read(metric, group.result)?.let { group.startTime to it } }.toMap(),
                    )
                }
            }
        }
    }

    override suspend fun lastNightSleep(now: ZonedDateTime): SleepNight? {
        val from = now.toLocalDate().minusDays(1).atTime(ChartBuckets.SLEEP_DAY_START).atZone(now.zone).toInstant()
        return pickSleep(from, now.toInstant(), untilInclusive = true)
    }

    override suspend fun sleepNight(morning: LocalDate): SleepNight? {
        val span = ChartBuckets.sleepSpan(morning)
        val zone = zone()
        return pickSleep(span.start.atZone(zone).toInstant(), span.end.atZone(zone).toInstant(), untilInclusive = false)
    }

    private suspend fun pickSleep(from: Instant, until: Instant, untilInclusive: Boolean): SleepNight? =
        healthCall(RecordType.SLEEP_SESSION) {
            // A session ending in the window may have started up to a day before it.
            val sessions = readAll(SleepSessionRecord::class, TimeRangeFilter.between(from.minus(SLEEP_LOOKBACK), until.plusMillis(1)))
            SleepNights.pick(sessions, from, until, untilInclusive)?.let(SleepNights::toNight)
        }

    override suspend fun latest(metric: Metric): LatestValue? {
        if (metric.kind != MetricKind.SAMPLED) return null
        val type = metric.recordType
        return healthCall(type) {
            val now = clock.instant()
            val response = client().readRecords(
                ReadRecordsRequest(
                    recordType = type.recordKClass(),
                    timeRangeFilter = TimeRangeFilter.between(now.minus(LATEST_LOOKBACK), now),
                    ascendingOrder = false,
                    pageSize = 1,
                ),
            )
            when (val record = response.records.firstOrNull()) {
                is WeightRecord -> LatestValue(metric, record.time, record.weight.inKilograms, record.source())
                is RestingHeartRateRecord -> LatestValue(metric, record.time, record.beatsPerMinute.toDouble(), record.source())
                else -> null
            }
        }
    }

    override suspend fun heartRateSamples(day: LocalDate): List<HeartRateSample> {
        val zone = zone()
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        return healthCall(RecordType.HEART_RATE) {
            readAll(HeartRateRecord::class, TimeRangeFilter.between(start, end))
                .flatMap { it.samples }
                .filter { !it.time.isBefore(start) && it.time.isBefore(end) }
                .sortedBy { it.time }
                .map { HeartRateSample(it.time, it.beatsPerMinute) }
        }
    }

    override suspend fun dataOrigins(type: RecordType, start: Instant, end: Instant): Set<DataSource> =
        healthCall(type) {
            client().aggregate(AggregateRequest(setOf(MetricAggregates.originMetric(type)), TimeRangeFilter.between(start, end)))
                .dataOrigins
                .mapTo(linkedSetOf()) { DataSource(it.packageName) }
        }

    override suspend fun readPage(
        type: RecordType,
        start: Instant,
        end: Instant,
        source: DataSource?,
        pageToken: String?,
        pageSize: Int,
    ): RecordPage = healthCall(type) {
        val response = client().readRecords(
            ReadRecordsRequest(
                recordType = type.recordKClass(),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                dataOriginFilter = source?.let { setOf(DataOrigin(it.packageName)) }.orEmpty(),
                ascendingOrder = true,
                pageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE),
                pageToken = pageToken,
            ),
        )
        RecordPage(response.records, response.pageToken?.takeUnless { it.isEmpty() })
    }

    override suspend fun readRecord(type: RecordType, id: String): Record? = healthCall(type) {
        try {
            client().readRecord(type.recordKClass(), id).record
        } catch (e: IllegalArgumentException) {
            // Health Connect reports an unknown id as an invalid argument.
            null
        }
    }

    /** Reads every page of [recordClass] in [filter]. */
    private suspend fun <T : Record> readAll(recordClass: KClass<T>, filter: TimeRangeFilter): List<T> {
        val records = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client().readRecords(
                ReadRecordsRequest(recordClass, filter, pageSize = BULK_PAGE_SIZE, pageToken = token),
            )
            records += response.records
            token = response.pageToken?.takeUnless { it.isEmpty() }
        } while (token != null)
        return records
    }

    private fun Record.source() = DataSource(metadata.dataOrigin.packageName)

    @Suppress("UNCHECKED_CAST")
    private fun RecordType.recordKClass(): KClass<Record> = recordClass as KClass<Record>

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        /** Health Connect's upper bound for one readRecords page. */
        internal const val MAX_PAGE_SIZE = 5000
        internal const val BULK_PAGE_SIZE = 1000
        private val SLEEP_LOOKBACK: Duration = Duration.ofDays(1)
        private val LATEST_LOOKBACK: Duration = Duration.ofDays(365)
    }
}
