// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.reddevs.tidemark.R
import io.reddevs.tidemark.data.model.SleepStage
import io.reddevs.tidemark.format.MeasurementFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A stage colour for the hypnogram and its legend. Wake states are warm, sleep runs light to deep. */
internal fun stageColor(stage: SleepStage, colors: ColorScheme): Color = when (stage) {
    SleepStage.OUT_OF_BED -> colors.outline
    SleepStage.AWAKE -> colors.tertiary
    SleepStage.AWAKE_IN_BED -> colors.tertiary.copy(alpha = 0.6f)
    SleepStage.REM -> colors.secondary
    SleepStage.LIGHT -> colors.primary.copy(alpha = 0.55f)
    SleepStage.SLEEPING -> colors.primary.copy(alpha = 0.75f)
    SleepStage.DEEP -> colors.primary
    SleepStage.UNKNOWN -> colors.outlineVariant
}

/**
 * Stepped sleep-stage chart drawn on a Compose Canvas: one row per stage present, a bar per stage
 * span, thin connectors where one stage hands over to the next, and nothing in the gaps no stage
 * covers. The whole chart is one accessibility node carrying [description].
 */
@Composable
internal fun Hypnogram(
    model: HypnogramModel,
    formatter: MeasurementFormatter,
    zone: ZoneId,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val rowNames = model.rows.map { stringResource(stageName(it)) }
    val ticks = remember(model, zone) { hourTicks(model.start, model.end, zone) }
    val tickLabels = ticks.map { formatter.time(it) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Rows and the time axis grow with the font scale, so labels never clip at 200%.
    val textHeight = with(LocalDensity.current) { labelStyle.lineHeight.toDp() }
    val rowHeight = maxOf(28.dp, textHeight + 10.dp)
    val axisHeight = textHeight + 10.dp

    Canvas(
        modifier
            .fillMaxWidth()
            .height(rowHeight * model.rows.size.coerceAtLeast(1) + axisHeight)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val rowPx = rowHeight.toPx()
        val gutter = rowNames.maxOfOrNull { measurer.measure(it, labelStyle).size.width }?.toFloat()?.plus(8.dp.toPx()) ?: 0f
        val plotStart = if (rtl) 0f else gutter
        val plotWidth = (size.width - gutter).coerceAtLeast(1f)
        val plotHeight = rowPx * model.rows.size
        fun x(fraction: Float) = if (rtl) plotStart + plotWidth * (1 - fraction) else plotStart + plotWidth * fraction

        // Row labels and faint row guides.
        rowNames.forEachIndexed { row, name ->
            val layout = measurer.measure(name, labelStyle)
            val labelX = if (rtl) size.width - layout.size.width else 0f
            drawText(layout, topLeft = Offset(labelX, row * rowPx + (rowPx - layout.size.height) / 2))
            drawLine(
                color = colors.outlineVariant.copy(alpha = 0.5f),
                start = Offset(plotStart, row * rowPx + rowPx / 2),
                end = Offset(plotStart + plotWidth, row * rowPx + rowPx / 2),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
            )
        }

        // Connectors between consecutive stages that touch.
        model.segments.zipWithNext().forEach { (a, b) ->
            if (a.end == b.start && a.row != b.row) {
                val cx = x(b.span.startFraction)
                drawLine(
                    color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                    start = Offset(cx, a.row * rowPx + rowPx / 2),
                    end = Offset(cx, b.row * rowPx + rowPx / 2),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        // Stage bars.
        val barHeight = rowPx * 0.6f
        model.segments.forEach { segment ->
            val left = minOf(x(segment.span.startFraction), x(segment.span.endFraction))
            val right = maxOf(x(segment.span.startFraction), x(segment.span.endFraction))
            drawRoundRect(
                color = stageColor(segment.stage, colors),
                topLeft = Offset(left, segment.row * rowPx + (rowPx - barHeight) / 2),
                size = Size((right - left).coerceAtLeast(1.dp.toPx()), barHeight),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }

        drawTimeAxis(model, ticks, tickLabels, measurer, labelStyle, plotHeight, ::x, colors.outlineVariant)
    }
}

private fun DrawScope.drawTimeAxis(
    model: HypnogramModel,
    ticks: List<Instant>,
    labels: List<String>,
    measurer: TextMeasurer,
    style: TextStyle,
    top: Float,
    x: (Float) -> Float,
    lineColor: Color,
) {
    val total = (model.end.toEpochMilli() - model.start.toEpochMilli()).coerceAtLeast(1).toFloat()
    drawLine(lineColor, Offset(x(0f), top), Offset(x(1f), top), strokeWidth = 1.dp.toPx())
    val drawn = mutableListOf<ClosedFloatingPointRange<Float>>()
    val gap = 8.dp.toPx()
    // Start and end first, then the hours in between where they fit without overlapping.
    val order = if (ticks.size < 2) ticks.indices.toList() else listOf(0, ticks.lastIndex) + (1 until ticks.lastIndex)
    order.forEach { i ->
        val fraction = (ticks[i].toEpochMilli() - model.start.toEpochMilli()) / total
        val layout = measurer.measure(labels[i], style)
        val center = x(fraction)
        val left = (center - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
        val right = left + layout.size.width
        if (drawn.none { left < it.endInclusive + gap && right > it.start - gap }) {
            drawLine(lineColor, Offset(center, top), Offset(center, top + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
            drawText(layout, topLeft = Offset(left, top + 6.dp.toPx()))
            drawn += left..right
        }
    }
}

/** Session start, every whole hour in between, and session end. */
internal fun hourTicks(start: Instant, end: Instant, zone: ZoneId): List<Instant> = buildList {
    add(start)
    var hour = start.atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant()
    val inner = mutableListOf<Instant>()
    while (hour.isBefore(end)) {
        inner += hour
        hour = hour.plus(1, ChronoUnit.HOURS)
    }
    // Keep the axis readable on long sessions: at most about six inner ticks.
    val step = (inner.size / 6 + 1).coerceAtLeast(1)
    inner.filterIndexed { i, _ -> i % step == 0 }.forEach(::add)
    if (end.isAfter(start)) add(end)
}
