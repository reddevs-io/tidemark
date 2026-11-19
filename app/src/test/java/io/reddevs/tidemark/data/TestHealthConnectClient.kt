// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.data

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ReadRecordResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.FakePermissionController
import androidx.health.connect.client.time.TimeRangeFilter
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.testing.FakeHealthRepository
import java.time.Instant
import kotlin.reflect.KClass

/**
 * FakeHealthConnectClient plus the two things connect-testing 1.0.0-alpha03 leaves out:
 * - [featureStatus]: the fake always reports features as unavailable;
 * - read permissions: the fake never checks them, so reads of a type whose permission is not
 *   granted throw SecurityException here, like the real provider.
 * It also emulates two provider behaviours the fake gets wrong: readRecords with
 * ascendingOrder = false returns newest first (the fake ignores the flag), and readRecord finds a
 * record by its id and reports an unknown id as IllegalArgumentException (the fake keys records by
 * id and package and throws NullPointerException).
 */
class TestHealthConnectClient(
    val permissions: FakePermissionController = FakePermissionController(false),
    val fake: FakeHealthConnectClient = FakeHealthConnectClient(FakeHealthConnectClient.DEFAULT_PACKAGE_NAME, java.time.Clock.systemUTC(), permissions),
) : HealthConnectClient by fake {

    var featureStatus: Int = HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    override val features: HealthConnectFeatures = object : HealthConnectFeatures {
        override fun getFeatureStatus(feature: Int): Int = featureStatus
    }

    override suspend fun <T : Record> readRecords(request: ReadRecordsRequest<T>): ReadRecordsResponse<T> {
        requirePermission(request.recordType)
        if (request.ascendingOrder) return fake.readRecords(request)
        val newestFirst = readAllFromFake(request.recordType, request.timeRangeFilter, request.dataOriginFilter)
            .sortedByDescending { FakeHealthRepository.timeOf(it) }
        val offset = request.pageToken?.toInt() ?: 0
        val page = newestFirst.drop(offset).take(request.pageSize)
        val next = offset + page.size
        return ReadRecordsResponse(page, if (next < newestFirst.size) next.toString() else null)
    }

    override suspend fun <T : Record> readRecord(recordType: KClass<T>, recordId: String): ReadRecordResponse<T> {
        requirePermission(recordType)
        val match = readAllFromFake(recordType, TimeRangeFilter.after(Instant.EPOCH), emptySet())
            .firstOrNull { it.metadata.id == recordId }
            ?: throw IllegalArgumentException("No record found for id $recordId")
        return ReadRecordResponse(match)
    }

    private suspend fun <T : Record> readAllFromFake(
        recordType: KClass<T>,
        filter: TimeRangeFilter,
        origins: Set<DataOrigin>,
    ): List<T> = fake.readRecords(ReadRecordsRequest(recordType, filter, origins, pageSize = 5000)).records

    fun grantAll() = permissions.grantPermissions(RecordType.readPermissions)

    private suspend fun requirePermission(recordType: KClass<out Record>) {
        if (HealthPermission.getReadPermission(recordType) !in permissions.getGrantedPermissions()) {
            throw SecurityException("Caller doesn't have ${HealthPermission.getReadPermission(recordType)}")
        }
    }
}
