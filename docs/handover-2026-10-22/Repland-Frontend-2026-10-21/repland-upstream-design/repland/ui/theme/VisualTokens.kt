package com.swan1127.repland.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.swan1127.repland.domain.model.TaskCategory

data class ReplandVisuals(
    val focusSurface: Color,
    val focusText: Color,
    val focusMuted: Color,
    val focusAction: Color,
    val onFocusAction: Color,
    val mainAction: Color,
    val onMainAction: Color,
    val profileSurface: Color,
    val profileText: Color,
)

val LightVisuals = ReplandVisuals(
    focusSurface = Color(0xFFEAF2FF),
    focusText = Color(0xFF263D43),
    focusMuted = Color(0xFF526279),
    focusAction = Color(0xFF326B75),
    onFocusAction = Color.White,
    mainAction = Color(0xFFDAF2E4),
    onMainAction = Color(0xFF39634D),
    profileSurface = Color(0xFFE8F3F5),
    profileText = Color(0xFF326B75),
)
val DarkVisuals = ReplandVisuals(
    focusSurface = Color(0xFF263C46),
    focusText = Color(0xFFE7EFEB),
    focusMuted = Color(0xFFBDC9DD),
    focusAction = Color(0xFFA8D9DE),
    onFocusAction = Color(0xFF173B44),
    mainAction = Color(0xFF304E40),
    onMainAction = Color(0xFFCEEDDA),
    profileSurface = Color(0xFF263F43),
    profileText = Color(0xFFA8D9DE),
)
val LocalReplandVisuals = staticCompositionLocalOf { LightVisuals }

data class ReplandAccent(val surface: Color, val ink: Color)

fun taskAccentIndex(category: TaskCategory): Int = when (category) {
    TaskCategory.COURSE -> 0
    TaskCategory.EXTRACURRICULAR -> 1
    TaskCategory.OFFICE -> 2
    TaskCategory.LEISURE -> 3
}

private val LightAccents = listOf(
    ReplandAccent(Color(0xFFEAF2FF), Color(0xFF3F6094)),
    ReplandAccent(Color(0xFFEAF7EF), Color(0xFF39634D)),
    ReplandAccent(Color(0xFFE8F3F5), Color(0xFF326B75)),
    ReplandAccent(Color(0xFFFFF5DC), Color(0xFF786020)),
    ReplandAccent(Color(0xFFFFEFF3), Color(0xFF92465F)),
)
private val DarkAccents = listOf(
    ReplandAccent(Color(0xFF29374B), Color(0xFFBBD2F5)),
    ReplandAccent(Color(0xFF293E35), Color(0xFFB5DCC5)),
    ReplandAccent(Color(0xFF263F43), Color(0xFFA8D9DE)),
    ReplandAccent(Color(0xFF413B2C), Color(0xFFE8D59E)),
    ReplandAccent(Color(0xFF45313B), Color(0xFFF0BDD0)),
)

@Composable
fun replandAccent(index: Int): ReplandAccent {
    val accents = if (MaterialTheme.colorScheme.background.luminance() < .2f) DarkAccents else LightAccents
    return accents[Math.floorMod(index, accents.size)]
}

val ReplandShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
