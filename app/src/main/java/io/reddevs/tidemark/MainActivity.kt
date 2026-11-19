// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.reddevs.tidemark.navigation.TidemarkNavHost
import io.reddevs.tidemark.onboarding.OnboardingGate
import io.reddevs.tidemark.ui.components.LoadingState
import io.reddevs.tidemark.ui.theme.TidemarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = appContainer
        setContent {
            TidemarkTheme {
                var start by remember { mutableStateOf<Any?>(null) }
                LaunchedEffect(Unit) { start = OnboardingGate.startDestination(container) }
                val destination = start
                if (destination == null) {
                    LoadingState()
                } else {
                    TidemarkNavHost(container = container, startDestination = destination)
                }
            }
        }
    }
}
