// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import io.reddevs.tidemark.ui.components.AppLabels
import io.reddevs.tidemark.data.HealthConnectIntents
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.Record
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.R
import io.reddevs.tidemark.data.model.DataSource
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.format.DateStyle
import io.reddevs.tidemark.format.MeasurementFormatter
import io.reddevs.tidemark.ui.components.EmptyState
import io.reddevs.tidemark.ui.components.ErrorState
import io.reddevs.tidemark.ui.components.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal const val RECORD_LIST_TAG = "explorer_record_list"

@Composable
internal fun RecordListRoute(
    container: AppContainer,
    viewModel: RecordListViewModel,
    onOpenRecord: (RecordType, String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formatter by container.formatter.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appLabels = remember(context) { AppLabels.get(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    RecordListScreen(
        state = state,
        formatter = formatter,
        appLabels = appLabels,
        clock = container.clock,
        onSelectRange = viewModel::selectRange,
        onSelectSource = viewModel::selectSource,
        onLastVisibleItem = viewModel::onLastVisibleItem,
        onRetry = viewModel::retry,
        onOpenSettings = { HealthConnectIntents.openAccess(context) },
        onOpenRecord = { record -> onOpenRecord(state.type, record.metadata.id) },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordListScreen(
    state: RecordListState,
    formatter: MeasurementFormatter,
    appLabels: AppLabels,
    clock: Clock,
    onSelectRange: (ExplorerRange) -> Unit,
    onSelectSource: (DataSource?) -> Unit,
    onLastVisibleItem: (Int) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecord: (Record) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val summaries = remember(context, formatter) { RecordSummaries(context, formatter) }
    val listState = rememberLazyListState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(state.type.label)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            RangeChips(state, formatter, clock, onSelectRange)
            SourceChips(state, appLabels, onSelectSource)
            HorizontalDivider()
            val bodyModifier = Modifier.fillMaxSize()
            when {
                state.loadingFirstPage -> LoadingState(bodyModifier)
                state.records.isEmpty() && state.error == LoadError.PERMISSION_MISSING ->
                    PermissionMissing(state.type, onOpenSettings, bodyModifier)
                state.records.isEmpty() && state.error != null ->
                    ErrorState(bodyModifier, message = stringResource(state.error.message), onRetry = onRetry)
                state.records.isEmpty() -> EmptyState(
                    stringResource(if (state.selectedSource == null) R.string.explorer_empty else R.string.explorer_empty_source),
                    bodyModifier,
                )
                else -> RecordList(state, listState, summaries, appLabels, onLastVisibleItem, onRetry, onOpenRecord)
            }
        }
    }
}

@Composable
private fun RecordList(
    state: RecordListState,
    listState: LazyListState,
    summaries: RecordSummaries,
    appLabels: AppLabels,
    onLastVisibleItem: (Int) -> Unit,
    onRetry: () -> Unit,
    onOpenRecord: (Record) -> Unit,
) {
    // Manual paging: report the last visible row, and the ViewModel loads the next page near the end.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.let { (it.visibleItemsInfo.lastOrNull()?.index ?: -1) to it.totalItemsCount } }
            .distinctUntilChanged()
            .collect { (lastVisible, _) -> onLastVisibleItem(lastVisible) }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(RECORD_LIST_TAG),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        itemsIndexed(state.records, contentType = { _, _ -> "record" }) { _, record ->
            RecordRow(record, summaries, appLabels, onClick = { onOpenRecord(record) })
            HorizontalDivider()
        }
        item(contentType = "footer") { ListFooter(state, onRetry) }
    }
}

@Composable
private fun RecordRow(record: Record, summaries: RecordSummaries, appLabels: AppLabels, onClick: () -> Unit) {
    val packageName = record.metadata.dataOrigin.packageName
    val appLabel = remember(packageName) { appLabels.label(packageName) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        overlineContent = { Text(summaries.time(record)) },
        headlineContent = { Text(summaries.value(record)) },
        supportingContent = { Text(appLabel) },
        leadingContent = { AppIcon(packageName, appLabels) },
    )
}

@Composable
internal fun AppIcon(packageName: String, appLabels: AppLabels, size: Dp = 40.dp) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, packageName, sizePx) {
        value = withContext(Dispatchers.IO) { appLabels.icon(packageName, sizePx) }
    }
    val modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp))
    bitmap?.let { Image(it, contentDescription = null, modifier = modifier) }
        ?: Icon(Icons.Filled.Apps, contentDescription = null, modifier = modifier.padding(6.dp))
}

