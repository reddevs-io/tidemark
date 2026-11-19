// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.R
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.format.LocaleMeasurementFormatter
import io.reddevs.tidemark.navigation.RecordDetail
import io.reddevs.tidemark.settings.UnitPreference
import io.reddevs.tidemark.ui.components.EmptyState
import io.reddevs.tidemark.ui.components.ErrorState
import io.reddevs.tidemark.ui.components.LoadingState
import io.reddevs.tidemark.ui.theme.TidemarkTheme
import java.time.Instant
import java.time.ZoneOffset

/** Generic field dump of one record and its metadata, rendered from [RecordFieldExtractor] sections. */
@Composable
fun RecordDetailRoute(container: AppContainer, args: RecordDetail, onBack: () -> Unit) {
    val viewModel: RecordDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RecordDetailViewModel(container.healthRepository, args.type, args.recordId) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formatter by container.formatter.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appLabels = remember(context) { AppLabels.get(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    val record = (state as? RecordDetailState.Loaded)?.record
    val sections = remember(record, formatter, context) {
        record?.let { RecordFieldExtractor(context, formatter, appLabels::label).extract(it) }
    }
    RecordDetailScreen(
        type = args.type,
        state = state,
        sections = sections,
        onRetry = viewModel::load,
        onOpenSettings = { openHealthConnectSettings(context) },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordDetailScreen(
    type: RecordType,
    state: RecordDetailState,
    sections: List<FieldSection>?,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(type.label)) }, navigationIcon = { BackButton(onBack) })
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (state) {
            RecordDetailState.Loading -> LoadingState(modifier)
            RecordDetailState.NotFound -> EmptyState(stringResource(R.string.explorer_record_not_found), modifier)
            is RecordDetailState.Failed -> if (state.error == LoadError.PERMISSION_MISSING) {
                Column(
                    modifier = modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.explorer_permission_missing, stringResource(type.label)))
                    Button(onClick = onOpenSettings) { Text(stringResource(R.string.action_open_health_connect_settings)) }
                }
            } else {
                ErrorState(modifier, message = stringResource(state.error.message), onRetry = onRetry)
            }
            is RecordDetailState.Loaded -> SectionList(sections.orEmpty(), modifier)
        }
    }
}

@Composable
private fun SectionList(sections: List<FieldSection>, modifier: Modifier) {
    // Collapsible lists start closed: a heart rate record can hold thousands of samples.
    var expanded by rememberSaveable { mutableStateOf(emptySet<String>()) }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        sections.forEach { section ->
            val key = section.kind.name
            val open = !section.collapsible || key in expanded
            item(key = "header-$key") {
                SectionHeader(
                    section = section,
                    open = open,
                    onToggle = { expanded = if (key in expanded) expanded - key else expanded + key },
                )
            }
            if (open) sectionBody(section)
        }
    }
}

private fun LazyListScope.sectionBody(section: FieldSection) {
    items(section.fields, key = { "${section.kind}-field-${it.key}" }) { FieldRow(it) }
    if (section.collapsible && section.entries.isEmpty()) {
        item(key = "${section.kind}-empty") {
            Text(
                stringResource(R.string.explorer_section_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
    items(section.entries.size, key = { "${section.kind}-entry-$it" }) { index ->
        val entry = section.entries[index]
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall)
            entry.fields.forEach { FieldRow(it, Modifier.padding(start = 8.dp)) }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun SectionHeader(section: FieldSection, open: Boolean, onToggle: () -> Unit) {
    val stateLabel = stringResource(if (open) R.string.explorer_expanded else R.string.explorer_collapsed)
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(if (section.collapsible) Modifier.clickable(onClick = onToggle).semantics { stateDescription = stateLabel } else Modifier)
        .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            section.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (section.collapsible) {
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
    }
}

@Composable
private fun FieldRow(field: Field, modifier: Modifier = Modifier) {
    SelectionContainer(modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(field.value, style = MaterialTheme.typography.bodyLarge)
            field.raw?.takeIf { it != field.value }?.let { raw ->
                Text(
                    stringResource(R.string.explorer_stored_value, raw),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(name = "Sleep record")
@Preview(name = "Sleep record, dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Sleep record, French", locale = "fr")
@Composable
private fun RecordDetailPreview() {
    val context = LocalContext.current
    val start = Instant.parse("2026-11-18T22:30:00Z")
    val record = SleepSessionRecord(
        startTime = start,
        startZoneOffset = ZoneOffset.ofHours(1),
        endTime = start.plusSeconds(8 * 3600),
        endZoneOffset = ZoneOffset.ofHours(1),
        metadata = Metadata.autoRecorded(Device(Device.TYPE_WATCH, "Example", "Watch 3")),
        stages = listOf(
            SleepSessionRecord.Stage(start, start.plusSeconds(3600), SleepSessionRecord.STAGE_TYPE_LIGHT),
            SleepSessionRecord.Stage(start.plusSeconds(3600), start.plusSeconds(7200), SleepSessionRecord.STAGE_TYPE_DEEP),
        ),
    )
    val formatter = LocaleMeasurementFormatter(context, LocalConfiguration.current.locales[0], UnitPreference.SYSTEM)
    val sections = RecordFieldExtractor(context, formatter) { it }.extract(record)
    TidemarkTheme(dynamicColor = false) {
        RecordDetailScreen(
            type = RecordType.SLEEP_SESSION,
            state = RecordDetailState.Loaded(record),
            sections = sections,
            onRetry = {},
            onOpenSettings = {},
            onBack = {},
        )
    }
}
