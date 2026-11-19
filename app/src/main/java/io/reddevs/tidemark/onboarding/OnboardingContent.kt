// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.permission.HealthPermission
import io.reddevs.tidemark.R
import io.reddevs.tidemark.data.model.HcAvailability
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.ui.components.LoadingState
import io.reddevs.tidemark.ui.theme.TidemarkTheme

/** Stateless onboarding screen for one [OnboardingState]. */
@Composable
fun OnboardingContent(
    state: OnboardingState,
    onGetStarted: () -> Unit,
    onInstallOrUpdate: () -> Unit,
    onRequestPermissions: (Set<String>) -> Unit,
    onOpenHealthConnectSettings: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    Scaffold { padding ->
        val modifier = Modifier.padding(padding)
        when (state) {
            OnboardingState.Welcome -> Step(
                modifier = modifier,
                icon = Icons.Outlined.Waves,
                title = stringResource(R.string.onboarding_welcome_title),
                body = {
                    BodyText(stringResource(R.string.onboarding_welcome_body))
                    BodyText(stringResource(R.string.onboarding_welcome_privacy))
                },
                actions = {
                    PrimaryButton(R.string.onboarding_action_get_started, onGetStarted)
                    TextButton(onClick = onOpenPrivacyPolicy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.onboarding_action_privacy_policy))
                    }
                },
            )

            OnboardingState.Checking, OnboardingState.Done -> LoadingState(modifier)

            is OnboardingState.HealthConnectMissing -> HealthConnectMissingStep(state.availability, modifier, onInstallOrUpdate)

            is OnboardingState.Permissions -> PermissionsStep(
                state = state,
                modifier = modifier,
                onRequestPermissions = onRequestPermissions,
                onOpenHealthConnectSettings = onOpenHealthConnectSettings,
                onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            )
        }
    }
}

@Composable
private fun HealthConnectMissingStep(availability: HcAvailability, modifier: Modifier, onInstallOrUpdate: () -> Unit) {
    when (availability) {
        HcAvailability.UPDATE_REQUIRED -> Step(
            modifier = modifier,
            icon = Icons.Outlined.SystemUpdate,
            title = stringResource(R.string.onboarding_update_title),
            body = { BodyText(stringResource(R.string.onboarding_update_body)) },
            actions = { PrimaryButton(R.string.onboarding_action_update, onInstallOrUpdate) },
        )

        HcAvailability.NOT_INSTALLED -> Step(
            modifier = modifier,
            icon = Icons.Outlined.Download,
            title = stringResource(R.string.onboarding_not_installed_title),
            body = { BodyText(stringResource(R.string.onboarding_not_installed_body)) },
            actions = { PrimaryButton(R.string.onboarding_action_install, onInstallOrUpdate) },
        )

        // Unsupported devices get the explanation and nothing else.
        HcAvailability.UNSUPPORTED, HcAvailability.AVAILABLE -> Step(
            modifier = modifier,
            icon = Icons.Outlined.ErrorOutline,
            title = stringResource(R.string.onboarding_unsupported_title),
            body = { BodyText(stringResource(R.string.onboarding_unsupported_body)) },
            actions = {},
        )
    }
}

@Composable
private fun PermissionsStep(
    state: OnboardingState.Permissions,
    modifier: Modifier,
    onRequestPermissions: (Set<String>) -> Unit,
    onOpenHealthConnectSettings: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    if (state.denied) {
        Step(
            modifier = modifier,
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.onboarding_denied_title),
            body = { BodyText(stringResource(R.string.onboarding_denied_body)) },
            actions = {
                PrimaryButton(R.string.action_retry) { onRequestPermissions(state.toRequest) }
                OutlinedButton(onClick = onOpenHealthConnectSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_open_health_connect_settings))
                }
            },
        )
        return
    }
    Step(
        modifier = modifier,
        icon = Icons.Outlined.Shield,
        title = stringResource(R.string.onboarding_permissions_title),
        body = {
            BodyText(stringResource(R.string.onboarding_permissions_body))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordType.entries.forEach { type ->
                    PermissionRow(Icons.Outlined.Check, stringResource(labelFor(type)))
                }
                if (state.includesHistory) {
                    PermissionRow(Icons.Outlined.History, stringResource(R.string.onboarding_permission_history))
                }
            }
            BodyText(stringResource(R.string.onboarding_permissions_read_only))
        },
        actions = {
            PrimaryButton(R.string.onboarding_action_continue) { onRequestPermissions(state.toRequest) }
            TextButton(onClick = onOpenPrivacyPolicy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_action_privacy_policy))
            }
        },
    )
}

@StringRes
private fun labelFor(type: RecordType): Int = when (type) {
    RecordType.STEPS -> R.string.onboarding_permission_steps
    RecordType.DISTANCE -> R.string.onboarding_permission_distance
    RecordType.ACTIVE_CALORIES -> R.string.onboarding_permission_active_calories
    RecordType.EXERCISE_SESSION -> R.string.onboarding_permission_exercise
    RecordType.SLEEP_SESSION -> R.string.onboarding_permission_sleep
    RecordType.HEART_RATE -> R.string.onboarding_permission_heart_rate
    RecordType.RESTING_HEART_RATE -> R.string.onboarding_permission_resting_heart_rate
    RecordType.WEIGHT -> R.string.onboarding_permission_weight
}

@Composable
private fun Step(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    body: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        body()
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

@Composable
private fun BodyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun PermissionRow(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PrimaryButton(@StringRes label: Int, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) }
}

@Preview(showBackground = true)
@Composable
private fun WelcomePreview() {
    TidemarkTheme { OnboardingContent(OnboardingState.Welcome, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true, locale = "fr")
@Composable
private fun PermissionsPreview() {
    val state = OnboardingState.Permissions(
        toRequest = RecordType.readPermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
        includesHistory = true,
    )
    TidemarkTheme { OnboardingContent(state, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun DeniedPreview() {
    val state = OnboardingState.Permissions(RecordType.readPermissions, includesHistory = false, denied = true)
    TidemarkTheme { OnboardingContent(state, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun NotInstalledPreview() {
    TidemarkTheme { OnboardingContent(OnboardingState.HealthConnectMissing(HcAvailability.NOT_INSTALLED), {}, {}, {}, {}, {}) }
}
