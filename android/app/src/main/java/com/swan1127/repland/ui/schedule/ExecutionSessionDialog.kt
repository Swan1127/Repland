package com.swan1127.repland.ui.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.delay

@Composable
fun ExecutionSessionDialog(
    session: ExecutionSession,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: (ExecutionOutcome, TaskFeedback) -> Unit,
    canOperate: Boolean = true,
    readNotice: (@Composable () -> Unit)? = null,
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var partial by rememberSaveable(session.id) { mutableStateOf(false) }
    var progress by rememberSaveable(session.id) { mutableStateOf("") }
    var content by rememberSaveable(session.id) { mutableStateOf("") }
    LaunchedEffect(session.id, session.runningSinceEpochMillis) {
        while (true) { now = System.currentTimeMillis(); delay(1_000) }
    }
    val elapsedSeconds = session.elapsedMillis(now) / 1_000
    val remaining = (session.targetSeconds - elapsedSeconds).coerceAtLeast(0)
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(
        // Compose 1.7's non-default measurement uses screen dimensions even
        // when the native dialog is inset. Honor native constraints instead.
        usePlatformDefaultWidth = true,
        decorFitsSystemWindows = false,
    )) {
        Surface(Modifier.fillMaxSize().testTag("execution-session"), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (session.isPaused) "本轮已暂停" else if (remaining == 0L) "计时已到 · 等待你确认结果" else "专注中",
                    style = MaterialTheme.typography.titleMedium)
                Text(session.taskTitle, style = MaterialTheme.typography.headlineSmall)
                Text(String.format("%02d:%02d", remaining / 60, remaining % 60),
                    style = if (LocalDensity.current.fontScale > 1.3f) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayLarge,
                    fontFamily = FontFamily.Monospace)
                Text("已投入 ${elapsedSeconds / 60} 分 ${elapsedSeconds % 60} 秒 · 暂停不计时",
                    style = MaterialTheme.typography.bodyMedium)
                Text("返回页面或切到后台会继续计时；不会自动完成任务。", style = MaterialTheme.typography.bodyMedium)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("execution-error")) }
                readNotice?.invoke()
                OutlinedButton(onClick = if (session.isPaused) onResume else onPause, enabled = !busy && canOperate,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("execution-pause-resume")) {
                    Text(if (session.isPaused) "继续计时" else "暂停计时")
                }
                if (partial) {
                    val validProgress = progress.all { it in '0'..'9' } && progress.toIntOrNull() in 1..99
                    OutlinedTextField(progress, { progress = it }, label = { Text("任务累计进度（1–99%）") },
                        modifier = Modifier.fillMaxWidth().testTag("execution-progress"), enabled = !busy,
                        isError = progress.isNotBlank() && !validProgress,
                        supportingText = { if (progress.isNotBlank() && !validProgress) Text("请输入 1–99 的整数百分比，不接受负数或小数。") })
                    OutlinedTextField(content, { content = it }, label = { Text("已完成内容") },
                        modifier = Modifier.fillMaxWidth().testTag("execution-content"), enabled = !busy)
                    Button(onClick = { onFinish(ExecutionOutcome.PARTIAL, TaskFeedback(progressPercent = progress.toIntOrNull(), completedContent = content)) },
                        enabled = !busy && canOperate && validProgress && content.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("execution-partial-save")) { Text("记录部分完成并结束本轮") }
                    TextButton(onClick = { partial = false }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消填写") }
                } else {
                    Button(onClick = { onFinish(ExecutionOutcome.CONTINUE, TaskFeedback()) }, enabled = !busy && canOperate,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("execution-continue")) { Text("结束本轮 · 保留任务状态") }
                    OutlinedButton(onClick = { partial = true }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("部分完成") }
                    OutlinedButton(onClick = { onFinish(ExecutionOutcome.COMPLETED, TaskFeedback(progressPercent = 100)) }, enabled = !busy && canOperate,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("execution-complete")) { Text("确认整个任务完成") }
                    TextButton(onClick = { onFinish(ExecutionOutcome.SKIPPED, TaskFeedback(postponeReason = "本轮跳过")) }, enabled = !busy && canOperate,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("跳过本轮 · 延后任务") }
                }
                TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("execution-return")) { Text("返回页面 · 保留本轮") }
            }
        }
    }
}
