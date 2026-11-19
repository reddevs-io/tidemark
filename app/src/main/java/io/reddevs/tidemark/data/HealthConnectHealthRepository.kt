// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
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
 * [HealthRepository] backed by HealthConnectClient.
 *
 * T-001 stub: availability and permissions are real so the app can launch and onboard; every data
 * call returns empty results. T-002 owns this file and replaces the stubbed parts.
 */
class HealthConnectHealthRepository(private val context: Context) : HealthRepository {

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override fun availability(): HcAvailability =
        when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.UPDATE_REQUIRED
            else -> if (isProviderInstallable()) HcAvailability.NOT_INSTALLED else HcAvailability.UNSUPPORTED
        }

    override suspend fun historyFeatureAvailable(): Boolean =
        availability() == HcAvailability.AVAILABLE &&
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    override fun permissionsToRequest(includeHistory: Boolean): Set<String> =
        if (includeHistory) {
            RecordType.readPermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        } else {
            RecordType.readPermissions
        }

    override suspend fun grantedPermissions(): Set<String> =
        if (availability() == HcAvailability.AVAILABLE) client.permissionController.getGrantedPermissions() else emptySet()

    override suspend fun historyAccess(): HistoryAccess = when {
        !historyFeatureAvailable() -> HistoryAccess.UNAVAILABLE
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in grantedPermissions() -> HistoryAccess.GRANTED
        else -> HistoryAccess.NOT_GRANTED
    }

    override suspend fun todayTotal(metric: Metric, today: LocalDate): Double? = null

    override suspend fun series(metric: Metric, window: ChartWindow): Series =
        Series(metric, window, points = emptyList(), total = null, average = null, min = null, max = null)

    override suspend fun lastNightSleep(now: ZonedDateTime): SleepNight? = null

    override suspend fun sleepNight(morning: LocalDate): SleepNight? = null

    override suspend fun latest(metric: Metric): LatestValue? = null

    override suspend fun heartRateSamples(day: LocalDate): List<HeartRateSample> = emptyList()

    override suspend fun dataOrigins(type: RecordType, start: Instant, end: Instant): Set<DataSource> = emptySet()

    override suspend fun readPage(
        type: RecordType,
        start: Instant,
        end: Instant,
        source: DataSource?,
        pageToken: String?,
        pageSize: Int,
    ): RecordPage = RecordPage(records = emptyList(), nextPageToken = null)

    override suspend fun readRecord(type: RecordType, id: String): Record? = null

    // Health Connect ships as a Play app on Android 9 to 13 and is built in from Android 14.
    private fun isProviderInstallable(): Boolean = android.os.Build.VERSION.SDK_INT < 34

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    }
}
