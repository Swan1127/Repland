package com.swan1127.repland.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DeepMoss,
    onPrimary = NightPaper,
    primaryContainer = Color(0xFF24463C),
    onPrimaryContainer = Color(0xFFC5F2DB),
    secondary = Color(0xFFB5C9B8),
    secondaryContainer = Color(0xFF283A32),
    tertiary = Color(0xFFDCC0A0),
    tertiaryContainer = Color(0xFF483929),
    background = NightPaper,
    onBackground = Color(0xFFE4ECE4),
    surface = NightPaper,
    surfaceContainerLow = Color(0xFF1D2824),
    surfaceContainer = Color(0xFF222F29),
    surfaceContainerHigh = Color(0xFF2B3832),
    surfaceContainerHighest = Color(0xFF34413B),
    outline = Color(0xFF82958A),
    outlineVariant = Color(0xFF3B4B42),
    onSurface = Color(0xFFE4ECE4),
    onSurfaceVariant = Color(0xFFB1C1B7),
)

private val LightColorScheme = lightColorScheme(
    primary = Moss,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EDE1),
    onPrimaryContainer = Color(0xFF174739),
    secondary = Color(0xFF51675A),
    secondaryContainer = Color(0xFFE3ECE2),
    onSecondaryContainer = Color(0xFF293F33),
    tertiary = Color(0xFF80613E),
    tertiaryContainer = Color(0xFFF3E8D8),
    onTertiaryContainer = Color(0xFF574225),
    background = WarmPaper,
    onBackground = Color(0xFF192821),
    surface = WarmPaper,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F2ED),
    surfaceContainerHigh = Color(0xFFE9EDE6),
    surfaceContainerHighest = Color(0xFFE1E7DD),
    outline = Color(0xFF758278),
    outlineVariant = Color(0xFFD9E0D7),
    onSurface = Color(0xFF192821),
    onSurfaceVariant = Color(0xFF52665B),
)

@Composable
fun ReplandTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
