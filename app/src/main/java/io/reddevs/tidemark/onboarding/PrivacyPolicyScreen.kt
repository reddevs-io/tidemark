// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding

import androidx.compose.runtime.Composable
import io.reddevs.tidemark.ui.components.PlaceholderScreen

/**
 * Renders the bundled privacy policy (assets/privacy/PRIVACY.md or PRIVACY.fr.md, copied from the
 * repo root at build time). T-003 owns this file.
 */
@Composable
fun PrivacyPolicyScreen(onBack: (() -> Unit)?) {
    PlaceholderScreen(title = "Privacy policy", onBack = onBack)
}
