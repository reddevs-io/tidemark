// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import androidx.compose.runtime.Composable
import io.reddevs.tidemark.AppContainer
import io.reddevs.tidemark.navigation.RecordDetail
import io.reddevs.tidemark.ui.components.PlaceholderScreen

/** Generic field dump of one record and its metadata. T-007 owns this file. */
@Composable
fun RecordDetailRoute(container: AppContainer, args: RecordDetail, onBack: () -> Unit) {
    PlaceholderScreen(title = args.type.name, onBack = onBack)
}
