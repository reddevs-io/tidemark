// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import io.reddevs.tidemark.ui.components.scaledTopAppBarHeight
import io.reddevs.tidemark.data.HealthConnectIntents
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.R
import io.reddevs.tidemark.data.model.DataSource
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.navigation.Explorer
import io.reddevs.tidemark.ui.components.ErrorState
import io.reddevs.tidemark.ui.components.LoadingState
import io.reddevs.tidemark.ui.theme.TidemarkTheme

/**
 * The record explorer. Without a type in [args] it starts on the type list and keeps the chosen
 * type in saved state, so system back returns to the list. With a type (a chart drill-down) it
 * opens that type's records directly, preselecting the day and source when given.
 */
@Composable
fun ExplorerRoute(
    container: AppContainer,
    args: Explorer,
    onOpenRecord: (RecordType, String) -> Unit,
    onBack: () -> Unit,
) {
    var selectedName by rememberSaveable { mutableStateOf(args.typeName) }
    val selected = selectedName?.let { name -> RecordType.entries.firstOrNull { it.name == name } }

    if (selected == null) {
        val viewModel: ExplorerTypesViewModel = viewModel(
            factory = viewModelFactory { initializer { ExplorerTypesViewModel(container.healthRepository) } },
        )
        TypeListRoute(viewModel, onSelect = { selectedName = it.name }, onBack = onBack)
    } else {
        val fromArgs = args.type == selected
        val backToTypes = args.type == null
        BackHandler(enabled = backToTypes) { selectedName = null }
        val viewModel: RecordListViewModel = viewModel(
            key = "explorer-records-${selected.name}",
            factory = viewModelFactory {
                initializer {
                    RecordListViewModel(
                        repository = container.healthRepository,
                        clock = container.clock,
                        type = selected,
                        initialRange = RecordListViewModel.initialRange(if (fromArgs) args.day else null),
                        initialSource = if (fromArgs) args.sourcePackage?.let(::DataSource) else null,
                    )
                }
            },
        )
        RecordListRoute(
            container = container,
            viewModel = viewModel,
            onOpenRecord = onOpenRecord,
            onBack = if (backToTypes) ({ selectedName = null }) else onBack,
        )
    }
}

@Composable
private fun TypeListRoute(viewModel: ExplorerTypesViewModel, onSelect: (RecordType) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (!viewModel.state.value.loading) viewModel.refresh() }
    TypeListScreen(
        state = state,
        onSelect = onSelect,
        onOpenSettings = { HealthConnectIntents.openAccess(context) },
        onRetry = viewModel::refresh,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TypeListScreen(
    state: TypeListState,
    onSelect: (RecordType) -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = scaledTopAppBarHeight(),
                title = { Text(stringResource(R.string.explorer_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when {
            state.loading -> LoadingState(modifier)
            state.error != null && state.types.isEmpty() ->
                ErrorState(modifier, message = stringResource(state.error.message), onRetry = onRetry)
            else -> LazyColumn(modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    Text(
                        stringResource(R.string.explorer_types_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(state.types, key = { it.type.name }) { access ->
                    TypeRow(access, onClick = { if (access.readable) onSelect(access.type) else onOpenSettings() })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TypeRow(access: TypeAccess, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(stringResource(access.type.label)) },
        supportingContent = {
            Text(stringResource(if (access.readable) R.string.explorer_type_readable else R.string.explorer_type_locked))
        },
        leadingContent = if (access.readable) {
            null
        } else {
            { Icon(Icons.Filled.Lock, contentDescription = null) }
        },
        trailingContent = {
            Icon(
                if (access.readable) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
            )
        },
    )
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}

@Preview(name = "Type list")
@Preview(name = "Type list, French", locale = "fr")
@Composable
private fun TypeListPreview() {
    TidemarkTheme(dynamicColor = false) {
        TypeListScreen(
            state = TypeListState(
                loading = false,
                types = RecordType.entries.map { TypeAccess(it, readable = it != RecordType.WEIGHT) },
            ),
            onSelect = {},
            onOpenSettings = {},
            onRetry = {},
            onBack = {},
        )
    }
}
