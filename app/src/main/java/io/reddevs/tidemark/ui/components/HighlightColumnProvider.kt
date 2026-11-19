// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.ui.components

import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

/**
 * Draws every column with [column] except the one at [highlightX] (today, or the period still in
 * progress), which uses [highlight]: the same bar in a lighter tint.
 */
internal class HighlightColumnProvider(
    private val column: LineComponent,
    private val highlight: LineComponent,
    private val highlightX: Double?,
) : ColumnCartesianLayer.ColumnProvider {
    override fun getColumn(entry: ColumnCartesianLayerModel.Entry, extraStore: ExtraStore): LineComponent =
        if (highlightX != null && entry.x == highlightX) highlight else column

    override fun getWidestSeriesColumn(seriesKey: Any, seriesIndex: Int, extraStore: ExtraStore): LineComponent = column

    override fun equals(other: Any?): Boolean =
        other is HighlightColumnProvider &&
            other.column == column &&
            other.highlight == highlight &&
            other.highlightX == highlightX

    override fun hashCode(): Int = (column.hashCode() * 31 + highlight.hashCode()) * 31 + highlightX.hashCode()
}

/** The tint of the in-progress bar, as a fraction of the normal bar colour's opacity. */
internal const val IN_PROGRESS_ALPHA = 0.42f
