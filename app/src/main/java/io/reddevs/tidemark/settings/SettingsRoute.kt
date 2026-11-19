// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.settings

import androidx.compose.runtime.Composable
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.ui.components.PlaceholderScreen

/** Settings: unit override, Health Connect access, privacy policy, about. T-004 owns this file. */
@Composable
fun SettingsRoute(
    container: AppContainer,
    onOpenPrivacy: () -> Unit,
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = "Settings", onBack = onBack)
}
