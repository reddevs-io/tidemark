// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lets the rows of a lazy list read as one rounded, outlined card while staying separate lazy
 * items. Each row draws its slice of the card: the first row the rounded top, the last row the
 * rounded bottom, rows in between only the sides. Put dividers between rows yourself.
 */
fun Modifier.cardSegment(
    first: Boolean,
    last: Boolean,
    container: Color,
    border: Color,
    radius: Dp = 16.dp,
): Modifier = drawBehind {
    val r = radius.toPx()
    val stroke = 1.dp.toPx()
    val half = stroke / 2
    // Extend the shape past the open edges, then clip to this row, so only the closed edges show.
    val top = if (first) 0f else -(r + stroke)
    val bottom = if (last) size.height else size.height + r + stroke
    val fill = Path().apply { addRoundRect(RoundRect(0f, top, size.width, bottom, CornerRadius(r))) }
    val outline = Path().apply {
        addRoundRect(RoundRect(half, top + half, size.width - half, bottom - half, CornerRadius(r - half)))
    }
    clipRect(0f, 0f, size.width, size.height) {
        drawPath(fill, container)
        drawPath(outline, border, style = Stroke(stroke))
    }
}

/** The clip shape matching a [cardSegment] row, so ripples stay inside the rounded corners. */
fun cardSegmentShape(first: Boolean, last: Boolean, radius: Dp = 16.dp): Shape = RoundedCornerShape(
    topStart = if (first) radius else 0.dp,
    topEnd = if (first) radius else 0.dp,
    bottomStart = if (last) radius else 0.dp,
    bottomEnd = if (last) radius else 0.dp,
)
