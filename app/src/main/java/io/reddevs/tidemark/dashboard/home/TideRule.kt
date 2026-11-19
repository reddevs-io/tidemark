// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * The "tide rule" under the dashboard title: thin horizontal lines 4 dp apart, like the marks on
 * a tide gauge. The only ornament in the app, and invisible to accessibility services.
 */
@Composable
internal fun TideRule(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primaryContainer
    Canvas(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(9.dp)
            .clearAndSetSemantics {},
    ) {
        val stroke = 1.dp.toPx()
        val step = 4.dp.toPx()
        var y = stroke / 2
        while (y <= size.height) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
            y += step
        }
    }
}
