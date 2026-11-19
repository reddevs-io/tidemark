// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// "Shoreline" static palette for Android 9 to 11, which have no dynamic color: sea teal on sand.
// Roles the design names are exact; the rest are derived to keep body text at 4.5:1 or more.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6670),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E6E6),
    onPrimaryContainer = Color(0xFF0B2F34),
    inversePrimary = Color(0xFF84C9CF),
    secondary = Color(0xFF4C6466),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1EBE8),
    onSecondaryContainer = Color(0xFF13302F),
    tertiary = Color(0xFF8A6A35),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF1E2C6),
    onTertiaryContainer = Color(0xFF2E2108),
    background = Color(0xFFF5F3EE),
    onBackground = Color(0xFF1C2B2D),
    surface = Color(0xFFF5F3EE),
    onSurface = Color(0xFF1C2B2D),
    surfaceVariant = Color(0xFFEFEBE3),
    onSurfaceVariant = Color(0xFF5A6768),
    surfaceTint = Color(0xFF1F6670),
    inverseSurface = Color(0xFF2B3A3C),
    inverseOnSurface = Color(0xFFEEF1EF),
    outline = Color(0xFF7A8586),
    outlineVariant = Color(0xFFE3DDD1),
    surfaceBright = Color(0xFFFCFBF8),
    surfaceDim = Color(0xFFE6E2D9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F7F3),
    surfaceContainer = Color(0xFFFCFBF8),
    surfaceContainerHigh = Color(0xFFEFEBE3),
    surfaceContainerHighest = Color(0xFFE9E4DA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF84C9CF),
    onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF1F3B3F),
    onPrimaryContainer = Color(0xFFC6EAEC),
    inversePrimary = Color(0xFF1F6670),
    secondary = Color(0xFFB0CBCB),
    onSecondary = Color(0xFF1B3436),
    secondaryContainer = Color(0xFF1F3335),
    onSecondaryContainer = Color(0xFFCFE5E4),
    tertiary = Color(0xFFD6B47C),
    onTertiary = Color(0xFF3E2C0E),
    tertiaryContainer = Color(0xFF4A3A20),
    onTertiaryContainer = Color(0xFFF1E2C6),
    background = Color(0xFF0E1A1C),
    onBackground = Color(0xFFE4ECEA),
    surface = Color(0xFF0E1A1C),
    onSurface = Color(0xFFE4ECEA),
    surfaceVariant = Color(0xFF1B2E31),
    onSurfaceVariant = Color(0xFF9AADAC),
    surfaceTint = Color(0xFF84C9CF),
    inverseSurface = Color(0xFFE4ECEA),
    inverseOnSurface = Color(0xFF233335),
    outline = Color(0xFF6B7F80),
    outlineVariant = Color(0xFF22383B),
    surfaceBright = Color(0xFF2A3E41),
    surfaceDim = Color(0xFF0E1A1C),
    surfaceContainerLowest = Color(0xFF091415),
    surfaceContainerLow = Color(0xFF112022),
    surfaceContainer = Color(0xFF142426),
    surfaceContainerHigh = Color(0xFF1B2E31),
    surfaceContainerHighest = Color(0xFF22363A),
)

@Composable
fun TidemarkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    // Sleep stage colours are fixed: they stay the same under dynamic colour.
    CompositionLocalProvider(LocalSleepColors provides if (darkTheme) DarkSleepColors else LightSleepColors) {
        MaterialTheme(colorScheme = colors, typography = TidemarkTypography, content = content)
    }
}
