package com.swan1127.repland.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CalmAccentDark,
    onPrimary = Color(0xFF173B44),
    primaryContainer = Color(0xFF263F46),
    onPrimaryContainer = Color(0xFFCBE7ED),
    secondary = Color(0xFFD4C4F0),
    secondaryContainer = Color(0xFF383149),
    onSecondaryContainer = Color(0xFFEFE7FA),
    tertiary = Color(0xFFDCC0A0),
    tertiaryContainer = Color(0xFF483929),
    onTertiaryContainer = Color(0xFFF3E8D8),
    background = Palette.NightPage,
    onBackground = Palette.NightInk,
    surface = Palette.NightPage,
    surfaceContainerLowest = Palette.NightPage,
    surfaceContainerLow = Palette.NightPaper,
    surfaceContainer = Palette.NightRaised,
    surfaceContainerHigh = Palette.NightHigh,
    surfaceContainerHighest = Palette.NightHighest,
    outline = Palette.NightInkFaint,
    outlineVariant = Palette.NightHairline,
    onSurface = Palette.NightInk,
    onSurfaceVariant = Palette.NightInkSoft,
    error = Palette.NightError,
    onError = Color(0xFF601410),
    errorContainer = Palette.NightErrorContainer,
    onErrorContainer = Color(0xFFF9DEDC),
)

private val LightColorScheme = lightColorScheme(
    primary = CalmAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F3F5),
    onPrimaryContainer = Color(0xFF234B55),
    secondary = Color(0xFF666079),
    secondaryContainer = Color(0xFFF1EEF8),
    onSecondaryContainer = Color(0xFF494158),
    tertiary = Color(0xFF80613E),
    tertiaryContainer = Color(0xFFFFF5DC),
    onTertiaryContainer = Color(0xFF574225),
    background = Palette.Page,
    onBackground = Palette.Ink,
    surface = Palette.Page,
    surfaceContainerLowest = Palette.Paper,
    surfaceContainerLow = Palette.Paper,
    surfaceContainer = Palette.PageDeep,
    surfaceContainerHigh = Palette.PageDeepest,
    surfaceContainerHighest = Palette.PageHighest,
    outline = Palette.InkFaint,
    outlineVariant = Palette.Hairline,
    onSurface = Palette.Ink,
    onSurfaceVariant = Palette.InkSoft,
    error = Palette.Error,
    onError = Color.White,
    errorContainer = Palette.ErrorLight,
    onErrorContainer = Color(0xFF5C1610),
)

fun replandColorScheme(darkTheme: Boolean): ColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

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

        else -> replandColorScheme(darkTheme)
    }

    ReplandMotionProvider {
        CompositionLocalProvider(LocalReplandVisuals provides if (darkTheme) DarkVisuals else LightVisuals) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = Typography,
                shapes = ReplandShapes,
                content = content
            )
        }
    }
}
