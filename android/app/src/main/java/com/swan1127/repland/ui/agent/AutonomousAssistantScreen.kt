package com.swan1127.repland.ui.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskInputSource
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.ui.components.DesignButton
import com.swan1127.repland.ui.components.DesignClickableSurface
import com.swan1127.repland.ui.components.DesignIcons
import com.swan1127.repland.ui.components.DesignSectionHeading
import com.swan1127.repland.ui.theme.replandAccent

/** One utterance, one operation. The parent owns execution, receipts, errors and undo. */
@Composable
fun AutonomousAssistantScreen(
    state: AutonomyUiState,
    tasks: List<Task>,
    onSubmit: (String) -> Unit,
    onUndo: () -> Unit,
    onOpenTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var observedSubmission by rememberSaveable { mutableStateOf(state.submission) }
    var observedAttempts by rememberSaveable { mutableStateOf(state.completedAttempts) }
    var awaitingReply by remember { mutableStateOf(false) }
    var sawBusy by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // Keep failed input. A completed submission, rather than a transient response, acknowledges it.
    LaunchedEffect(state.submission, state.completedAttempts, state.busy) {
        if (state.submission != observedSubmission) {
            input = ""
            observedSubmission = state.submission
            awaitingReply = false
            sawBusy = false
        }
        if (state.completedAttempts != observedAttempts) {
            observedAttempts = state.completedAttempts
            awaitingReply = false
            sawBusy = false
        }
        if (state.busy) {
            sawBusy = true
        } else if (sawBusy) {
            awaitingReply = false
            sawBusy = false
        }
    }
    val working = state.busy || awaitingReply
    val canSend = state.ready && !working && input.isNotBlank()
    val currentTasks = tasks.associateBy(Task::id)
    val shownTasks = state.queryResults?.mapNotNull { result ->
        currentTasks[result.id]?.takeIf { it.status.isActive }
    }.orEmpty().take(50)
    // Receipts survive reopening; query results only appear in response to an explicit query.
    val response = state.response?.takeIf(String::isNotBlank)
        ?: state.delegation.summary?.takeIf { state.delegation.canUndo && it.isNotBlank() }
        ?: if (state.queryResults != null) {
            if (shownTasks.isEmpty()) "没有符合条件的任务。" else "找到 ${shownTasks.size} 件任务，点开可查看详情。"
        } else null

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(scrollState)
            .testTag("autonomous-assistant-scroll").padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !working,
                    label = { Text("告诉我需要处理的事") },
                    placeholder = { Text("例如：明天复习物理 45 分钟") },
                    modifier = Modifier.fillMaxWidth().testTag("autonomous-input"),
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                )
                state.error?.takeIf(String::isNotBlank)?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("autonomous-error")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                DesignButton(
                    onClick = {
                        if (state.ready && !state.busy && !awaitingReply && input.isNotBlank()) {
                            awaitingReply = true
                            focus.clearFocus()
                            onSubmit(input.trim())
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier.align(Alignment.End).testTag("autonomous-submit"),
                ) {
                    Text(if (working) "正在处理…" else if (!state.ready) "正在准备…" else "发送并处理")
                    Spacer(Modifier.width(8.dp))
                    Icon(DesignIcons.ArrowRight, null, Modifier.size(20.dp))
                }
            }
        }
        response?.let {
            Surface(color = replandAccent(1).surface, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesignSectionHeading("处理结果", DesignIcons.Tasks, accentIndex = 1)
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("autonomous-response")
                            .semantics { liveRegion = LiveRegionMode.Polite })
                    if (state.delegation.canUndo) {
                        TextButton(
                            onClick = onUndo,
                            enabled = state.ready && !working,
                            modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp).testTag("autonomy-undo"),
                        ) {
                            Text("撤销上次操作")
                        }
                    }
                }
            }
        }
        shownTasks.forEach { task ->
            AssistantTaskResult(task, enabled = !working, onOpen = { onOpenTask(task.id) })
        }
    }
}

@Composable
private fun AssistantTaskResult(task: Task, enabled: Boolean, onOpen: () -> Unit) {
    val accent = replandAccent(1)
    val status = when (task.status) {
        TaskStatus.NOT_STARTED -> "待开始"
        TaskStatus.IN_PROGRESS -> "进行中"
        TaskStatus.COMPLETED -> "已完成"
        TaskStatus.POSTPONED -> "已顺延"
        TaskStatus.CANCELLED -> "已取消"
        TaskStatus.REPLACED -> "已替换"
    }
    val duration = task.totalDurationMinutes?.let {
        if (task.inputSources.duration == TaskInputSource.PLANNING_ESTIMATE) "规划估算 · $it 分钟" else "预计 $it 分钟"
    }
    DesignClickableSurface(
        onClick = onOpen,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("autonomous-task-${task.id}"),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accent.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(40.dp)) {
                Icon(DesignIcons.Tasks, null, Modifier.padding(10.dp), tint = accent.ink)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(task.displayName, style = MaterialTheme.typography.titleMedium)
                Text(listOfNotNull(status, duration, task.progressPercent?.let { "进度 $it%" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(DesignIcons.ArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
