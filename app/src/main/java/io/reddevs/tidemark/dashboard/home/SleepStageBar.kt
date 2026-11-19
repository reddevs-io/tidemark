// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.dashboard.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.reddevs.tidemark.data.model.SleepNight
import io.reddevs.tidemark.data.model.SleepStage
import java.time.Duration

/**
 * A thin horizontal bar of last night's stages, start to end, each span proportional to its
 * length. Gaps between stages show the track colour. A night without stages draws one
 * "sleeping" span. Decorative: the tile reads the duration aloud.
 */
@Composable
internal fun SleepStageBar(night: SleepNight, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val track = scheme.surfaceVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val total = Duration.between(night.start, night.end).toMillis().toFloat()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        drawRect(track)
        if (total <= 0f) return@Canvas
        val spans = night.stages.ifEmpty { null }
        if (spans == null) {
            drawRect(stageColor(SleepStage.SLEEPING, scheme))
            return@Canvas
        }
        spans.forEach { span ->
            val from = Duration.between(night.start, span.start).toMillis().coerceIn(0, total.toLong()) / total
            val to = Duration.between(night.start, span.end).toMillis().coerceIn(0, total.toLong()) / total
            if (to <= from) return@forEach
            val left = if (rtl) size.width * (1 - to) else size.width * from
            drawRect(
                color = stageColor(span.stage, scheme),
                topLeft = Offset(left, 0f),
                size = Size(size.width * (to - from), size.height),
            )
        }
    }
}

/** Deep is the strongest colour, awake states the weakest. */
internal fun stageColor(stage: SleepStage, scheme: ColorScheme): Color = when (stage) {
    SleepStage.DEEP -> scheme.primary
    SleepStage.LIGHT -> scheme.primary.copy(alpha = 0.55f)
    SleepStage.REM -> scheme.tertiary
    SleepStage.SLEEPING -> scheme.secondary
    SleepStage.AWAKE, SleepStage.AWAKE_IN_BED, SleepStage.OUT_OF_BED -> scheme.outline
    SleepStage.UNKNOWN -> scheme.outlineVariant
}
