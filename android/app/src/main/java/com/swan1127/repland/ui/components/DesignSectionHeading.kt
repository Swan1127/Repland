package com.swan1127.repland.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swan1127.repland.ui.theme.replandAccent
import com.swan1127.repland.ui.theme.ReplandAccent

/** Shared visual hierarchy for the new assistant and usage summary. */
@Composable
internal fun DesignSectionHeading(
    title: String,
    icon: ImageVector,
    accentIndex: Int = 0,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    accent: ReplandAccent = replandAccent(accentIndex),
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = accent.surface, shape = RoundedCornerShape(12.dp)) {
            Icon(icon, contentDescription = null, tint = accent.ink,
                modifier = Modifier.size(40.dp).padding(10.dp))
        }
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
    }
}
