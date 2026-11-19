// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.reddevs.tidemark.R
import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.HeartRateSample
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.format.MeasurementFormatter
import io.reddevs.tidemark.ui.components.EmptyState
import io.reddevs.tidemark.ui.components.ErrorState
import io.reddevs.tidemark.ui.components.LoadingState
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Callbacks from the detail screen, grouped so previews can pass a no-op set. */
internal data class DetailActions(
    val onSelectRange: (ChartRange) -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenHealthConnectSettings: () -> Unit = {},
    val onBucketSelected: (Int) -> Unit = {},
    val onDaySelected: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MetricDetailScreen(
    state: MetricDetailUiState,
    formatter: MeasurementFormatter,
    zone: ZoneId,
    actions: DetailActions,
    sourceLabel: (String) -> String = { it },
) {
    val title = stringResource(metricTitle(state.metric))
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        // Hours without activity would make the day breakdown 24 rows long; leave them out.
        val rows = (state.content as? DetailContent.Buckets)?.series?.points.orEmpty()
            .withIndex()
            .filter { it.value.value != null && !(state.window.range == ChartRange.DAY && it.value.value == 0.0) }
        val labels = remember(formatter, zone) { BucketLabels(formatter, zone) }
        val scale = remember(formatter, state.metric) { ChartScale(state.metric, formatter) }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "range") { RangeSwitcher(state.window.range, actions.onSelectRange) }
            item(key = "period") { PeriodNavigator(formatter.rangeLabel(state.window), state.canGoNext, actions) }
            item(key = "content") {
                DetailBody(state, formatter, zone, labels, scale, actions, sourceLabel)
            }
            if (rows.isNotEmpty()) {
                item(key = "breakdown") {
                    Text(
                        stringResource(R.string.detail_breakdown),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .semantics { heading() },
                    )
                }
                items(rows, key = { "bucket-${it.index}" }) { (index, point) ->
                    BucketRow(
                        label = labels.full(point, state.window.range),
                        value = scale.value(point.value!!),
                        clickLabel = stringResource(
                            if (state.window.range == ChartRange.YEAR) R.string.detail_show_month else R.string.detail_show_records,
                        ),
                        onClick = { actions.onBucketSelected(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeSwitcher(selected: ChartRange, onSelect: (ChartRange) -> Unit) {
    val ranges = ChartRange.entries
    val label = stringResource(R.string.detail_range_switcher)
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = label },
    ) {
        ranges.forEachIndexed { i, range ->
            SegmentedButton(
                selected = range == selected,
                onClick = { onSelect(range) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = ranges.size),
                label = { Text(stringResource(rangeName(range)), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun PeriodNavigator(label: String, canGoNext: Boolean, actions: DetailActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.detail_previous_period))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = actions.onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.detail_next_period))
        }
    }
}

@Composable
private fun DetailBody(
    state: MetricDetailUiState,
    formatter: MeasurementFormatter,
    zone: ZoneId,
    labels: BucketLabels,
    scale: ChartScale,
    actions: DetailActions,
    sourceLabel: (String) -> String,
) {
    val boxModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 240.dp)
    when (val content = state.content) {
        DetailContent.Loading -> Box(boxModifier) { LoadingState() }
        DetailContent.Empty -> Box(boxModifier) { EmptyState(stringResource(R.string.detail_empty)) }
        is DetailContent.Error -> Box(boxModifier) {
            if (content.error == DetailError.PERMISSION_MISSING) {
                PermissionMissing(actions)
            } else {
                ErrorState(message = stringResource(errorMessage(content.error)), onRetry = actions.onRetry)
            }
        }
        is DetailContent.Buckets -> BucketsBody(state, content, formatter, labels, scale, actions)
        is DetailContent.SleepDay -> SleepDayBody(content, formatter, zone, actions, sourceLabel)
        is DetailContent.HeartRateDay -> HeartRateDayBody(state, content, formatter, zone, scale, actions)
    }
}

@Composable
private fun BucketsBody(
    state: MetricDetailUiState,
    content: DetailContent.Buckets,
    formatter: MeasurementFormatter,
    labels: BucketLabels,
    scale: ChartScale,
    actions: DetailActions,
) {
    val range = state.window.range
    val series = content.series
    val stats = summaryStats(state.metric, range, series)
    val spec = remember(series, scale, labels) {
        val points = series.points.mapIndexedNotNull { i, p ->
            p.value?.let { v ->
                ChartPoint(
                    x = i,
                    y = scale.toChart(v),
                    min = p.min?.let(scale::toChart),
                    max = p.max?.let(scale::toChart),
                )
            }
        }
        ChartSpec(
            points = points,
            slots = series.points.size,
            xLabel = { i -> series.points.getOrNull(i)?.let { labels.axis(it, range) }.orEmpty() },
            labelSpacing = axisLabelSpacing(range),
            yLabel = scale::label,
        )
    }
    val description = chartDescription(state, formatter, stats.spoken(scale))
    Column(Modifier.padding(horizontal = 16.dp)) {
        when (content.style) {
            ChartStyle.BARS -> DetailBarChart(spec, description, actions.onBucketSelected)
            ChartStyle.LINES -> DetailLineChart(spec, description, actions.onBucketSelected)
        }
        SummaryRow(stats.map { stringResource(statLabel(it.kind)) to scale.value(it.value) })
        if (range == ChartRange.YEAR && chartStyle(state.metric) == ChartStyle.BARS && state.metric != Metric.SLEEP) {
            Note(stringResource(R.string.detail_year_values_note))
        }
        if (range == ChartRange.DAY) DayRecordsButton(actions.onDaySelected)
    }
}

@Composable
private fun SleepDayBody(
    content: DetailContent.SleepDay,
    formatter: MeasurementFormatter,
    zone: ZoneId,
    actions: DetailActions,
    sourceLabel: (String) -> String,
) {
    val model = remember(content.night) { HypnogramModel.of(content.night) }
    val stages = model.totals.map { (stage, duration) ->
        stringResource(R.string.detail_stat_spoken, stringResource(stageName(stage)), formatter.duration(duration))
    }
    val description = stringResource(
        R.string.detail_hypnogram_description,
        formatter.time(model.start),
        formatter.time(model.end),
        formatter.duration(model.asleep),
        stages.joinToString(". "),
    )
    Column(Modifier.padding(horizontal = 16.dp)) {
        Hypnogram(
            model = model,
            formatter = formatter,
            zone = zone,
            description = description,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clickable(onClickLabel = stringResource(R.string.detail_show_records), onClick = actions.onDaySelected),
        )
        SummaryRow(
            listOf(
                stringResource(R.string.detail_stat_asleep) to formatter.duration(model.asleep),
                stringResource(R.string.detail_stat_in_bed) to formatter.duration(model.inBed),
            ),
        )
        StageLegend(model, formatter)
        if (model.stageless) Note(stringResource(R.string.detail_hypnogram_no_stages))
        Note(stringResource(R.string.detail_source, sourceLabel(content.night.source.packageName)))
        DayRecordsButton(actions.onDaySelected)
    }
}

@Composable
private fun StageLegend(model: HypnogramModel, formatter: MeasurementFormatter) {
    val colors = MaterialTheme.colorScheme
    val total = model.inBed.toMillis().coerceAtLeast(1)
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        model.totals.forEach { (stage, duration) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(stageColor(stage, colors)),
                )
                Text(
                    stringResource(stageName(stage)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f),
                )
                Text(
                    "${formatter.duration(duration)} · ${percent(duration.toMillis(), total, formatter)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun percent(part: Long, total: Long, formatter: MeasurementFormatter): String =
    NumberFormat.getPercentInstance(formatter.locale).format(part.toDouble() / total)

@Composable
private fun HeartRateDayBody(
    state: MetricDetailUiState,
    content: DetailContent.HeartRateDay,
    formatter: MeasurementFormatter,
    zone: ZoneId,
    scale: ChartScale,
    actions: DetailActions,
) {
    val stats = heartRateDayStats(content)
    val day = state.window.anchor
    val dayStart = day.atStartOfDay(zone).toInstant()
    val slots = HEART_RATE_SLOTS_PER_DAY
    val reference = content.resting?.let { stringResource(R.string.detail_resting_reference, formatter.heartRate(it)) }
    val spec = remember(content, zone, formatter) {
        val points = heartRateSlots(content.samples, dayStart).map { (slot, bpm) -> ChartPoint(slot, bpm) }
        ChartSpec(
            points = points,
            slots = slots,
            xLabel = { slot -> formatter.time(dayStart.plus(slot.toLong() * SLOT_MINUTES, ChronoUnit.MINUTES)) },
            labelSpacing = slots / 4,
            yLabel = { formatter.heartRate(it) },
            referenceY = content.resting,
            referenceLabel = reference,
        )
    }
    val description = chartDescription(state, formatter, stats.spoken(scale))
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (spec.points.isNotEmpty()) {
            Text(stringResource(R.string.detail_heart_rate_samples), style = MaterialTheme.typography.labelLarge)
            DetailLineChart(spec, description, onTap = { actions.onDaySelected() }, showPoints = false)
        } else if (!content.samplesPermissionMissing) {
            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp)) { EmptyState(stringResource(R.string.detail_empty)) }
        }
        SummaryRow(stats.map { stringResource(statLabel(it.kind)) to formatter.heartRate(it.value) })
        if (content.samplesPermissionMissing) Note(stringResource(R.string.detail_heart_rate_permission_missing))
        DayRecordsButton(actions.onDaySelected)
    }
}

/** Five-minute mean heart rate slots, so a day of second-level samples stays light to draw. */
internal fun heartRateSlots(
    samples: List<HeartRateSample>,
    dayStart: Instant,
): List<Pair<Int, Double>> =
    samples
        .groupBy { (Duration.between(dayStart, it.time).toMinutes() / SLOT_MINUTES).toInt() }
        .filterKeys { it in 0 until HEART_RATE_SLOTS_PER_DAY }
        .toSortedMap()
        .map { (slot, list) -> slot to list.map { it.bpm }.average() }

internal const val SLOT_MINUTES = 5L
internal const val HEART_RATE_SLOTS_PER_DAY = (24 * 60 / SLOT_MINUTES).toInt()

@Composable
private fun SummaryRow(stats: List<Pair<String, String>>) {
    if (stats.isEmpty()) return
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stats.forEach { (label, value) ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.widthIn(min = 96.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun BucketRow(label: String, value: String, clickLabel: String, onClick: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = clickLabel, onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun DayRecordsButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.padding(vertical = 8.dp)) {
        Text(stringResource(R.string.detail_show_day_records))
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun List<SummaryStat>.spoken(scale: ChartScale): String =
    map { stringResource(R.string.detail_stat_spoken, stringResource(statLabel(it.kind)), scale.value(it.value)) }
        .joinToString(". ")

@Composable
private fun chartDescription(state: MetricDetailUiState, formatter: MeasurementFormatter, summary: String): String =
    stringResource(
        R.string.detail_chart_description,
        stringResource(metricTitle(state.metric)),
        formatter.rangeLabel(state.window),
        summary,
    )

/** The permission error offers Health Connect's settings, where the user can grant the type. */
@Composable
private fun PermissionMissing(actions: DetailActions) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.detail_error_permission),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = actions.onOpenHealthConnectSettings) {
            Text(stringResource(R.string.action_open_health_connect_settings))
        }
        OutlinedButton(onClick = actions.onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@StringRes
private fun errorMessage(error: DetailError): Int = when (error) {
    DetailError.PERMISSION_MISSING -> R.string.detail_error_permission
    DetailError.RATE_LIMITED -> R.string.error_rate_limited
    DetailError.UNAVAILABLE -> R.string.error_unavailable
    DetailError.GENERIC -> R.string.error_generic
}
