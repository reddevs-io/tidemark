// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Top app bar height that grows with the font scale. The default bar stays 64 dp tall, so at
 * large text sizes its title overflows into the status bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun scaledTopAppBarHeight(): Dp =
    TopAppBarDefaults.TopAppBarExpandedHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)
