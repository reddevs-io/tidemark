// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.reddevs.tidemark.ui.theme.TidemarkTheme

/**
 * Health Connect opens this screen to show why Tidemark asks for access: through
 * ACTION_SHOW_PERMISSIONS_RATIONALE on Android 13 and below, and through the
 * ViewPermissionUsageActivity alias on Android 14 and above. T-003 owns this file.
 */
class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TidemarkTheme { PrivacyPolicyScreen(onBack = ::finish) }
        }
    }
}
