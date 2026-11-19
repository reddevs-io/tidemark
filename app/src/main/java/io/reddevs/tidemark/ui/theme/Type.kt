// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.reddevs.tidemark.R

/** One instance of the bundled Manrope variable font (SIL OFL 1.1) pinned to [weight]. */
private fun manrope(weight: FontWeight) = Font(
    R.font.manrope,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Manrope at the five weights the design uses. minSdk 28 renders variable fonts natively. */
val Manrope = FontFamily(
    manrope(FontWeight.Normal),
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold),
)

private fun style(size: Int, lineHeight: Int, weight: FontWeight, letterSpacing: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.em,
)

/**
 * The Shoreline type scale on Manrope. headlineMedium carries big values (28sp bold, slightly
 * tightened), titleLarge screen titles, bodyMedium running text and labelMedium small labels.
 */
val TidemarkTypography = Typography(
    displayLarge = style(57, 64, FontWeight.Bold, -0.02),
    displayMedium = style(45, 52, FontWeight.Bold, -0.02),
    displaySmall = style(36, 44, FontWeight.Bold, -0.02),
    headlineLarge = style(32, 40, FontWeight.Bold, -0.02),
    headlineMedium = style(28, 34, FontWeight.Bold, -0.02),
    headlineSmall = style(24, 30, FontWeight.Bold, -0.01),
    titleLarge = style(22, 28, FontWeight.Bold),
    titleMedium = style(16, 22, FontWeight.Bold),
    titleSmall = style(14, 20, FontWeight.Bold),
    bodyLarge = style(16, 24, FontWeight.Medium),
    bodyMedium = style(14, 20, FontWeight.Medium),
    bodySmall = style(12, 16, FontWeight.Medium),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.Bold),
    labelSmall = style(11, 16, FontWeight.Bold),
)
