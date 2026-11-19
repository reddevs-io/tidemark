// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding

import androidx.compose.runtime.Composable
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.ui.components.PlaceholderScreen

/** First run: Health Connect status, permission request. T-003 owns this file. */
@Composable
fun OnboardingRoute(container: AppContainer, onDone: () -> Unit) {
    PlaceholderScreen(title = "Tidemark", body = "Onboarding")
}
