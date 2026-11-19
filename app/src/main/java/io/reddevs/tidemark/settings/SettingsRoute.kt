// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.settings

import io.reddevs.tidemark.data.HealthConnectIntents
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.BuildConfig
import io.reddevs.tidemark.R
import io.reddevs.tidemark.format.UnitSystem
import io.reddevs.tidemark.format.resolveUnitSystem
import io.reddevs.tidemark.ui.theme.TidemarkTheme
import kotlinx.coroutines.launch

/** Settings: unit override, Health Connect access, privacy policy, about. */
@Composable
fun SettingsRoute(
    container: AppContainer,
    onOpenPrivacy: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = viewModel { SettingsViewModel(container.userPreferences) }
    val unitPreference by viewModel.unitPreference.collectAsStateWithLifecycle()
    val formatter by container.formatter.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unavailable = stringResource(R.string.error_unavailable)
    val linkFailed = stringResource(R.string.settings_open_link_failed)

    SettingsScreen(
        unitPreference = unitPreference,
        systemUnits = resolveUnitSystem(formatter.locale, UnitPreference.SYSTEM),
        versionName = BuildConfig.VERSION_NAME,
        snackbarHostState = snackbar,
        onSelectUnit = viewModel::selectUnit,
        onManageHealthConnect = {
            if (!HealthConnectIntents.openAccess(context)) scope.launch { snackbar.showSnackbar(unavailable) }
        },
        onOpenPrivacy = onOpenPrivacy,
        onOpenSourceCode = {
            if (!context.startActivityOrFalse(SettingsLinks.sourceCode())) scope.launch { snackbar.showSnackbar(linkFailed) }
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    unitPreference: UnitPreference?,
    systemUnits: UnitSystem,
    versionName: String,
    onSelectUnit: (UnitPreference) -> Unit,
    onManageHealthConnect: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenSourceCode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item { SectionHeader(R.string.settings_section_units) }
            item { UnitChoices(unitPreference, systemUnits, onSelectUnit) }
            item { NoteText(R.string.settings_units_energy_note) }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

            item { SectionHeader(R.string.settings_section_health_connect) }
            item {
                LinkRow(
                    icon = Icons.Outlined.HealthAndSafety,
                    headline = stringResource(R.string.settings_manage_access),
                    supporting = stringResource(R.string.settings_manage_access_summary),
                    external = true,
                    onClick = onManageHealthConnect,
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

            item { SectionHeader(R.string.settings_section_privacy) }
            item {
                LinkRow(
                    icon = Icons.Outlined.Policy,
                    headline = stringResource(R.string.settings_privacy_policy),
                    onClick = onOpenPrivacy,
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.WifiOff, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_no_network)) },
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

            item { SectionHeader(R.string.settings_section_about) }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_version)) },
                    supportingContent = { Text(versionName) },
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Gavel, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_license)) },
                    supportingContent = { Text(stringResource(R.string.settings_license_value)) },
                )
            }
            item {
                LinkRow(
                    icon = Icons.Outlined.Code,
                    headline = stringResource(R.string.settings_source_code),
                    supporting = stringResource(R.string.settings_source_code_value),
                    external = true,
                    onClick = onOpenSourceCode,
                )
            }
        }
    }
}

@Composable
private fun UnitChoices(
    selected: UnitPreference?,
    systemUnits: UnitSystem,
    onSelect: (UnitPreference) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        UnitPreference.entries.forEach { option ->
            val supporting = when (option) {
                UnitPreference.SYSTEM -> stringResource(R.string.settings_units_system_current, stringResource(systemUnits.summaryRes))
                UnitPreference.METRIC -> stringResource(UnitSystem.METRIC.summaryRes)
                UnitPreference.IMPERIAL -> stringResource(UnitSystem.US.summaryRes)
            }
            ListItem(
                modifier = Modifier.selectable(
                    selected = option == selected,
                    role = Role.RadioButton,
                    onClick = { onSelect(option) },
                ),
                leadingContent = { RadioButton(selected = option == selected, onClick = null) },
                headlineContent = { Text(stringResource(option.labelRes)) },
                supportingContent = { Text(supporting) },
            )
        }
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    headline: String,
    onClick: () -> Unit,
    supporting: String? = null,
    external: Boolean = false,
) {
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(headline) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = if (external) {
            { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) }
        } else {
            null
        },
    )
}

@Composable
private fun SectionHeader(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun NoteText(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

private val UnitPreference.labelRes: Int
    @StringRes get() = when (this) {
        UnitPreference.SYSTEM -> R.string.settings_units_system
        UnitPreference.METRIC -> R.string.settings_units_metric
        UnitPreference.IMPERIAL -> R.string.settings_units_imperial
    }

private val UnitSystem.summaryRes: Int
    @StringRes get() = when (this) {
        UnitSystem.METRIC -> R.string.settings_units_summary_metric
        UnitSystem.UK -> R.string.settings_units_summary_uk
        UnitSystem.US -> R.string.settings_units_summary_us
    }

@Composable
private fun SettingsPreviewContent() {
    TidemarkTheme(dynamicColor = false) {
        SettingsScreen(
            unitPreference = UnitPreference.SYSTEM,
            systemUnits = UnitSystem.UK,
            versionName = "1.0.0",
            onSelectUnit = {},
            onManageHealthConnect = {},
            onOpenPrivacy = {},
            onOpenSourceCode = {},
            onBack = {},
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun SettingsLightPreview() = SettingsPreviewContent()

@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsDarkPreview() = SettingsPreviewContent()

@Preview(name = "French", showBackground = true, locale = "fr")
@Composable
private fun SettingsFrenchPreview() = SettingsPreviewContent()

@Preview(name = "Large font", showBackground = true, fontScale = 2f, widthDp = 360)
@Composable
private fun SettingsLargeFontPreview() = SettingsPreviewContent()
