// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import io.reddevs.tidemark.data.HealthAccessException
import io.reddevs.tidemark.data.model.DataSource
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.navigation.Explorer
import io.reddevs.tidemark.testing.FakeHealthRepository.Call
import io.reddevs.tidemark.testing.MainDispatcherRule
import io.reddevs.tidemark.testing.TestRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RecordListViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val zone = ZoneId.of("Europe/Paris")
    private val clock = Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), zone)
    private val today = LocalDate.of(2026, 11, 19)
    private val repo = RecordingHealthRepository()

    /** [count] steps records, one a minute from 08:00 today, alternating between apps "a" and "b". */
    private fun seedSteps(count: Int, day: LocalDate = today) {
        val first = day.atTime(8, 0).atZone(zone).toInstant()
        repo.records[RecordType.STEPS] = (0 until count).map { i ->
            TestRecords.steps("s$i", first + Duration.ofMinutes(i.toLong()), count = 10, packageName = if (i % 2 == 0) "a" else "b")
        }
    }

    private fun viewModel(
        range: ExplorerRange = ExplorerRange.Last7Days,
        source: DataSource? = null,
    ) = RecordListViewModel(repo, clock, RecordType.STEPS, range, source)

    private val RecordListViewModel.records get() = state.value.records
    private val pageCalls get() = repo.calls[Call.READ_PAGE] ?: 0

    @Test
    fun loadsTheFirstPage() {
        seedSteps(250)
        val vm = viewModel()

        assertEquals(100, vm.records.size)
        assertEquals("s0", vm.records.first().metadata.id)
        assertFalse(vm.state.value.loadingFirstPage)
        assertFalse(vm.state.value.endReached)
        assertEquals(1, pageCalls)
        val query = repo.pageQueries.single()
        assertNull(query.pageToken)
        assertEquals(100, query.pageSize)
        assertEquals(today.minusDays(6).atStartOfDay(zone).toInstant(), query.start)
        assertEquals(today.plusDays(1).atStartOfDay(zone).toInstant(), query.end)
    }

    @Test
    fun loadsTheNextPageOnlyNearTheEnd() {
        seedSteps(250)
        val vm = viewModel()

        vm.onLastVisibleItem(10)
        vm.onLastVisibleItem(79)
        assertEquals(1, pageCalls)

        vm.onLastVisibleItem(80)
        assertEquals(2, pageCalls)
        assertEquals(200, vm.records.size)
        assertEquals("s199", vm.records.last().metadata.id)
        assertEquals("100", repo.pageQueries.last().pageToken)
    }

    @Test
    fun stopsAtTheEndOfData() {
        seedSteps(150)
        val vm = viewModel()

        vm.onLastVisibleItem(99)
        assertEquals(150, vm.records.size)
        assertTrue(vm.state.value.endReached)

        vm.onLastVisibleItem(149)
        vm.onLastVisibleItem(150)
        assertEquals(2, pageCalls)
    }

    @Test
    fun emptyRangeEndsImmediately() {
        val vm = viewModel()

        assertTrue(vm.records.isEmpty())
        assertTrue(vm.state.value.endReached)
        assertNull(vm.state.value.error)
    }

    @Test
    fun changingTheSourceResetsPaging() {
        seedSteps(400)
        val vm = viewModel()
        vm.onLastVisibleItem(99)
        assertEquals(200, vm.records.size)

        vm.selectSource(DataSource("b"))

        val query = repo.pageQueries.last()
        assertEquals(DataSource("b"), query.source)
        assertNull(query.pageToken)
        assertEquals(100, vm.records.size)
        assertTrue(vm.records.all { it.metadata.dataOrigin.packageName == "b" })
        assertEquals("s1", vm.records.first().metadata.id)
        // The chips keep every source, so the user can switch back.
        assertEquals(setOf(DataSource("a"), DataSource("b")), vm.state.value.sources)

        vm.selectSource(null)
        assertNull(repo.pageQueries.last().source)
        assertEquals("s0", vm.records.first().metadata.id)
    }

    @Test
    fun sourcesMergeAggregateOriginsWithOriginsSeenInPages() {
        seedSteps(10)
        repo.aggregateOrigins = setOf(DataSource("a"), DataSource("from.aggregate.only"))
        val vm = viewModel()

        assertEquals(setOf(DataSource("a"), DataSource("b"), DataSource("from.aggregate.only")), vm.state.value.sources)
    }

    @Test
    fun drillDownArgumentsPreselectTypeDayAndSource() {
        val day = today.minusDays(3)
        seedSteps(10, day)
        val args = Explorer.of(RecordType.STEPS, day, sourcePackage = "b")

        val vm = RecordListViewModel(
            repo,
            clock,
            args.type!!,
            RecordListViewModel.initialRange(args.day),
            args.sourcePackage?.let(::DataSource),
        )

        assertEquals(RecordType.STEPS, vm.state.value.type)
        assertEquals(ExplorerRange.Day(day), vm.state.value.range)
        assertEquals(day, vm.state.value.drillDownDay)
        val query = repo.pageQueries.single()
        assertEquals(RecordType.STEPS, query.type)
        assertEquals(day.atStartOfDay(zone).toInstant(), query.start)
        assertEquals(day.plusDays(1).atStartOfDay(zone).toInstant(), query.end)
        assertEquals(DataSource("b"), query.source)
        assertEquals(5, vm.records.size)
    }

    @Test
    fun withoutDrillDownTheRangeIsTheLastSevenDays() {
        assertEquals(ExplorerRange.Last7Days, RecordListViewModel.initialRange(Explorer().day))
    }

    @Test
    fun changingTheRangeReloadsWithNewBounds() {
        seedSteps(10, today.minusDays(20))
        val vm = viewModel()
        assertTrue(vm.records.isEmpty())

        vm.selectRange(ExplorerRange.Last30Days)

        assertEquals(10, vm.records.size)
        assertEquals(today.minusDays(29).atStartOfDay(zone).toInstant(), repo.pageQueries.last().start)
        assertEquals(2, repo.calls[Call.DATA_ORIGINS])
    }

    @Test
    fun missingPermissionShowsItsOwnStateAndRecoversOnResume() {
        seedSteps(10)
        repo.granted = repo.granted - RecordType.STEPS.readPermission
        val vm = viewModel()

        assertEquals(LoadError.PERMISSION_MISSING, vm.state.value.error)
        assertTrue(vm.records.isEmpty())
        assertFalse(vm.state.value.loadingFirstPage)

        repo.granted = repo.granted + RecordType.STEPS.readPermission
        vm.onResume()

        assertNull(vm.state.value.error)
        assertEquals(10, vm.records.size)
    }

    @Test
    fun errorOnFirstPageCanBeRetried() {
        seedSteps(10)
        repo.failures[Call.READ_PAGE] = HealthAccessException.RateLimited()
        val vm = viewModel()

        assertEquals(LoadError.RATE_LIMITED, vm.state.value.error)
        vm.onResume() // only a missing permission retries on resume
        assertEquals(1, pageCalls)

        repo.failures.clear()
        vm.retry()
        assertNull(vm.state.value.error)
        assertEquals(10, vm.records.size)
    }

    @Test
    fun errorOnALaterPageKeepsLoadedRecords() {
        seedSteps(250)
        val vm = viewModel()
        repo.failures[Call.READ_PAGE] = HealthAccessException.Unexpected(IllegalStateException("boom"))

        vm.onLastVisibleItem(99)
        assertEquals(LoadError.GENERIC, vm.state.value.error)
        assertEquals(100, vm.records.size)

        vm.onLastVisibleItem(99) // no automatic retry while an error shows
        assertEquals(2, pageCalls)

        repo.failures.clear()
        vm.retry()
        assertEquals(200, vm.records.size)
        assertEquals("100", repo.pageQueries.last().pageToken)
    }

    @Test
    fun originsFailureDoesNotBlockTheList() {
        seedSteps(10)
        repo.failures[Call.DATA_ORIGINS] = HealthAccessException.RateLimited()
        val vm = viewModel()

        assertNull(vm.state.value.error)
        assertEquals(10, vm.records.size)
        assertEquals(setOf(DataSource("a"), DataSource("b")), vm.state.value.sources)
    }

    @Test
    fun scrollingFiveThousandRecordsLoadsOnlyThePagesItNeeds() {
        seedSteps(5_000, today.minusDays(5))
        val vm = viewModel()

        // Scroll row by row to row 349, as the list would report while flinging.
        for (row in 0..349) vm.onLastVisibleItem(row)

        // Row 349 is within the prefetch distance of 400 loaded rows, but not of 300.
        assertEquals(4, pageCalls)
        assertEquals(400, vm.records.size)
        assertEquals(listOf(null, "100", "200", "300"), repo.pageQueries.map { it.pageToken })

        // Jumping to the end of what's loaded asks for exactly one more page each time.
        vm.onLastVisibleItem(399)
        vm.onLastVisibleItem(399)
        assertEquals(5, pageCalls)
    }
}