@Composable
private fun ListFooter(state: RecordListState, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            state.loadingMore -> CircularProgressIndicator(Modifier.size(24.dp))
            state.error != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.explorer_load_more_error), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
            state.endReached -> Text(
                pluralStringResource(R.plurals.explorer_record_count, state.records.size, state.records.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionMissing(type: RecordType, onOpenSettings: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.explorer_permission_missing, stringResource(type.label)),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.action_open_health_connect_settings)) }
    }
}

@Composable
private fun RangeChips(
    state: RecordListState,
    formatter: MeasurementFormatter,
    clock: Clock,
    onSelectRange: (ExplorerRange) -> Unit,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val range = state.range
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val day = (range as? ExplorerRange.Day)?.date ?: state.drillDownDay
        if (day != null) {
            item {
                FilterChip(
                    selected = range == ExplorerRange.Day(day),
                    onClick = { onSelectRange(ExplorerRange.Day(day)) },
                    label = { Text(formatter.date(day, DateStyle.MEDIUM)) },
                )
            }
        }
        item { RangeChip(R.string.explorer_range_today, range == ExplorerRange.Today) { onSelectRange(ExplorerRange.Today) } }
        item { RangeChip(R.string.explorer_range_7_days, range == ExplorerRange.Last7Days) { onSelectRange(ExplorerRange.Last7Days) } }
        item { RangeChip(R.string.explorer_range_30_days, range == ExplorerRange.Last30Days) { onSelectRange(ExplorerRange.Last30Days) } }
        item {
            FilterChip(
                selected = range is ExplorerRange.Custom,
                onClick = { pickerOpen = true },
                label = {
                    Text(
                        if (range is ExplorerRange.Custom) {
                            stringResource(
                                R.string.explorer_date_span,
                                formatter.date(range.first, DateStyle.DAY_MONTH),
                                formatter.date(range.last, DateStyle.DAY_MONTH),
                            )
                        } else {
                            stringResource(R.string.explorer_range_custom)
                        },
                    )
                },
            )
        }
    }
    if (pickerOpen) {
        CustomRangeDialog(
            initial = range as? ExplorerRange.Custom,
            today = LocalDate.now(clock),
            onDismiss = { pickerOpen = false },
            onConfirm = {
                pickerOpen = false
                onSelectRange(it)
            },
        )
    }
}

@Composable
private fun RangeChip(label: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label)) })
}

@Composable
private fun SourceChips(state: RecordListState, appLabels: AppLabels, onSelectSource: (DataSource?) -> Unit) {
    if (state.sources.isEmpty()) return
    val sources = remember(state.sources) { state.sources.map { it to appLabels.label(it.packageName) }.sortedBy { it.second.lowercase() } }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = state.selectedSource == null,
                onClick = { onSelectSource(null) },
                label = { Text(stringResource(R.string.explorer_source_all)) },
            )
        }
        sources.forEach { (source, label) ->
            item(key = source.packageName) {
                FilterChip(
                    selected = state.selectedSource == source,
                    onClick = { onSelectSource(source) },
                    label = { Text(label) },
                    leadingIcon = { AppIcon(source.packageName, appLabels, size = 18.dp) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(
    initial: ExplorerRange.Custom?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (ExplorerRange.Custom) -> Unit,
) {
    // The Material date pickers work in UTC milliseconds at midnight.
    fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.toUtcDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val todayMillis = today.toUtcMillis()
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.first?.toUtcMillis(),
        initialSelectedEndDateMillis = initial?.last?.toUtcMillis(),
        yearRange = (today.year - 10)..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis

            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    val start = pickerState.selectedStartDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = start != null,
                onClick = {
                    val first = start?.toUtcDate() ?: return@TextButton
                    val last = pickerState.selectedEndDateMillis?.toUtcDate() ?: first
                    onConfirm(ExplorerRange.Custom(first, last))
                },
            ) { Text(stringResource(R.string.explorer_action_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.explorer_action_cancel)) } },
    ) {
        DateRangePicker(
            state = pickerState,
            modifier = Modifier.weight(1f),
            title = { Text(stringResource(R.string.explorer_date_picker_title), Modifier.padding(start = 24.dp, top = 16.dp)) },
        )
    }
}
