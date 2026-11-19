// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.R

/** First run: welcome, Health Connect status, permission request. */
@Composable
fun OnboardingRoute(container: AppContainer, onDone: () -> Unit) {
    val viewModel: OnboardingViewModel = viewModel {
        OnboardingViewModel(container.healthRepository, container.userPreferences, container.clock)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnDone by rememberUpdatedState(onDone)

    val contract = remember { PermissionController.createRequestPermissionResultContract() }
    val permissionLauncher = rememberLauncherForActivityResult(contract) { granted ->
        viewModel.onPermissionResult(granted)
    }

    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    LaunchedEffect(state) {
        if (state == OnboardingState.Done) currentOnDone()
    }

    OnboardingContent(
        state = state,
        onGetStarted = viewModel::onGetStarted,
        onInstallOrUpdate = { openOrToast(context, HealthConnectIntents.installOrUpdate(context)) },
        onRequestPermissions = { permissions ->
            try {
                permissionLauncher.launch(permissions)
            } catch (_: ActivityNotFoundException) {
                viewModel.onPermissionRequestFailed()
            }
        },
        onOpenHealthConnectSettings = { openOrToast(context, HealthConnectIntents.appPermissions(context)) },
        onOpenPrivacyPolicy = { context.startActivity(Intent(context, PrivacyPolicyActivity::class.java)) },
    )
}

private fun openOrToast(context: Context, candidates: List<Intent>) {
    if (!HealthConnectIntents.startFirst(context, candidates)) {
        Toast.makeText(context, R.string.onboarding_open_failed, Toast.LENGTH_LONG).show()
    }
}
