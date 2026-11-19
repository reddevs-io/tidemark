// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import io.reddevs.tidemark.ui.components.scaledTopAppBarHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.reddevs.tidemark.R
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.format.MeasurementFormatter

/** The order tiles appear in, row by row. */
internal val TileOrder = listOf(
    Metric.STEPS,
    Metric.ACTIVE_CALORIES,
    Metric.DISTANCE,
    Metric.SLEEP,
    Metric.RESTING_HEART_RATE,
    Metric.WEIGHT,
)

/** Stateless dashboard: top bar, history banner and the tile grid, with pull to refresh. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    formatter: MeasurementFormatter,
    onRefresh: () -> Unit,
    onOpenMetric: (Metric) -> Unit,
    onRetry: (Metric) -> Unit,
    onOpenHealthConnectSettings: () -> Unit,
    onDismissBanner: () -> Unit,
    onOpenExplorer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                expandedHeight = scaledTopAppBarHeight(),
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenExplorer) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ListAlt,
                            contentDescription = stringResource(R.string.dashboard_action_explorer),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.dashboard_action_settings),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val columns = tileColumns(maxWidth - ScreenPadding * 2, LocalDensity.current.fontScale)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(TileSpacing),
                ) {
                    state.banner?.let {
                        HistoryBannerCard(
                            banner = it,
                            formatter = formatter,
                            today = state.today,
                            onOpenHealthConnectSettings = onOpenHealthConnectSettings,
                            onDismiss = onDismissBanner,
                        )
                    }
                    TileOrder.chunked(columns).forEach { row ->
                        // Intrinsic height lets every tile in a row match the tallest one.
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(TileSpacing),
                        ) {
                            row.forEach { metric ->
                                MetricTile(
                                    metric = metric,
                                    state = state.tile(metric),
                                    formatter = formatter,
                                    today = state.today,
                                    zone = state.zone,
                                    onOpen = { onOpenMetric(metric) },
                                    onRetry = { onRetry(metric) },
                                    onOpenHealthConnectSettings = onOpenHealthConnectSettings,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Two columns on a phone, more on wider screens, and one column once a large font scale would
 * make a tile narrower than its text needs.
 */
internal fun tileColumns(available: Dp, fontScale: Float): Int {
    val minTileWidth = MinTileWidth * fontScale.coerceAtLeast(1f)
    val fit = ((available + TileSpacing) / (minTileWidth + TileSpacing)).toInt()
    return fit.coerceIn(1, MaxColumns)
}

private val ScreenPadding = 16.dp
private val TileSpacing = 12.dp
private val MinTileWidth = 150.dp
private const val MaxColumns = 3
