// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.reddevs.tidemark.data.model.SleepStage

/**
 * Fixed sleep stage colours, the same with or without dynamic colour, so a night always reads the
 * same: deep sea for deep sleep, shallow water for light, sand for REM, a warm coral for awake.
 */
@Immutable
data class SleepColors(
    val deep: Color,
    val light: Color,
    val rem: Color,
    val awake: Color,
    /** Stages Health Connect reports without detail ("sleeping", "unknown"). */
    val neutral: Color,
) {
    /** The colour for [stage]. Stages outside the four main ones borrow the nearest main colour. */
    fun of(stage: SleepStage): Color = when (stage) {
        SleepStage.DEEP -> deep
        SleepStage.LIGHT -> light
        SleepStage.SLEEPING -> lerp(light, deep, 0.5f)
        SleepStage.REM -> rem
        SleepStage.AWAKE -> awake
        SleepStage.AWAKE_IN_BED -> awake.copy(alpha = 0.7f)
        SleepStage.OUT_OF_BED -> awake.copy(alpha = 0.45f)
        SleepStage.UNKNOWN -> neutral
    }
}

internal val LightSleepColors = SleepColors(
    deep = Color(0xFF123F45),
    light = Color(0xFF6FA3A8),
    rem = Color(0xFFC99A4E),
    awake = Color(0xFFC8553D),
    neutral = Color(0xFFA9B3B2),
)

internal val DarkSleepColors = SleepColors(
    deep = Color(0xFF2E7A82),
    light = Color(0xFF9FD6DA),
    rem = Color(0xFFD6B47C),
    awake = Color(0xFFE9826A),
    neutral = Color(0xFF55696A),
)

val LocalSleepColors = staticCompositionLocalOf { LightSleepColors }

/** Shorthand for the current [SleepColors], next to MaterialTheme.colorScheme. */
val sleepColors: SleepColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSleepColors.current
