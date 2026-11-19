// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.reddevs.tidemark.data.HealthAccessException
import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.HeartRateSample
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.testing.FakeHealthRepository
import io.reddevs.tidemark.testing.FakeHealthRepository.Call
import io.reddevs.tidemark.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class MetricDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val today = DetailSamples.today
    private val windows = mutableListOf<ChartWindow>()
    private val repo = FakeHealthRepository().apply {
        seriesProvider = { metric, window ->
            windows += window
            DetailSamples.series(metric, window)
        }
    }

    private fun viewModel(
        metric: Metric = Metric.STEPS,
        range: ChartRange = ChartRange.WEEK,
        anchor: LocalDate = today,
        saved: SavedStateHandle = SavedStateHandle(),
    ) = MetricDetailViewModel(repo, metric, ChartWindow(range, anchor), { today }, { Locale.UK }, saved)

    private val MetricDetailViewModel.content get() = state.value.content

    @Test
    fun loadsBucketsForTheInitialWindow() {
        val vm = viewModel()
        val content = vm.content as DetailContent.Buckets
        assertEquals(ChartStyle.BARS, content.style)
        assertEquals(7, content.series.points.size)
        assertEquals(listOf(ChartWindow(ChartRange.WEEK, today)), windows)
    }

    @Test
    fun restingHeartRateAndWeightUseLines() {
        assertEquals(ChartStyle.LINES, (viewModel(Metric.WEIGHT).content as DetailContent.Buckets).style)
        assertEquals(ChartStyle.LINES, (viewModel(Metric.RESTING_HEART_RATE).content as DetailContent.Buckets).style)
    }

    @Test
    fun nextIsDisabledAtTodayAndDoesNothing() {
        val vm = viewModel()
        assertFalse(vm.state.value.canGoNext)
        vm.next()
        assertEquals(ChartWindow(ChartRange.WEEK, today), vm.state.value.window)
        assertEquals(1, repo.calls[Call.SERIES])

        vm.previous()
        assertEquals(ChartWindow(ChartRange.WEEK, today.minusWeeks(1)), vm.state.value.window)
        assertTrue(vm.state.value.canGoNext)
        vm.next()
        assertEquals(ChartWindow(ChartRange.WEEK, today), vm.state.value.window)
        assertFalse(vm.state.value.canGoNext)
    }

    @Test
    fun anchorInTheFutureIsClampedToToday() {
        val vm = viewModel(anchor = today.plusDays(10))
        assertEquals(today, vm.state.value.window.anchor)
        assertFalse(vm.state.value.canGoNext)
    }

    @Test
    fun switchingRangeKeepsTheAnchor() {
        val vm = viewModel(range = ChartRange.DAY)
        vm.previous()
        vm.previous()
        vm.selectRange(ChartRange.MONTH)
        assertEquals(ChartWindow(ChartRange.MONTH, today.minusDays(2)), vm.state.value.window)
        vm.selectRange(ChartRange.YEAR)
        assertEquals(ChartWindow(ChartRange.YEAR, today.minusDays(2)), vm.state.value.window)
    }

    @Test
    fun tappingAMonthInTheYearViewOpensThatMonth() {
        val vm = viewModel(range = ChartRange.YEAR)
        vm.previous() // 2025
        vm.onBucketSelected(2) // March 2025
        assertEquals(ChartWindow(ChartRange.MONTH, LocalDate.of(2025, 3, 1)), vm.state.value.window)
        assertEquals(ChartWindow(ChartRange.MONTH, LocalDate.of(2025, 3, 1)), windows.last())
        assertTrue(vm.state.value.canGoNext)
        assertEquals(31, (vm.content as DetailContent.Buckets).series.points.size)
    }

    @Test
    fun tappingADayBucketOpensRecordsForThatDay() = runTest {
        val vm = viewModel(Metric.ACTIVE_CALORIES)
        vm.recordsRequests.test {
            vm.onBucketSelected(1) // Tuesday in a Monday-first week
            assertEquals(DrillTarget.Records(RecordType.ACTIVE_CALORIES, LocalDate.of(2026, 11, 17)), awaitItem())
        }
    }

    @Test
    fun sleepBucketDrillsToTheMorningItEnds() = runTest {
        val vm = viewModel(Metric.SLEEP)
        vm.recordsRequests.test {
            vm.onBucketSelected(0) // noon Sunday 15 to noon Monday 16
            assertEquals(DrillTarget.Records(RecordType.SLEEP_SESSION, LocalDate.of(2026, 11, 16)), awaitItem())
        }
    }

    @Test
    fun hourBucketOpensRecordsForTheDay() = runTest {
        val vm = viewModel(Metric.STEPS, ChartRange.DAY)
        vm.recordsRequests.test {
            vm.onBucketSelected(14)
            assertEquals(DrillTarget.Records(RecordType.STEPS, today), awaitItem())
            vm.onDaySelected()
            assertEquals(DrillTarget.Records(RecordType.STEPS, today), awaitItem())
        }
    }

    @Test
    fun sleepDayViewShowsTheMorningsNight() = runTest {
        repo.sleepNights[today] = DetailSamples.night(today)
        val vm = viewModel(Metric.SLEEP, ChartRange.DAY)
        assertEquals(DetailContent.SleepDay(DetailSamples.night(today)), vm.content)
        vm.recordsRequests.test {
            vm.onDaySelected()
            assertEquals(DrillTarget.Records(RecordType.SLEEP_SESSION, today), awaitItem())
        }
        vm.previous()
        assertEquals(DetailContent.Empty, vm.content)
    }

    @Test
    fun restingHeartRateDayViewPlotsSamplesWithTheRestingValue() = runTest {
        val samples = listOf(
            HeartRateSample(today.atTime(9, 0).toInstant(ZoneOffset.UTC), 80),
            HeartRateSample(today.atTime(8, 0).toInstant(ZoneOffset.UTC), 64),
        )
        repo.heartRateByDay[today] = samples
        val vm = viewModel(Metric.RESTING_HEART_RATE, ChartRange.DAY)
        val content = vm.content as DetailContent.HeartRateDay
        assertEquals(samples.sortedBy { it.time }, content.samples)
        assertEquals(58.0, content.resting!!, 0.001)
        assertFalse(content.samplesPermissionMissing)
        vm.recordsRequests.test {
            vm.onDaySelected()
            assertEquals(DrillTarget.Records(RecordType.HEART_RATE, today), awaitItem())
        }
    }

    @Test
    fun missingHeartRatePermissionStillShowsTheRestingValue() {
        repo.granted = repo.granted - RecordType.HEART_RATE.readPermission
        val content = viewModel(Metric.RESTING_HEART_RATE, ChartRange.DAY).content as DetailContent.HeartRateDay
        assertTrue(content.samplesPermissionMissing)
        assertTrue(content.samples.isEmpty())
        assertEquals(58.0, content.resting!!, 0.001)
    }

    @Test
    fun emptySeriesShowsTheEmptyState() {
        repo.seriesProvider = { metric, window -> FakeHealthRepository.emptySeries(metric, window) }
        assertEquals(DetailContent.Empty, viewModel().content)
    }

    @Test
    fun missingPermissionShowsAnError() {
        repo.granted = repo.granted - RecordType.STEPS.readPermission
        assertEquals(DetailContent.Error(DetailError.PERMISSION_MISSING, RecordType.STEPS), viewModel().content)
    }

    @Test
    fun rateLimitShowsAnErrorAndRetryRecovers() {
        repo.failures[Call.SERIES] = HealthAccessException.RateLimited()
        val vm = viewModel()
        assertEquals(DetailContent.Error(DetailError.RATE_LIMITED), vm.content)

        repo.failures.clear()
        vm.retry()
        assertTrue(vm.content is DetailContent.Buckets)
    }

    @Test
    fun unavailableAndUnexpectedErrorsMapToTheirStates() {
        repo.failures[Call.SERIES] = HealthAccessException.Unavailable()
        assertEquals(DetailContent.Error(DetailError.UNAVAILABLE), viewModel().content)
        repo.failures[Call.SERIES] = HealthAccessException.Unexpected(IllegalStateException("boom"))
        assertEquals(DetailContent.Error(DetailError.GENERIC), viewModel().content)
    }

    @Test
    fun tapsAreIgnoredWhileThereAreNoBuckets() = runTest {
        repo.failures[Call.SERIES] = HealthAccessException.RateLimited()
        val vm = viewModel()
        vm.recordsRequests.test {
            vm.onBucketSelected(0)
            expectNoEvents()
        }
    }

    @Test
    fun windowSurvivesProcessDeathThroughSavedState() {
        val saved = SavedStateHandle()
        val first = viewModel(saved = saved)
        first.selectRange(ChartRange.MONTH)
        first.previous()

        val restored = viewModel(saved = saved)
        assertEquals(ChartWindow(ChartRange.MONTH, today.minusMonths(1)), restored.state.value.window)
    }
}
