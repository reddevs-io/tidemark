// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.detail

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.R
import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.format.LocaleMeasurementFormatter
import io.reddevs.tidemark.settings.UnitPreference
import io.reddevs.tidemark.ui.theme.TidemarkTheme
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetricDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val formatter = LocaleMeasurementFormatter(context, Locale.UK, UnitPreference.SYSTEM)

    private fun show(state: MetricDetailUiState, actions: DetailActions = DetailActions()) {
        compose.setContent {
            TidemarkTheme(dynamicColor = false) {
                MetricDetailScreen(state, formatter, DetailSamples.zone, actions)
            }
        }
    }

    @Test
    fun everyMetricAndRangeRendersWithAChartDescription() {
        val current = mutableStateOf(DetailSamples.state(Metric.STEPS, ChartRange.DAY))
        compose.setContent {
            TidemarkTheme(dynamicColor = false) {
                MetricDetailScreen(current.value, formatter, DetailSamples.zone, DetailActions())
            }
        }
        Metric.entries.forEach { metric ->
            ChartRange.entries.forEach { range ->
                current.value = DetailSamples.state(metric, range)
                compose.waitForIdle()
                val title = context.getString(metricTitle(metric))
                val prefix = if (metric == Metric.SLEEP && range == ChartRange.DAY) {
                    context.getString(R.string.detail_hypnogram_description).substringBefore(" %1")
                } else {
                    title
                }
                compose.onNode(hasContentDescription(prefix, substring = true), useUnmergedTree = true).assertExists()
            }
        }
    }

    @Test
    fun hypnogramDescribesEveryStage() {
        show(DetailSamples.state(Metric.SLEEP, ChartRange.DAY))
        val description = compose.onNode(hasContentDescription("Sleep stages", substring = true))
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        listOf("Awake", "Light", "Deep", "REM", "Sleeping", "Out of bed", "Awake in bed", "Unknown").forEach {
            assert(description.contains("$it:")) { "$it missing from: $description" }
        }
    }

    @Test
    fun breakdownRowDrillsDown() {
        var tapped = -1
        show(DetailSamples.state(Metric.STEPS, ChartRange.WEEK), DetailActions(onBucketSelected = { tapped = it }))
        val tuesday = formatter.date(LocalDate.of(2026, 11, 17))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(tuesday))
        compose.onNodeWithText(tuesday).performClick()
        assertEquals(1, tapped)
    }

    @Test
    fun nextIsDisabledAtToday() {
        show(DetailSamples.state(Metric.WEIGHT, ChartRange.MONTH))
        compose.onNodeWithContentDescription(context.getString(R.string.detail_next_period)).assertIsNotEnabled()
    }

    @Test
    fun errorStateOffersRetry() {
        var retried = false
        show(
            DetailSamples.state(Metric.STEPS, ChartRange.WEEK).copy(content = DetailContent.Error(DetailError.RATE_LIMITED)),
            DetailActions(onRetry = { retried = true }),
        )
        compose.onNodeWithText(context.getString(R.string.error_rate_limited)).assertExists()
        compose.onNodeWithText(context.getString(R.string.action_retry)).performClick()
        assert(retried)
    }
}
