// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import io.reddevs.tidemark.R
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.format.DateStyle
import io.reddevs.tidemark.format.MeasurementFormatter
import java.time.LocalDate
import java.time.ZoneId

internal fun tileTestTag(metric: Metric) = "dashboard_tile_${metric.name}"

/**
 * One dashboard tile. Loading, value and no-data tiles open the metric on tap and read as one
 * element: title, value with unit, when, and source. Permission and error tiles keep their button
 * reachable instead.
 */
@Composable
internal fun MetricTile(
    metric: Metric,
    state: TileState,
    formatter: MeasurementFormatter,
    today: LocalDate,
    zone: ZoneId,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onOpenHealthConnectSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = tileText(metric, state, formatter, today, zone)
    val tag = tileTestTag(metric)
    when (state) {
        is TileState.PermissionMissing, is TileState.Error -> Card(
            modifier = modifier.semantics(mergeDescendants = true) { testTag = tag },
        ) {
            TileBody(metric, text) {
                val (label, action) = if (state is TileState.Error) {
                    stringResource(R.string.action_retry) to onRetry
                } else {
                    stringResource(R.string.action_open_health_connect_settings) to onOpenHealthConnectSettings
                }
                TextButton(onClick = action) { Text(label) }
            }
        }
        else -> {
            val openLabel = stringResource(R.string.dashboard_tile_open)
            Card(
                onClick = onOpen,
                modifier = modifier.clearAndSetSemantics {
                    testTag = tag
                    contentDescription = text.description
                    role = Role.Button
                    onClick(label = openLabel) { onOpen(); true }
                },
            ) {
                TileBody(metric, text) {
                    when (state) {
                        is TileState.Loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        is TileState.Value -> {
                            state.sleep?.let { SleepStageBar(it, Modifier.padding(vertical = 4.dp)) }
                            state.sparkline?.let { Sparkline(it, metric.sparklineAsColumns()) }
                        }
                        is TileState.NoData -> state.sparkline?.let { Sparkline(it, metric.sparklineAsColumns()) }
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun TileBody(metric: Metric, text: TileText, extra: @Composable () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(metric.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text.title, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(4.dp))
        text.value?.let { Text(it, style = MaterialTheme.typography.headlineSmall) }
        text.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        text.subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        text.source?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        extra()
    }
}

/** The words on a tile; [description] is what TalkBack reads for the whole tile. */
internal data class TileText(
    val title: String,
    val value: String? = null,
    val subtitle: String? = null,
    val source: String? = null,
    val message: String? = null,
) {
    val description: String get() = listOfNotNull(title, value, subtitle, source, message).joinToString(", ")
}

@Composable
internal fun tileText(
    metric: Metric,
    state: TileState,
    formatter: MeasurementFormatter,
    today: LocalDate,
    zone: ZoneId,
): TileText {
    val title = stringResource(metric.titleRes())
    return when (state) {
        TileState.Loading -> TileText(title, message = stringResource(R.string.state_loading))
        is TileState.Value -> {
            val sleep = state.sleep
            val latest = state.latest
            when {
                sleep != null -> TileText(
                    title = title,
                    value = valueText(metric, state.value, formatter),
                    subtitle = stringResource(
                        R.string.dashboard_time_span,
                        formatter.time(sleep.start),
                        formatter.time(sleep.end),
                    ),
                    source = stringResource(R.string.dashboard_source, appLabel(sleep.source.packageName)),
                )
                latest != null -> TileText(
                    title = title,
                    value = valueText(metric, state.value, formatter),
                    subtitle = dayLabel(latest.time.atZone(zone).toLocalDate(), today, formatter),
                    source = stringResource(R.string.dashboard_source, appLabel(latest.source.packageName)),
                )
                else -> TileText(
                    title = title,
                    value = valueText(metric, state.value, formatter),
                    subtitle = stringResource(R.string.dashboard_today),
                )
            }
        }
        is TileState.NoData -> TileText(
            title,
            message = stringResource(
                when (metric) {
                    Metric.STEPS, Metric.ACTIVE_CALORIES, Metric.DISTANCE -> R.string.dashboard_no_data_today
                    Metric.SLEEP -> R.string.dashboard_no_sleep
                    Metric.RESTING_HEART_RATE, Metric.WEIGHT -> R.string.dashboard_no_recent_value
                },
            ),
        )
        is TileState.PermissionMissing -> TileText(title, message = stringResource(R.string.dashboard_permission_missing))
        is TileState.Error -> TileText(
            title,
            message = stringResource(
                when (state.kind) {
                    ErrorKind.RATE_LIMITED -> R.string.error_rate_limited
                    ErrorKind.UNAVAILABLE -> R.string.error_unavailable
                    ErrorKind.OTHER -> R.string.error_generic
                },
            ),
        )
    }
}

@Composable
private fun valueText(metric: Metric, value: Double, formatter: MeasurementFormatter): String =
    if (metric == Metric.STEPS) {
        val count = value.toLong()
        pluralStringResource(
            R.plurals.dashboard_steps_value,
            count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            formatter.steps(count),
        )
    } else {
        formatter.metricValue(metric, value)
    }

@Composable
private fun dayLabel(day: LocalDate, today: LocalDate, formatter: MeasurementFormatter): String = when (day) {
    today -> stringResource(R.string.dashboard_today)
    today.minusDays(1) -> stringResource(R.string.dashboard_yesterday)
    else -> formatter.date(day, DateStyle.MEDIUM)
}

/** The app's label through PackageManager, or the package name when it can't be resolved. */
@Composable
private fun appLabel(packageName: String): String {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    return remember(packageName, inspection) {
        if (inspection) {
            packageName
        } else {
            try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }
        }
    }
}

internal fun Metric.titleRes(): Int = when (this) {
    Metric.STEPS -> R.string.dashboard_tile_steps
    Metric.ACTIVE_CALORIES -> R.string.dashboard_tile_active_calories
    Metric.DISTANCE -> R.string.dashboard_tile_distance
    Metric.SLEEP -> R.string.dashboard_tile_sleep
    Metric.RESTING_HEART_RATE -> R.string.dashboard_tile_resting_heart_rate
    Metric.WEIGHT -> R.string.dashboard_tile_weight
}

private fun Metric.icon(): ImageVector = when (this) {
    Metric.STEPS -> Icons.AutoMirrored.Outlined.DirectionsWalk
    Metric.ACTIVE_CALORIES -> Icons.Outlined.LocalFireDepartment
    Metric.DISTANCE -> Icons.Outlined.Straighten
    Metric.SLEEP -> Icons.Outlined.Bedtime
    Metric.RESTING_HEART_RATE -> Icons.Outlined.MonitorHeart
    Metric.WEIGHT -> Icons.Outlined.MonitorWeight
}
