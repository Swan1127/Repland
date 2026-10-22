package com.swan1127.repland.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.swan1127.repland.ui.theme.pressMotion
import com.swan1127.repland.ui.theme.replandAccent
import com.swan1127.repland.ui.theme.LocalReplandVisuals
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/** Shared visual treatment over native Material controls; semantics and ripple stay native. */
@Composable
fun DesignButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = LocalReplandVisuals.current.mainAction,
        contentColor = LocalReplandVisuals.current.onMainAction,
    ),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    Button(onClick, modifier.heightIn(min = 48.dp).then(pressMotion(source, enabled)), enabled, shape, colors, elevation, border,
        contentPadding, source, content)
}

@Composable
fun DesignOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp).then(pressMotion(source, enabled)), enabled, shape, colors, elevation, border,
        contentPadding, source, content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    accentIndex: Int = 0,
) {
    val source = remember { MutableInteractionSource() }
    val accent = replandAccent(accentIndex)
    FilterChip(selected = selected, onClick = onClick, label = label,
        modifier = modifier.heightIn(min = 48.dp).then(pressMotion(source, enabled)), enabled = enabled, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        interactionSource = source,
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = accent.surface,
            selectedLabelColor = accent.ink,
            selectedLeadingIconColor = accent.ink,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled, selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant, selectedBorderColor = accent.ink.copy(alpha = .2f)))
}

@Composable
fun DesignClickableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadowElevation: Dp = 0.dp,
    pressedScale: Float = .98f,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.then(pressMotion(source, enabled, pressedScale)),
        shape = shape, color = color, shadowElevation = shadowElevation, interactionSource = source, content = content)
}

@Composable
fun RowScope.DesignNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    accentIndex: Int,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val accent = replandAccent(accentIndex)
    NavigationBarItem(selected = selected, onClick = onClick, icon = icon, label = label,
        modifier = modifier.then(pressMotion(source, true)), interactionSource = source,
        colors = NavigationBarItemDefaults.colors(selectedIconColor = accent.ink,
            selectedTextColor = accent.ink, indicatorColor = accent.surface))
}
