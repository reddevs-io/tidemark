// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import android.os.RemoteException
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.testing.AggregationResult
import androidx.health.connect.client.testing.stubs.MutableStub
import androidx.health.connect.client.testing.stubs.stub
import androidx.health.connect.client.units.Mass
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.data.MetricAggregatesTest.Companion.aggregation
import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.DataSource
import io.reddevs.tidemark.data.model.HcAvailability
import io.reddevs.tidemark.data.model.HistoryAccess
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.data.model.SleepStage
import io.reddevs.tidemark.testing.TestRecords
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Repository tests on Robolectric with connect-testing's FakeHealthConnectClient, wrapped by
 * [TestHealthConnectClient] for feature status and permission checks. The fake can't compute
 * aggregates, so aggregate tests stub results through fake.overrides with connect-testing's
 * AggregationResult factory; bucket math itself is covered by [SeriesPlanTest].
 */
@RunWith(AndroidJUnit4::class)
class HealthConnectHealthRepositoryTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val now = Instant.parse("2026-11-19T08:00:00Z") // 09:00 in Paris
    private val client = TestHealthConnectClient().apply { grantAll() }
    private var status = HealthConnectClient.SDK_AVAILABLE
    private var clientsCreated = 0

    private fun repository(installable: Boolean = true, installed: Boolean = true) = HealthConnectHealthRepository(
        sdkStatus = { status },
        clientFactory = { clientsCreated++; client },
        clock = Clock.fixed(now, paris),
        zone = { paris },
        locale = { Locale.FRANCE },
        providerInstallable = { installable },
        providerInstalled = { installed },
    )

    private val repo = repository()

    private suspend fun insert(packageName: String, records: List<Record>): List<String> {
        client.fake.setPackageName(packageName)
        return client.fake.insertRecords(records).recordIdsList
    }

    private fun steps(i: Int, start: Instant) = TestRecords.steps("s$i", start, count = 10L + i)

    private suspend inline fun <reified E : HealthAccessException> assertThrows(crossinline block: suspend () -> Unit): E {
        try {
            block()
        } catch (e: HealthAccessException) {
            if (e is E) return e
            fail("Expected ${E::class.simpleName} but got $e")
        }
        fail("Expected ${E::class.simpleName}")
        error("unreachable")
    }

    // Availability and history

    @Test
    fun availabilityFollowsSdkStatus() {
        assertEquals(HcAvailability.AVAILABLE, repo.availability())
        status = HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
        assertEquals(HcAvailability.UPDATE_REQUIRED, repo.availability())
        status = HealthConnectClient.SDK_UNAVAILABLE
        assertEquals(HcAvailability.NOT_INSTALLED, repo.availability())
        assertEquals(HcAvailability.UNSUPPORTED, repository(installable = false).availability())
    }

    @Test
    fun updateRequiredWithoutThePackageMeansNotInstalled() {
        // Android 9 to 13 report "update required" when Health Connect is missing but installable.
        status = HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
        assertEquals(HcAvailability.NOT_INSTALLED, repository(installed = false).availability())
        assertEquals(HcAvailability.UPDATE_REQUIRED, repository(installed = true).availability())
    }

    @Test
    fun clientIsCreatedLazilyAndOnlyWhenAvailable() = runTest {
        status = HealthConnectClient.SDK_UNAVAILABLE
        assertEquals(emptySet<String>(), repo.grantedPermissions())
        assertEquals(HistoryAccess.UNAVAILABLE, repo.historyAccess())
        assertThrows<HealthAccessException.Unavailable> { repo.readPage(RecordType.STEPS, now.minusSeconds(60), now, null, null) }
        assertEquals(0, clientsCreated)

        status = HealthConnectClient.SDK_AVAILABLE
        repo.readPage(RecordType.STEPS, now.minusSeconds(60), now, null, null)
        repo.readPage(RecordType.STEPS, now.minusSeconds(60), now, null, null)
        assertEquals(1, clientsCreated)
    }

    @Test
    fun historyAccessHasThreeStates() = runTest {
        client.featureStatus = HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE
        assertFalse(repo.historyFeatureAvailable())
        assertEquals(HistoryAccess.UNAVAILABLE, repo.historyAccess())
        assertEquals(RecordType.readPermissions, repo.permissionsToRequest(includeHistory = true))

        client.featureStatus = HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        assertTrue(repo.historyFeatureAvailable())
        assertEquals(HistoryAccess.NOT_GRANTED, repo.historyAccess())
        assertEquals(
            RecordType.readPermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
            repo.permissionsToRequest(includeHistory = true),
        )
        assertEquals(RecordType.readPermissions, repo.permissionsToRequest(includeHistory = false))

        client.permissions.grantPermission(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
        assertEquals(HistoryAccess.GRANTED, repo.historyAccess())
    }

    // Raw reads

    @Test
    fun pagesThroughRecordsWithAToken() = runTest {
        val start = Instant.parse("2026-11-18T00:00:00Z")
        insert("com.a", (0 until 250).map { steps(it, start + Duration.ofMinutes(it.toLong())) })

        val pages = mutableListOf<io.reddevs.tidemark.data.model.RecordPage>()
        var token: String? = null
        do {
            val page = repo.readPage(RecordType.STEPS, start, start + Duration.ofDays(1), null, token, pageSize = 100)
            pages += page
            token = page.nextPageToken
        } while (token != null)

        assertEquals(listOf(100, 100, 50), pages.map { it.records.size })
        assertNotNull(pages[0].nextPageToken)
        assertNotNull(pages[1].nextPageToken)
        val all = pages.flatMap { it.records }.map { it as StepsRecord }
        assertEquals(250, all.map { it.metadata.id }.toSet().size)
        assertEquals(all.sortedBy { it.startTime }, all)
    }

    @Test
    fun filtersBySource() = runTest {
        val start = Instant.parse("2026-11-18T00:00:00Z")
        insert("com.a", (0 until 3).map { steps(it, start + Duration.ofHours(it.toLong())) })
        insert("com.b", (3 until 5).map { steps(it, start + Duration.ofHours(it.toLong())) })
        val end = start + Duration.ofDays(1)

        val onlyB = repo.readPage(RecordType.STEPS, start, end, DataSource("com.b"), null)
        assertEquals(2, onlyB.records.size)
        assertTrue(onlyB.records.all { it.metadata.dataOrigin.packageName == "com.b" })
        assertEquals(5, repo.readPage(RecordType.STEPS, start, end, null, null).records.size)
    }

    @Test
    fun readRecordFindsOrReturnsNull() = runTest {
        val id = insert("com.a", listOf(steps(1, Instant.parse("2026-11-18T10:00:00Z")))).single()
        val found = repo.readRecord(RecordType.STEPS, id)
        assertEquals(id, found?.metadata?.id)
        assertEquals(11L, (found as StepsRecord).count)
        assertNull(repo.readRecord(RecordType.STEPS, "no-such-id"))
    }

    // Sleep

    @Test
    fun lastNightPicksTheLongestOfOverlappingSessionsAndNamesItsSource() = runTest {
        insert(
            "com.a",
            listOf(
                sleep("2026-11-18T22:00:00Z", "2026-11-19T05:00:00Z"), // 7 h
                sleep("2026-11-17T20:00:00Z", "2026-11-18T10:00:00Z"), // 14 h, but ends before 12:00 yesterday
            ),
        )
        val stages = listOf(
            stage("2026-11-18T21:30:00Z", "2026-11-18T22:00:00Z", SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED),
            stage("2026-11-18T22:00:00Z", "2026-11-19T00:00:00Z", SleepSessionRecord.STAGE_TYPE_LIGHT),
            stage("2026-11-19T00:00:00Z", "2026-11-19T01:30:00Z", SleepSessionRecord.STAGE_TYPE_DEEP),
            // A gap between 01:30 and 02:00.
            stage("2026-11-19T02:00:00Z", "2026-11-19T03:00:00Z", SleepSessionRecord.STAGE_TYPE_REM),
            stage("2026-11-19T03:00:00Z", "2026-11-19T03:10:00Z", SleepSessionRecord.STAGE_TYPE_AWAKE),
            stage("2026-11-19T03:10:00Z", "2026-11-19T03:20:00Z", SleepSessionRecord.STAGE_TYPE_OUT_OF_BED),
            stage("2026-11-19T03:20:00Z", "2026-11-19T05:00:00Z", SleepSessionRecord.STAGE_TYPE_SLEEPING),
            stage("2026-11-19T05:00:00Z", "2026-11-19T05:30:00Z", SleepSessionRecord.STAGE_TYPE_UNKNOWN),
        )
        val idB = insert("com.b", listOf(sleep("2026-11-18T21:30:00Z", "2026-11-19T05:30:00Z", stages))).single() // 8 h

        val night = repo.lastNightSleep(ZonedDateTime.ofInstant(now, paris))!!
        assertEquals(idB, night.sessionId)
        assertEquals(DataSource("com.b"), night.source)
        assertEquals(Instant.parse("2026-11-18T21:30:00Z"), night.start)
        assertEquals(Instant.parse("2026-11-19T05:30:00Z"), night.end)
        assertEquals(
            listOf(
                SleepStage.AWAKE_IN_BED, SleepStage.LIGHT, SleepStage.DEEP, SleepStage.REM,
                SleepStage.AWAKE, SleepStage.OUT_OF_BED, SleepStage.SLEEPING, SleepStage.UNKNOWN,
            ),
            night.stages.map { it.stage },
        )
        assertEquals(Instant.parse("2026-11-19T02:00:00Z"), night.stages[3].start)
    }

    @Test
    fun sleepNightBelongsToTheMorningItEnds() = runTest {
        val night = insert("com.a", listOf(sleep("2026-11-18T22:00:00Z", "2026-11-19T06:00:00Z"))).single()
        // Ends at 13:00 local on the 19th: after noon, so it belongs to the morning of the 20th.
        val nap = insert("com.a", listOf(sleep("2026-11-19T10:00:00Z", "2026-11-19T12:00:00Z"))).single()

        assertEquals(night, repo.sleepNight(LocalDate.parse("2026-11-19"))?.sessionId)
        assertEquals(nap, repo.sleepNight(LocalDate.parse("2026-11-20"))?.sessionId)
        assertNull(repo.sleepNight(LocalDate.parse("2026-11-18")))
    }

    // Latest values and heart rate

    @Test
    fun latestReadsTheNewestValueWithinAYear() = runTest {
        insert(
            "com.scale",
            listOf(
                weight(now - Duration.ofDays(400), 80.0),
                weight(now - Duration.ofDays(10), 70.0),
                weight(now - Duration.ofDays(2), 71.5),
            ),
        )
        val latest = repo.latest(Metric.WEIGHT)!!
        assertEquals(71.5, latest.value, 1e-9)
        assertEquals(now - Duration.ofDays(2), latest.time)
        assertEquals(DataSource("com.scale"), latest.source)
        assertNull(repo.latest(Metric.RESTING_HEART_RATE))
        assertNull(repo.latest(Metric.STEPS))
    }

    @Test
    fun heartRateSamplesCoverTheLocalDayAcrossPages() = runTest {
        val dayStart = Instant.parse("2026-11-18T23:00:00Z") // midnight on the 19th in Paris
        val count = HealthConnectHealthRepository.BULK_PAGE_SIZE + 5
        insert("com.watch", (0 until count).map { i -> heartRate(dayStart + Duration.ofSeconds(i * 60L), 60L + i % 40) })
        insert("com.watch", listOf(heartRate(dayStart - Duration.ofMinutes(30), 150))) // the 18th, local time

        val samples = repo.heartRateSamples(LocalDate.parse("2026-11-19"))
        assertEquals(count, samples.size)
        assertEquals(dayStart, samples.first().time)
        assertEquals(samples.sortedBy { it.time }, samples)
        assertFalse(samples.any { it.bpm == 150L })
    }

    // Permissions and errors

    @Test
    fun missingPermissionFailsOnlyThatType() = runTest {
        insert("com.scale", listOf(weight(now - Duration.ofDays(1), 70.0)))
        client.permissions.replaceGrantedPermissions(setOf(RecordType.WEIGHT.readPermission))
        client.fake.overrides.aggregate = MutableStub<AggregationResult>(SecurityException("denied"))

        val page = assertThrows<HealthAccessException.PermissionMissing> {
            repo.readPage(RecordType.STEPS, now - Duration.ofDays(1), now, null, null)
        }
        assertEquals(RecordType.STEPS, page.type)
        assertEquals(
            RecordType.SLEEP_SESSION,
            assertThrows<HealthAccessException.PermissionMissing> { repo.lastNightSleep(ZonedDateTime.ofInstant(now, paris)) }.type,
        )
        assertEquals(
            RecordType.STEPS,
            assertThrows<HealthAccessException.PermissionMissing> { repo.todayTotal(Metric.STEPS, LocalDate.parse("2026-11-19")) }.type,
        )
        assertEquals(70.0, repo.latest(Metric.WEIGHT)!!.value, 1e-9)
    }

    @Test
    fun rateLimitAndServiceErrorsAreMapped() = runTest {
        client.fake.overrides.aggregate = MutableStub<AggregationResult>(RemoteException("Rate limited request quota has been exceeded"))
        assertThrows<HealthAccessException.RateLimited> { repo.todayTotal(Metric.STEPS, LocalDate.parse("2026-11-19")) }
        client.fake.overrides.aggregate = MutableStub<AggregationResult>(RemoteException("binder died"))
        assertThrows<HealthAccessException.Unavailable> { repo.dataOrigins(RecordType.STEPS, now - Duration.ofDays(1), now) }
    }

    // Aggregates (stubbed through fake.overrides)

    @Test
    fun todayTotalReadsTheAggregate() = runTest {
        client.fake.overrides.aggregate = stub(aggregation(StepsRecord.COUNT_TOTAL to 1234L))
        assertEquals(1234.0, repo.todayTotal(Metric.STEPS, LocalDate.parse("2026-11-19"))!!, 1e-9)
        assertNull(repo.todayTotal(Metric.WEIGHT, LocalDate.parse("2026-11-19")))

        client.fake.overrides.aggregate = stub(AggregationResult())
        assertNull(repo.todayTotal(Metric.STEPS, LocalDate.parse("2026-11-19")))
    }

    @Test
    fun dataOriginsComeFromTheAggregate() = runTest {
        client.fake.overrides.aggregate = stub(AggregationResult(dataOrigins = setOf(DataOrigin("com.a"), DataOrigin("com.b"))))
        assertEquals(
            setOf(DataSource("com.a"), DataSource("com.b")),
            repo.dataOrigins(RecordType.EXERCISE_SESSION, now - Duration.ofDays(7), now),
        )
    }

    @Test
    fun yearSeriesFoldsDailyGroupsIntoMonthlyAverages() = runTest {
        fun day(date: String, steps: Long) = AggregationResultGroupedByPeriod(
            aggregation(StepsRecord.COUNT_TOTAL to steps),
            LocalDateTime.parse("${date}T00:00"),
            LocalDateTime.parse("${date}T00:00").plusDays(1),
        )
        client.fake.overrides.aggregateGroupByPeriod =
            stub<List<AggregationResultGroupedByPeriod>>(listOf(day("2026-01-05", 1000), day("2026-01-06", 3000), day("2026-03-01", 500)))

        val series = repo.series(Metric.STEPS, ChartWindow(ChartRange.YEAR, LocalDate.parse("2026-11-19")))
        assertEquals(12, series.points.size)
        assertEquals(2000.0, series.points[0].value)
        assertNull(series.points[1].value)
        assertEquals(500.0, series.points[2].value)
        assertEquals(4500.0, series.total)
        assertEquals(1500.0, series.average)
    }

    @Test
    fun weightSeriesCarriesMinAndMax() = runTest {
        client.fake.overrides.aggregateGroupByPeriod = stub<List<AggregationResultGroupedByPeriod>>(
            listOf(
                AggregationResultGroupedByPeriod(
                    aggregation(
                        WeightRecord.WEIGHT_AVG to Mass.kilograms(71.0),
                        WeightRecord.WEIGHT_MIN to Mass.kilograms(70.0),
                        WeightRecord.WEIGHT_MAX to Mass.kilograms(72.0),
                    ),
                    LocalDateTime.parse("2026-11-17T00:00"),
                    LocalDateTime.parse("2026-11-18T00:00"),
                ),
            ),
        )
        val series = repo.series(Metric.WEIGHT, ChartWindow(ChartRange.WEEK, LocalDate.parse("2026-11-19")))
        assertEquals(7, series.points.size)
        assertEquals(LocalDateTime.parse("2026-11-16T00:00"), series.points.first().start) // Monday in France
        assertEquals(71.0, series.points[1].value!!, 1e-9)
        assertEquals(70.0, series.min!!, 1e-9)
        assertEquals(72.0, series.max!!, 1e-9)
    }

    @Test
    fun dayViewMatchesHourlyGroupsByInstantAcrossDst() = runTest {
        val secondTwoAm = Instant.parse("2026-10-25T01:00:00Z")
        client.fake.overrides.aggregateGroupByDuration = stub<List<AggregationResultGroupedByDuration>>(
            listOf(
                AggregationResultGroupedByDuration(
                    aggregation(StepsRecord.COUNT_TOTAL to 300L),
                    secondTwoAm,
                    secondTwoAm + Duration.ofHours(1),
                    ZoneOffset.ofHours(1),
                ),
            ),
        )
        val series = repo.series(Metric.STEPS, ChartWindow(ChartRange.DAY, LocalDate.parse("2026-10-25")))
        assertEquals(25, series.points.size)
        assertNull(series.points[2].value)
        assertEquals(300.0, series.points[3].value)
        assertEquals(300.0, series.total)
    }

    @Test
    fun sleepSeriesUsesNoonToNoonGroups() = runTest {
        client.fake.overrides.aggregateGroupByPeriod = stub<List<AggregationResultGroupedByPeriod>>(
            listOf(
                AggregationResultGroupedByPeriod(
                    aggregation(SleepSessionRecord.SLEEP_DURATION_TOTAL to Duration.ofHours(7)),
                    LocalDateTime.parse("2026-11-18T12:00"),
                    LocalDateTime.parse("2026-11-19T12:00"),
                ),
            ),
        )
        val series = repo.series(Metric.SLEEP, ChartWindow(ChartRange.WEEK, LocalDate.parse("2026-11-19")))
        val thursday = series.points.single { it.value != null }
        assertEquals(LocalDateTime.parse("2026-11-19T00:00"), thursday.start)
        assertEquals(7 * 3600.0, thursday.value!!, 1e-9)
    }

    private fun sleep(start: String, end: String, stages: List<SleepSessionRecord.Stage> = emptyList()) = SleepSessionRecord(
        startTime = Instant.parse(start),
        startZoneOffset = ZoneOffset.UTC,
        endTime = Instant.parse(end),
        endZoneOffset = ZoneOffset.UTC,
        metadata = TestRecords.metadata("sleep-$start", "ignored"),
        stages = stages,
    )

    private fun stage(start: String, end: String, type: Int) =
        SleepSessionRecord.Stage(Instant.parse(start), Instant.parse(end), type)

    private fun weight(time: Instant, kg: Double) =
        WeightRecord(time = time, zoneOffset = ZoneOffset.UTC, weight = Mass.kilograms(kg), metadata = TestRecords.metadata("w$time"))

    private fun heartRate(time: Instant, bpm: Long) = HeartRateRecord(
        startTime = time,
        startZoneOffset = ZoneOffset.UTC,
        endTime = time + Duration.ofSeconds(30),
        endZoneOffset = ZoneOffset.UTC,
        samples = listOf(HeartRateRecord.Sample(time, bpm)),
        metadata = TestRecords.metadata("hr$time"),
    )
}
