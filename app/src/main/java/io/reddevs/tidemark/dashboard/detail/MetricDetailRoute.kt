// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.detail

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.navigation.MetricDetail
import java.time.LocalDate

/** Detail chart with day, week, month and year ranges. T-006 owns this file. */
@Composable
fun MetricDetailRoute(
    container: AppContainer,
    args: MetricDetail,
    onOpenRecords: (RecordType, LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: MetricDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                MetricDetailViewModel(
                    repository = container.healthRepository,
                    metric = args.metric,
                    initialWindow = ChartWindow(args.range, args.anchor),
                    today = { LocalDate.now(container.clock) },
                    locale = { container.formatter.value.locale },
                    savedState = createSavedStateHandle(),
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formatter by container.formatter.collectAsStateWithLifecycle()
    val openRecords by rememberUpdatedState(onOpenRecords)
    LaunchedEffect(viewModel) {
        viewModel.recordsRequests.collect { openRecords(it.type, it.day) }
    }
    val context = LocalContext.current
    val labels = remember(context) { AppLabels(context) }

    MetricDetailScreen(
        state = state,
        formatter = formatter,
        zone = container.clock.zone,
        actions = DetailActions(
            onSelectRange = viewModel::selectRange,
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            onRetry = viewModel::retry,
            onBucketSelected = viewModel::onBucketSelected,
            onDaySelected = viewModel::onDaySelected,
            onBack = onBack,
        ),
        sourceLabel = labels::label,
    )
}

/**
 * Resolves a data origin's app label. Health Connect apps are visible through the manifest's
 * rationale-intent query; anything unresolved falls back to the package name.
 */
private class AppLabels(context: Context) {
    private val packageManager = context.packageManager
    private val cache = mutableMapOf<String, String>()

    fun label(packageName: String): String = cache.getOrPut(packageName) {
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
