// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import io.reddevs.tidemark.ui.components.AppLabels
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.format.LocaleMeasurementFormatter
import io.reddevs.tidemark.settings.UnitPreference
import io.reddevs.tidemark.testing.FakeHealthRepository.Call
import io.reddevs.tidemark.testing.TestRecords
import io.reddevs.tidemark.ui.theme.TidemarkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Scrolls the real list over 5,000 records and checks, through the fake's call count, that paging stays lazy. */
@RunWith(AndroidJUnit4::class)
class RecordListScrollTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/Paris")
    private val clock = Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), zone)
    private val repo = RecordingHealthRepository().apply {
        val first = LocalDate.of(2026, 11, 14).atStartOfDay(zone).toInstant()
        records[RecordType.STEPS] = (0 until 5_000).map { i ->
            TestRecords.steps("s$i", first + Duration.ofMinutes(i.toLong()), count = i.toLong(), packageName = "com.example.fit")
        }
    }

    @Test
    fun scrollingLoadsOnlyTheNeededPages() {
        val vm = RecordListViewModel(repo, clock, RecordType.STEPS)
        compose.setContent {
            TidemarkTheme(dynamicColor = false) {
                val state by vm.state.collectAsState()
                RecordListScreen(
                    state = state,
                    formatter = LocaleMeasurementFormatter(context, Locale.US, UnitPreference.METRIC),
                    appLabels = AppLabels(context.packageManager),
                    clock = clock,
                    onSelectRange = vm::selectRange,
                    onSelectSource = vm::selectSource,
                    onLastVisibleItem = vm::onLastVisibleItem,
                    onRetry = vm::retry,
                    onOpenSettings = {},
                    onOpenRecord = {},
                    onBack = {},
                )
            }
        }
        compose.waitForIdle()
        assertEquals("only the first page before scrolling", 1, repo.calls[Call.READ_PAGE])

        // Scroll down as a user would: the list only holds what's loaded, plus a footer row.
        var position = 0
        while (position < 250) {
            position = minOf(250, vm.state.value.records.size)
            compose.onNodeWithTag(RECORD_LIST_TAG).performScrollToIndex(position)
            compose.waitForIdle()
        }
        compose.onNodeWithText("250 steps").assertExists()

        val calls = repo.calls.getValue(Call.READ_PAGE)
        val loaded = vm.state.value.records.size
        // Row 250 needs page 3; prefetch may add page 4, never more.
        assertTrue("$calls page reads", calls in 3..4)
        assertEquals(calls * 100, loaded)
        assertEquals((0 until calls).map { if (it == 0) null else "${it * 100}" }, repo.pageQueries.map { it.pageToken })
    }
}
