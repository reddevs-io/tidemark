// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import io.reddevs.tidemark.ui.components.AppLabels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
            shape = TileShape,
            colors = tileColors(),
            border = tileBorder(),
        ) {
            TileBody(metric, text) {
                val (label, action) = if (state is TileState.Error) {
                    stringResource(R.string.action_retry) to onRetry
                } else {
                    stringResource(R.string.action_open_health_connect_settings) to onOpenHealthConnectSettings
                }
                TextButton(onClick = action, shape = MaterialTheme.shapes.small) { Text(label) }
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
                shape = TileShape,
                colors = tileColors(),
                border = tileBorder(),
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
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricBadge(metric)
            Text(
                text.title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.height(4.dp))
        text.value?.let {
            Text(
                withSmallUnits(
                    it,
                    SpanStyle(
                        fontSize = 0.5.em,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.em,
                        color = colors.onSurfaceVariant,
                    ),
                ),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        text.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        text.subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        text.source?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        extra()
    }
}

/** A 26 dp rounded square in the primary container colour holding the metric's icon. */
@Composable
private fun MetricBadge(metric: Metric) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(26.dp)
            .background(colors.primaryContainer, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(metric.icon(), contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(17.dp))
    }
}

/**
 * Shrinks the unit words in a formatted value ("8,432 steps", "7 h 32 min", "72.5 kg") so the
 * numbers stand out. Every run of letters counts as a unit; the text itself is unchanged.
 */
internal fun withSmallUnits(value: String, unitStyle: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(value)
    var i = 0
    while (i < value.length) {
        if (value[i].isLetter()) {
            val start = i
            while (i < value.length && value[i].isLetter()) i++
            addStyle(unitStyle, start, i)
        } else {
            i++
        }
    }
}

private val TileShape = RoundedCornerShape(20.dp)

@Composable
private fun tileColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun tileBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

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

/** The app's label from the shared [AppLabels] cache, or the package name in previews. */
@Composable
private fun appLabel(packageName: String): String {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    return remember(packageName, inspection) {
        if (inspection) {
            packageName
        } else {
            AppLabels.get(context).label(packageName)
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
    Metric.STEPS -> Icons.AutoMirrored.Rounded.DirectionsWalk
    Metric.ACTIVE_CALORIES -> Icons.Rounded.LocalFireDepartment
    Metric.DISTANCE -> Icons.Rounded.Straighten
    Metric.SLEEP -> Icons.Rounded.Bedtime
    Metric.RESTING_HEART_RATE -> Icons.Rounded.MonitorHeart
    Metric.WEIGHT -> Icons.Rounded.MonitorWeight
}
