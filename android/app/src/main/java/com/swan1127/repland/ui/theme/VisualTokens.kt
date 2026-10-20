package com.swan1127.repland.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

data class ReplandVisuals(
    val focusSurface: Color,
    val focusText: Color,
    val focusMuted: Color,
    val focusAction: Color,
    val onFocusAction: Color,
)

val LightVisuals = ReplandVisuals(Color(0xFFEAF2FF), Color(0xFF293C58), Color(0xFF526279), Color(0xFFDAF2E4), Color(0xFF39634D))
val DarkVisuals = ReplandVisuals(Color(0xFF283446), Color(0xFFE7EDFA), Color(0xFFBDC9DD), Color(0xFFA8D9DE), Color(0xFF173B44))
val LocalReplandVisuals = staticCompositionLocalOf { LightVisuals }

data class ReplandAccent(val surface: Color, val ink: Color, val highlight: Color = surface)

private val LightAccents = listOf(
    ReplandAccent(Color(0xFFEAF2FF), Color(0xFF3F6094), Color(0xFFDCEAFF)),
    ReplandAccent(Color(0xFFEAF7EF), Color(0xFF39634D), Color(0xFFDAF2E4)),
    ReplandAccent(Color(0xFFF2EDFF), Color(0xFF68558F), Color(0xFFE8DFFF)),
    ReplandAccent(Color(0xFFFFF5DC), Color(0xFF786020), Color(0xFFFFEAC2)),
    ReplandAccent(Color(0xFFFFEFF3), Color(0xFF92465F), Color(0xFFFFDFE9)),
)
private val DarkAccents = listOf(
    ReplandAccent(Color(0xFF29374B), Color(0xFFBBD2F5), Color(0xFF334963)),
    ReplandAccent(Color(0xFF293E35), Color(0xFFB5DCC5), Color(0xFF355342)),
    ReplandAccent(Color(0xFF383149), Color(0xFFD4C4F0), Color(0xFF493C62)),
    ReplandAccent(Color(0xFF413B2C), Color(0xFFE8D59E), Color(0xFF54472D)),
    ReplandAccent(Color(0xFF45313B), Color(0xFFF0BDD0), Color(0xFF5C3B4B)),
)

/** Quiet surface/ink pairs shared by navigation and content; labels retain semantic meaning. */
@Composable
fun replandAccent(index: Int): ReplandAccent {
    val accents = if (MaterialTheme.colorScheme.background.luminance() < .2f) DarkAccents else LightAccents
    return accents[Math.floorMod(index, accents.size)]
}

/** Adjacent blue-green washes connect personal pages without competing colour blocks. */
@Composable
fun replandSettingsAccent(index: Int = 0): ReplandAccent {
    val scheme = MaterialTheme.colorScheme
    val base = when (Math.floorMod(index, 3)) {
        1 -> lerp(scheme.primaryContainer, replandAccent(0).surface, .6f)
        2 -> replandAccent(1).surface
        else -> scheme.primaryContainer
    }
    return ReplandAccent(
        surface = lerp(base, scheme.surfaceContainerLow, .45f),
        ink = scheme.primary,
        highlight = lerp(base, scheme.surfaceContainerLow, .05f),
    )
}

val ReplandShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
