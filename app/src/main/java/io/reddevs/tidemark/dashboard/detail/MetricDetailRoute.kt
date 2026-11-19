// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.detail

import androidx.compose.runtime.Composable
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.navigation.MetricDetail
import io.reddevs.tidemark.ui.components.PlaceholderScreen
import java.time.LocalDate

/** Detail chart with day, week, month and year ranges. T-006 owns this file. */
@Composable
fun MetricDetailRoute(
    container: AppContainer,
    args: MetricDetail,
    onOpenRecords: (RecordType, LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = args.metric.name, onBack = onBack)
}
