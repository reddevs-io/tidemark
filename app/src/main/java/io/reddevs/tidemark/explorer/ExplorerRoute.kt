// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import androidx.compose.runtime.Composable
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.data.model.RecordType
import io.reddevs.tidemark.navigation.Explorer
import io.reddevs.tidemark.ui.components.PlaceholderScreen

/** Type list and paged record list with filters. T-007 owns this file. */
@Composable
fun ExplorerRoute(
    container: AppContainer,
    args: Explorer,
    onOpenRecord: (RecordType, String) -> Unit,
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = "Explorer", onBack = onBack)
}
