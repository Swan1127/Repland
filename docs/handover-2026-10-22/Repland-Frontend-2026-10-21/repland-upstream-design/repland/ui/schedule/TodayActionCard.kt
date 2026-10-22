package com.swan1127.repland.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swan1127.repland.ui.components.LandscapeArtwork
import com.swan1127.repland.ui.components.LandscapeStyle
import com.swan1127.repland.ui.components.DesignButton
import com.swan1127.repland.ui.theme.LocalReplandVisuals

@Composable
fun TodayActionCard(
    label: String,
    title: String,
    subtitle: String?,
    parallelLabel: String?,
    unplacedLabel: String?,
    actionLabel: String,
    onAction: () -> Unit,
    onOpenTasks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visuals = LocalReplandVisuals.current
    val compactDecoration = LocalConfiguration.current.fontScale < 1.4f
    Surface(
        modifier = modifier.fillMaxWidth().testTag("today-focus"),
        color = visuals.focusSurface,
        shape = RoundedCornerShape(26.dp),
    ) {
        Box(Modifier.clipToBounds()) {
            if (compactDecoration) {
                LandscapeArtwork(
                    modifier = Modifier.align(Alignment.TopEnd).width(112.dp).height(112.dp)
                        .clip(RoundedCornerShape(bottomStart = 36.dp)),
                    style = LandscapeStyle.Dawn,
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(end = if (compactDecoration) 88.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = visuals.focusMuted)
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = visuals.focusText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = visuals.focusMuted)
                    }
                    parallelLabel?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = visuals.focusMuted)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesignButton(onClick = onAction, modifier = Modifier.weight(1f, fill = false)) {
                        Text(actionLabel)
                    }
                    unplacedLabel?.let {
                        TextButton(onClick = onOpenTasks, modifier = Modifier.weight(1f, fill = false)) {
                            Text(it, color = visuals.focusAction)
                        }
                    }
                }
            }
        }
    }
}
