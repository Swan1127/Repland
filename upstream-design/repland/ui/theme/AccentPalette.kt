package com.swan1127.repland.ui.theme

import com.swan1127.repland.domain.model.ThemeAccent
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The colour tokens a [ThemeAccent] is allowed to own, in one mode.
 *
 * The default (BAMBOO) is the calm teal scheme shipped with the new design. The two alternatives
 * keep the same structure — a white-readable brand, a pale container, and a separate second
 * emphasis — so switching accents cannot change how a surface is layered, only which family the
 * interactive colour belongs to.
 */
data class ThemeRamp(
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val inversePrimary: Long,
    val secondary: Long,
    val onSecondary: Long,
    val secondaryContainer: Long,
    val onSecondaryContainer: Long,
    val tertiary: Long,
    val onTertiary: Long,
    val tertiaryContainer: Long,
    val onTertiaryContainer: Long,
)

/** One accent's two modes. Both are authored, not derived. */
data class ThemePalette(val light: ThemeRamp, val dark: ThemeRamp)

/**
 * The three ramps.
 *
 * In dark mode the brand is the luminous member of its family, not a darker one: a dark brand on a
 * near-black page would read as one flat field and the focal card would stop leading the screen.
 */
fun paletteFor(accent: ThemeAccent): ThemePalette = when (accent) {
    ThemeAccent.BAMBOO -> ThemePalette(
        light = ThemeRamp(
            primary = 0xFF326B75,
            onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFE8F3F5,
            onPrimaryContainer = 0xFF234B55,
            inversePrimary = 0xFF4F8F9A,
            secondary = 0xFF666079,
            onSecondary = 0xFFFFFFFF,
            secondaryContainer = 0xFFF1EEF8,
            onSecondaryContainer = 0xFF494158,
            tertiary = 0xFF80613E,
            onTertiary = 0xFFFFFFFF,
            tertiaryContainer = 0xFFFFF5DC,
            onTertiaryContainer = 0xFF574225,
        ),
        dark = ThemeRamp(
            primary = 0xFFA8D9DE,
            onPrimary = 0xFF173B44,
            primaryContainer = 0xFF263F46,
            onPrimaryContainer = 0xFFCBE7ED,
            inversePrimary = 0xFF326B75,
            secondary = 0xFFD4C4F0,
            onSecondary = 0xFF2E2838,
            secondaryContainer = 0xFF383149,
            onSecondaryContainer = 0xFFEFE7FA,
            tertiary = 0xFFDCC0A0,
            onTertiary = 0xFF3F2E1A,
            tertiaryContainer = 0xFF483929,
            onTertiaryContainer = 0xFFF3E8D8,
        ),
    )

    ThemeAccent.INDIGO -> ThemePalette(
        light = ThemeRamp(
            primary = 0xFF3F6094,
            onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFEAF2FF,
            onPrimaryContainer = 0xFF293C58,
            inversePrimary = 0xFF5B7FB4,
            secondary = 0xFF666079,
            onSecondary = 0xFFFFFFFF,
            secondaryContainer = 0xFFF1EEF8,
            onSecondaryContainer = 0xFF494158,
            tertiary = 0xFF80613E,
            onTertiary = 0xFFFFFFFF,
            tertiaryContainer = 0xFFFFF5DC,
            onTertiaryContainer = 0xFF574225,
        ),
        dark = ThemeRamp(
            primary = 0xFFBBD2F5,
            onPrimary = 0xFF1D2C44,
            primaryContainer = 0xFF29374B,
            onPrimaryContainer = 0xFFD7E6FA,
            inversePrimary = 0xFF3F6094,
            secondary = 0xFFD4C4F0,
            onSecondary = 0xFF2E2838,
            secondaryContainer = 0xFF383149,
            onSecondaryContainer = 0xFFEFE7FA,
            tertiary = 0xFFDCC0A0,
            onTertiary = 0xFF3F2E1A,
            tertiaryContainer = 0xFF483929,
            onTertiaryContainer = 0xFFF3E8D8,
        ),
    )

    ThemeAccent.PLUM -> ThemePalette(
        light = ThemeRamp(
            primary = 0xFF8A4A63,
            onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFFFEFF3,
            onPrimaryContainer = 0xFF5A2A3A,
            inversePrimary = 0xFFB06A86,
            secondary = 0xFF666079,
            onSecondary = 0xFFFFFFFF,
            secondaryContainer = 0xFFF1EEF8,
            onSecondaryContainer = 0xFF494158,
            tertiary = 0xFF80613E,
            onTertiary = 0xFFFFFFFF,
            tertiaryContainer = 0xFFFFF5DC,
            onTertiaryContainer = 0xFF574225,
        ),
        dark = ThemeRamp(
            primary = 0xFFF0BDD0,
            onPrimary = 0xFF431F2D,
            primaryContainer = 0xFF45313B,
            onPrimaryContainer = 0xFFF7DDE6,
            inversePrimary = 0xFF8A4A63,
            secondary = 0xFFD4C4F0,
            onSecondary = 0xFF2E2838,
            secondaryContainer = 0xFF383149,
            onSecondaryContainer = 0xFFEFE7FA,
            tertiary = 0xFFDCC0A0,
            onTertiary = 0xFF3F2E1A,
            tertiaryContainer = 0xFF483929,
            onTertiaryContainer = 0xFFF3E8D8,
        ),
    )
}

// ---------------------------------------------------------------- contrast arithmetic

/**
 * WCAG 2.1 relative luminance of an `0xAARRGGBB` colour, in `0.0..1.0`.
 *
 * The sRGB channels are linearised before they are weighted, because an average of the encoded
 * channels would call `#808080` "half as bright" as white when it is about a fifth.
 */
fun relativeLuminance(argb: Long): Double {
    fun linear(channel: Double): Double =
        if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

    val red = linear(channelOf(argb, 16))
    val green = linear(channelOf(argb, 8))
    val blue = linear(channelOf(argb, 0))
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

/** WCAG 2.1 contrast ratio between two opaque colours, in `1.0..21.0`. */
fun contrastRatio(first: Long, second: Long): Double {
    val a = relativeLuminance(first)
    val b = relativeLuminance(second)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

/** One 8-bit sRGB channel as `0.0..1.0`; [shift] is 16 for red, 8 for green, 0 for blue. */
private fun channelOf(argb: Long, shift: Int): Double =
    ((argb shr shift) and 0xFFL).toDouble() / 255.0
