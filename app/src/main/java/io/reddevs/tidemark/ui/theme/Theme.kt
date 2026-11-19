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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Static fallback palette for Android 9 to 11, which have no dynamic color: deep sea teal and sand.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6F78),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA9EEF5),
    onPrimaryContainer = Color(0xFF002023),
    secondary = Color(0xFF4A6366),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8EB),
    onSecondaryContainer = Color(0xFF051F22),
    tertiary = Color(0xFF7A5A2E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF291800),
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F484A),
    outline = Color(0xFF6F797A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DD2D9),
    onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF004F56),
    onPrimaryContainer = Color(0xFFA9EEF5),
    secondary = Color(0xFFB1CBCF),
    onSecondary = Color(0xFF1C3437),
    secondaryContainer = Color(0xFF324B4E),
    onSecondaryContainer = Color(0xFFCCE8EB),
    tertiary = Color(0xFFEDC08E),
    onTertiary = Color(0xFF432C05),
    tertiaryContainer = Color(0xFF5E4218),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF111415),
    onBackground = Color(0xFFE1E3E3),
    surface = Color(0xFF111415),
    onSurface = Color(0xFFE1E3E3),
    surfaceVariant = Color(0xFF3F484A),
    onSurfaceVariant = Color(0xFFBEC8C9),
    outline = Color(0xFF899294),
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
    MaterialTheme(colorScheme = colors, content = content)
}
