package com.swan1127.repland.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.swan1127.repland.domain.model.ThemeAccent

private fun lightScheme(ramp: ThemeRamp) = lightColorScheme(
    primary = Color(ramp.primary),
    onPrimary = Color(ramp.onPrimary),
    primaryContainer = Color(ramp.primaryContainer),
    onPrimaryContainer = Color(ramp.onPrimaryContainer),
    inversePrimary = Color(ramp.inversePrimary),

    secondary = Color(ramp.secondary),
    onSecondary = Color(ramp.onSecondary),
    secondaryContainer = Color(ramp.secondaryContainer),
    onSecondaryContainer = Color(ramp.onSecondaryContainer),

    tertiary = Color(ramp.tertiary),
    onTertiary = Color(ramp.onTertiary),
    tertiaryContainer = Color(ramp.tertiaryContainer),
    onTertiaryContainer = Color(ramp.onTertiaryContainer),

    background = Palette.Page,
    onBackground = Palette.Ink,
    surface = Palette.Page,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.PageDeep,
    onSurfaceVariant = Palette.InkSoft,

    surfaceContainerLowest = Palette.Paper,
    surfaceContainerLow = Palette.Paper,
    surfaceContainer = Palette.PageDeep,
    surfaceContainerHigh = Palette.PageDeepest,
    surfaceContainerHighest = Palette.PageHighest,

    outline = Palette.InkFaint,
    outlineVariant = Palette.Hairline,

    error = Palette.Error,
    onError = Color.White,
    errorContainer = Palette.ErrorLight,
    onErrorContainer = Color(0xFF5C1610),
)

private fun darkScheme(ramp: ThemeRamp) = darkColorScheme(
    primary = Color(ramp.primary),
    onPrimary = Color(ramp.onPrimary),
    primaryContainer = Color(ramp.primaryContainer),
    onPrimaryContainer = Color(ramp.onPrimaryContainer),
    inversePrimary = Color(ramp.inversePrimary),

    secondary = Color(ramp.secondary),
    onSecondary = Color(ramp.onSecondary),
    secondaryContainer = Color(ramp.secondaryContainer),
    onSecondaryContainer = Color(ramp.onSecondaryContainer),

    tertiary = Color(ramp.tertiary),
    onTertiary = Color(ramp.onTertiary),
    tertiaryContainer = Color(ramp.tertiaryContainer),
    onTertiaryContainer = Color(ramp.onTertiaryContainer),

    background = Palette.NightPage,
    onBackground = Palette.NightInk,
    surface = Palette.NightPage,
    onSurface = Palette.NightInk,
    surfaceVariant = Palette.NightHigh,
    onSurfaceVariant = Palette.NightInkSoft,

    surfaceContainerLowest = Palette.NightPage,
    surfaceContainerLow = Palette.NightPaper,
    surfaceContainer = Palette.NightRaised,
    surfaceContainerHigh = Palette.NightHigh,
    surfaceContainerHighest = Palette.NightHighest,

    outline = Palette.NightInkFaint,
    outlineVariant = Palette.NightHairline,

    error = Palette.NightError,
    onError = Color(0xFF601410),
    errorContainer = Palette.NightErrorContainer,
    onErrorContainer = Color(0xFFF9DEDC),
)

/**
 * The scheme for a given accent and mode, as a plain value, so the mapping is testable without a
 * Compose host.
 */
fun replandColorScheme(accent: ThemeAccent, darkTheme: Boolean): ColorScheme {
    val palette = paletteFor(accent)
    return if (darkTheme) darkScheme(palette.dark) else lightScheme(palette.light)
}

@Composable
fun ReplandTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: ThemeAccent = ThemeAccent.DEFAULT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> replandColorScheme(accent, darkTheme)
    }

    ReplandMotionProvider {
        CompositionLocalProvider(
            LocalReplandVisuals provides if (darkTheme) DarkVisuals else LightVisuals,
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = ReplandTypography,
                shapes = ReplandShapes,
                content = content,
            )
        }
    }
}
