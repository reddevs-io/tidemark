// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.testing

import androidx.health.connect.client.permission.HealthPermission
import io.reddevs.tidemark.data.HealthAccessException
import io.reddevs.tidemark.data.model.DataSource
import io.reddevs.tidemark.data.model.HistoryAccess
import io.reddevs.tidemark.data.model.RecordType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FakeHealthRepositoryTest {
    private val start = Instant.parse("2026-11-01T00:00:00Z")
    private val end = start + Duration.ofDays(1)

    private val repo = FakeHealthRepository().apply {
        records[RecordType.STEPS] = (0 until 250).map { i ->
            TestRecords.steps("s$i", start + Duration.ofMinutes(i.toLong()), count = 10, packageName = if (i % 2 == 0) "a" else "b")
        }.shuffled()
    }

    @Test
    fun pagesThroughRecordsInTimeOrder() = runTest {
        val first = repo.readPage(RecordType.STEPS, start, end, source = null, pageToken = null)
        val second = repo.readPage(RecordType.STEPS, start, end, source = null, pageToken = first.nextPageToken)
        val third = repo.readPage(RecordType.STEPS, start, end, source = null, pageToken = second.nextPageToken)

        assertEquals(listOf(100, 100, 50), listOf(first, second, third).map { it.records.size })
        assertEquals("s0", first.records.first().metadata.id)
        assertEquals("s249", third.records.last().metadata.id)
        assertNull(third.nextPageToken)
        assertEquals(3, repo.calls[FakeHealthRepository.Call.READ_PAGE])
    }

    @Test
    fun filtersBySourceAndListsOrigins() = runTest {
        val page = repo.readPage(RecordType.STEPS, start, end, DataSource("b"), pageToken = null, pageSize = 500)

        assertEquals(125, page.records.size)
        assertEquals(setOf(DataSource("a"), DataSource("b")), repo.dataOrigins(RecordType.STEPS, start, end))
    }

    @Test(expected = HealthAccessException.PermissionMissing::class)
    fun missingPermissionThrows() = runTest {
        repo.granted = setOf(RecordType.WEIGHT.readPermission)
        repo.readPage(RecordType.STEPS, start, end, source = null, pageToken = null)
    }

    @Test
    fun historyAccessFollowsFeatureAndGrant() = runTest {
        assertEquals(HistoryAccess.GRANTED, repo.historyAccess())
        repo.granted = repo.granted - HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        assertEquals(HistoryAccess.NOT_GRANTED, repo.historyAccess())
        repo.historyFeatureAvailable = false
        assertEquals(HistoryAccess.UNAVAILABLE, repo.historyAccess())
    }
}
