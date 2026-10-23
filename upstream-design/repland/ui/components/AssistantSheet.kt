package com.swan1127.repland.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AssistantSheet(
    taskTitle: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var prompt by remember(taskTitle) { mutableStateOf("") }
    var voiceError by remember { mutableStateOf(false) }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !words.isNullOrBlank()) {
            prompt = if (prompt.isBlank()) words else "$prompt\n$words"
            voiceError = false
        } else voiceError = true
    }
    val suggestions = listOf("帮我理解这项任务", "估算合适的时间", "拆成下一步", "帮我调整今天")
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.DateRange, contentDescription = null, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("和 Repland 说说", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(taskTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("你可以直接说发生了什么，我会根据上下文选择合适的建议。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEachIndexed { index, suggestion ->
                    DesignFilterChip(
                        selected = prompt == suggestion,
                        onClick = { prompt = suggestion },
                        label = { Text(suggestion) },
                        accentIndex = index,
                    )
                }
            }
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth().testTag("assistant-prompt"),
                label = { Text("本次想讨论的情况") },
                placeholder = { Text("例如：我只完成了一半，接下来怎么安排？") },
                minLines = 3,
                trailingIcon = {
                    IconButton(onClick = {
                        voiceError = false
                        runCatching {
                            speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                            })
                        }.onFailure { voiceError = true }
                    }) { Icon(PlannerIcons.Voice, contentDescription = "语音输入", modifier = Modifier.size(20.dp)) }
                },
            )
            if (voiceError) Text("没有识别到语音，可以继续输入文字。", color = MaterialTheme.colorScheme.error)
            Button(
                onClick = { onSubmit(prompt.trim()) },
                enabled = prompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("assistant-submit"),
            ) { Text("发送") }
            Spacer(Modifier.height(12.dp))
        }
    }
}
