package com.swan1127.repland.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AppShellHeader(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    onUndo: (() -> Unit)?,
    onVoiceCapture: (() -> Unit)?,
    onOpenCommands: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                onBack?.let { action ->
                    IconButton(onClick = action, modifier = Modifier.testTag("secondary-back")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                onUndo?.let { action ->
                    TextButton(onClick = action, modifier = Modifier.testTag("undo-recent-change")) { Text("撤销") }
                }
                onVoiceCapture?.let { action ->
                    TextButton(onClick = action, modifier = Modifier.testTag("voice-capture")) { Text("语音") }
                }
                IconButton(onClick = onOpenCommands, modifier = Modifier.testTag("open-command-palette")) {
                    Icon(PlannerIcons.Schedule, contentDescription = "打开功能与操作")
                }
            }
            subtitle?.let { text ->
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
