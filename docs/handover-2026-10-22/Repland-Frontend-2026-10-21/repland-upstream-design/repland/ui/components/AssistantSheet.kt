package com.swan1127.repland.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AssistantSheet(
    taskTitle: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    initialPrompt: String = "",
    onDraftChange: (String) -> Unit = {},
) {
    var prompt by rememberSaveable(taskTitle) { mutableStateOf(initialPrompt) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val suggestions = listOf("帮我理解这项任务", "估算合适的时间", "拆成下一步", "帮我调整今天")
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * .86f).dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.DateRange, contentDescription = null, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("和 Repland 说说", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(taskTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("根据输入选择理解任务、估时、拆解或调整建议。此入口不会将这段文字作为聊天消息发送；自由对话请使用首页的一句话安排。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { suggestion ->
                    DesignFilterChip(
                        selected = prompt == suggestion,
                        onClick = { prompt = suggestion; onDraftChange(suggestion) },
                        label = { Text(suggestion) },
                        accentIndex = 1,
                    )
                }
            }
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it; onDraftChange(it) },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("assistant-prompt"),
                label = { Text("要处理的内容") },
                placeholder = { Text("例如：我只完成了一半，接下来怎么安排？") },
                minLines = 3,
                maxLines = 6,
            )
            DesignButton(
                onClick = { focusManager.clearFocus(); onSubmit(prompt.trim()) },
                enabled = prompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("assistant-submit"),
            ) { Text("发送") }
            Spacer(Modifier.height(12.dp))
        }
    }
}
