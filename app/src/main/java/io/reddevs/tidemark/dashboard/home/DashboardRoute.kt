// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.R
import io.reddevs.tidemark.data.model.Metric
import kotlinx.coroutines.launch

/** Dashboard tiles and history banner. T-005 owns this file. */
@Composable
fun DashboardRoute(
    container: AppContainer,
    onOpenMetric: (Metric) -> Unit,
    onOpenExplorer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = viewModel {
        DashboardViewModel(container.healthRepository, container.userPreferences, container.clock)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formatter by container.formatter.collectAsStateWithLifecycle()

    // Load on every resume (first entry included), so a permission changed in Health Connect shows up
    // on return. No polling.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val openFailed = stringResource(R.string.dashboard_open_settings_failed)

    DashboardScreen(
        state = state,
        formatter = formatter,
        onRefresh = { viewModel.refresh(userInitiated = true) },
        onOpenMetric = onOpenMetric,
        onRetry = viewModel::retry,
        onOpenHealthConnectSettings = {
            if (!openHealthConnectSettings(context)) scope.launch { snackbarHostState.showSnackbar(openFailed) }
        },
        onDismissBanner = viewModel::dismissBanner,
        onOpenExplorer = onOpenExplorer,
        onOpenSettings = onOpenSettings,
        snackbarHostState = snackbarHostState,
    )
}

/** Opens Health Connect's data and access screen. Returns false when no app handles it. */
internal fun openHealthConnectSettings(context: Context): Boolean = try {
    context.startActivity(HealthConnectClient.getHealthConnectManageDataIntent(context))
    true
} catch (e: ActivityNotFoundException) {
    false
}
