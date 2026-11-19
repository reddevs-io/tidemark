// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import androidx.compose.runtime.Composable
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.ui.components.PlaceholderScreen

/** Dashboard tiles and history banner. T-005 owns this file. */
@Composable
fun DashboardRoute(
    container: AppContainer,
    onOpenMetric: (Metric) -> Unit,
    onOpenExplorer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    PlaceholderScreen(title = "Tidemark", body = "Dashboard")
}
