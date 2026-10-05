package com.swan1127.repland.ui.components

import androidx.activity.compose.BackHandler

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskCaptureDraft
import com.swan1127.repland.domain.model.TaskCaptureStage
import com.swan1127.repland.domain.model.TaskName
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * The default list representation is intentionally compact. Full task information belongs
 * in the detail screen, not in every row users scan throughout the day.
 */
@Composable
fun TaskRow(
    task: Task,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: TaskRowEmphasis = TaskRowEmphasis.NORMAL,
    planningSummary: String? = null,
) {
    val statusColor = when {
        task.status == TaskStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
        task.status == TaskStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        task.dueDate?.isBefore(LocalDate.now()) == true && task.status.isActive -> MaterialTheme.colorScheme.error
        task.scheduledForDate?.isBefore(LocalDate.now()) == true && task.status.isActive -> MaterialTheme.colorScheme.tertiary
        emphasis == TaskRowEmphasis.ATTENTION -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val summary = planningSummary ?: task.summaryLabel()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .testTag("task-card-${task.displayName}")
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = if (task.status == TaskStatus.NOT_STARTED || task.status == TaskStatus.POSTPONED) onStart else onOpen,
            modifier = Modifier
                .size(48.dp)
                .testTag("task-start-${task.id}")
                .semantics { contentDescription = if (task.status == TaskStatus.NOT_STARTED || task.status == TaskStatus.POSTPONED) "开始 ${task.displayName}" else "查看 ${task.displayName}" },
        ) {
            Icon(
                imageVector = when (task.status) {
                    TaskStatus.COMPLETED -> Icons.Outlined.CheckCircle
                    TaskStatus.IN_PROGRESS -> Icons.Outlined.PlayArrow
                    else -> Icons.Outlined.PlayArrow
                },
                contentDescription = null,
                tint = statusColor,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = task.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (task.status == TaskStatus.COMPLETED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TaskMetaLabel(task.category.captureLabel())
                TaskMetaLabel(task.userPriority.captureLabel())
                task.totalDurationMinutes?.let { TaskMetaLabel("${it} 分钟") }
            }
            val progress = when (task.status) {
                TaskStatus.COMPLETED -> 1f
                else -> (task.progressPercent ?: 0).coerceIn(0, 100) / 100f
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
        Icon(
            imageVector = if (emphasis == TaskRowEmphasis.ATTENTION) Icons.Outlined.Warning else Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.padding(horizontal = 8.dp).size(18.dp),
        )
    }
}

@Composable
private fun TaskMetaLabel(label: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(6.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

enum class TaskRowEmphasis { NORMAL, ATTENTION }

@Composable
fun NowCard(
    task: Task,
    timeLabel: String,
    actionLabel: String,
    onAction: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .testTag("now-task-${task.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerLow)))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (task.status == TaskStatus.IN_PROGRESS) "正在进行" else "下一项",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpen, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "查看任务详情")
                }
            }
            Text(task.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(timeLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAction, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun CapacitySummary(
    scheduledMinutes: Int,
    taskCount: Int,
    onOpenPlan: () -> Unit,
) {
    val hour = scheduledMinutes / 60
    val minute = scheduledMinutes % 60
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenPlan)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("今天的安排", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (taskCount == 0) "还没有具体时段" else "已安排 ${hour} 小时 ${minute} 分钟 · $taskCount 项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "查看计划", modifier = Modifier.size(16.dp))
        }
    }
}

private val CaptureDraftSaver = listSaver<TaskCaptureDraft, String>(
    save = { listOf(it.id, it.text, it.stage.name, it.dueDate?.toString().orEmpty(), it.scheduledForDate?.toString().orEmpty(),
        it.duration.toString(), it.category.name, it.priority.name, it.isCustomDuration.toString(), it.customDurationText) },
    restore = { TaskCaptureDraft(it[0], it[1], TaskCaptureStage.valueOf(it[2]), it[3].takeIf(String::isNotEmpty)?.let(LocalDate::parse),
        it[4].takeIf(String::isNotEmpty)?.let(LocalDate::parse), it[5].toInt(), TaskCategory.valueOf(it[6]),
        TaskPriority.valueOf(it[7]), it[8].toBoolean(), it[9]) },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskCaptureSheet(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (TaskDraft) -> Unit,
    onVoice: (() -> Unit)? = null,
    draft: TaskCaptureDraft? = null,
    onDraftChange: (TaskCaptureDraft) -> Unit = {},
    saving: Boolean = false,
    persisted: Boolean = false,
    saveError: String? = null,
    onDiscard: (() -> Unit)? = null,
) {
    var localDraft by rememberSaveable(stateSaver = CaptureDraftSaver) { mutableStateOf(TaskCaptureDraft(text = initialText)) }
    val current = draft ?: localDraft
    val imeBridge = remember { CaptureImeBridge() }
    val sheetScope = rememberCoroutineScope()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    fun update(value: TaskCaptureDraft) { if (!saving) { localDraft = value; onDraftChange(value) } }
    fun step(value: TaskCaptureStage) { imeBridge.hide(); update(current.copy(stage = value)) }
    LaunchedEffect(initialText) {
        if (initialText.isNotBlank() && initialText != current.text) update(current.copy(text = initialText, stage = TaskCaptureStage.CAPTURE))
    }
    val latestSaving by rememberUpdatedState(saving)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            when {
                target != SheetValue.Hidden -> true
                latestSaving -> false
                imeBridge.visible -> { imeBridge.hide(); false }
                else -> true
            }
        })

    fun save(value: TaskCaptureDraft = current) {
        if (saving || value.text.isBlank()) return
        if (!value.durationIsValid) { step(TaskCaptureStage.DURATION); return }
        imeBridge.hide()
        if (value != current) update(value)
        onSave(value.toTaskDraft())
    }

    ModalBottomSheet(
        modifier = Modifier.testTag("task-capture-sheet"),
        // Material 3 1.3.0 invokes this after a Back-triggered hide even when vetoed.
        onDismissRequest = { if (!saving && !sheetState.isVisible) onDismiss() },
        sheetState = sheetState,
        // 1.3.0's overlay Back callback bypasses confirmValueChange via hide().
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val sheetFocus = LocalFocusManager.current
        val sheetKeyboard = LocalSoftwareKeyboardController.current
        val sheetImeVisible = WindowInsets.isImeVisible
        BackHandler {
            if (!saving) {
                if (sheetImeVisible) { sheetFocus.clearFocus(); sheetKeyboard?.hide() }
                else sheetScope.launch { sheetState.hide(); if (!sheetState.isVisible) onDismiss() }
            }
        }
        SideEffect {
            // Read controllers/insets from the dialog, not the underlying Activity window.
            imeBridge.visible = sheetImeVisible
            imeBridge.hide = { sheetFocus.clearFocus(); sheetKeyboard?.hide() }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compactHeight = maxHeight < 480.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9f).dp)
                .then(if (compactHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("新任务", style = MaterialTheme.typography.titleLarge)
                    Text(if (persisted) "草稿已保存在此设备，尚未创建任务" else if (current.text.isBlank()) "关闭可保留填写内容，尚未创建任务" else "尚未创建任务 · 正在暂存草稿",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { imeBridge.hide(); onDismiss() }, enabled = !saving, modifier = Modifier.testTag("task-capture-close")) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭并保留草稿")
                }
            }
            if (current.stage != TaskCaptureStage.CAPTURE) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
                    Text(current.text, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.fillMaxWidth().then(if (compactHeight) Modifier else
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))) {
                when (current.stage) {
                    TaskCaptureStage.CAPTURE -> CaptureInputStep(
                        text = current.text,
                        onTextChange = { update(current.copy(text = it)) },
                        onMore = { if (current.text.isNotBlank()) step(TaskCaptureStage.DETAILS) },
                        onVoice = onVoice?.let { action -> { imeBridge.hide(); action() } },
                        category = current.category, priority = current.priority,
                        draft = current, enabled = !saving,
                    )
                    TaskCaptureStage.INTENT -> CaptureIntentStep(
                        onSaveToday = { save(current.copy(scheduledForDate = LocalDate.now())) },
                        onChooseDuration = { step(TaskCaptureStage.DURATION) },
                        onChooseDeadline = { step(TaskCaptureStage.DEADLINE) },
                        enabled = !saving,
                    )
                    TaskCaptureStage.DURATION -> CaptureDurationStep(
                        draft = current, onChange = ::update, enabled = !saving,
                    )
                    TaskCaptureStage.DEADLINE -> CaptureDeadlineStep(
                        selectedDate = current.dueDate,
                        onSelect = { selected -> save(current.copy(dueDate = selected)) },
                        enabled = !saving,
                    )
                    TaskCaptureStage.DETAILS -> CaptureDetailsStep(
                        category = current.category, priority = current.priority,
                        onCategory = { update(current.copy(category = it)) },
                        onPriority = { update(current.copy(priority = it)) },
                        enabled = !saving,
                    )
                }
            }
            saveError?.let { Text(it, modifier = Modifier.testTag("task-capture-error"),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (!current.durationIsValid && current.stage != TaskCaptureStage.DURATION) {
                Text("自定义时长尚未有效，原输入仍保留。请修改时长或明确选择“不确定”。",
                    modifier = Modifier.testTag("task-capture-duration-error"),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { step(TaskCaptureStage.DURATION) }, enabled = !saving,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("task-capture-correct-duration")) { Text("修改时长") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (current.stage) {
                    TaskCaptureStage.CAPTURE -> {
                        Button(onClick = { step(TaskCaptureStage.INTENT) }, enabled = current.text.isNotBlank() && !saving,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("task-capture-arrange-today")) { Text("安排今天") }
                        OutlinedButton(onClick = { save() }, enabled = current.text.isNotBlank() && !saving,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("task-capture-save-inbox")) {
                            Text(if (saving) "正在保存…" else if (current.dueDate != null || current.scheduledForDate != null)
                                "保存任务（保留已选日期）" else "先保存到待安排")
                        }
                    }
                    TaskCaptureStage.DURATION -> Button(onClick = { save(current.copy(scheduledForDate = LocalDate.now())) },
                        enabled = current.durationIsValid && !saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("task-capture-save-duration")) { Text(if (saving) "正在保存…" else "保存到今天（未排时段）") }
                    TaskCaptureStage.DETAILS -> Button(onClick = { save() }, enabled = !saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("task-capture-save-details")) { Text(if (saving) "正在保存…" else "保存任务") }
                    else -> if (saving) Text("正在保存…", style = MaterialTheme.typography.bodyMedium)
                }
                if (current.stage != TaskCaptureStage.CAPTURE) TextButton(onClick = {
                    step(if (current.stage == TaskCaptureStage.INTENT || current.stage == TaskCaptureStage.DETAILS) TaskCaptureStage.CAPTURE else TaskCaptureStage.INTENT)
                }, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("task-capture-back")) { Text("返回修改") }
                if (current.stage == TaskCaptureStage.CAPTURE && current.text.isNotBlank()) TextButton(
                    onClick = { confirmDiscard = true }, enabled = !saving, modifier = Modifier.testTag("task-capture-discard")) { Text("放弃草稿") }
            }
        }
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("放弃这份草稿？") },
        text = { Text("会清除未创建的内容和选项，不影响已有任务。关闭抽屉则会保留草稿。") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; if (onDiscard != null) onDiscard() else update(TaskCaptureDraft()) },
            modifier = Modifier.testTag("task-capture-confirm-discard")) { Text("放弃草稿") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } })
}

/** Event-only bridge across the ModalBottomSheet's separate window/composition. */
private class CaptureImeBridge {
    var visible: Boolean = false
    var hide: () -> Unit = {}
}

@Composable
private fun CaptureInputStep(
    text: String,
    onTextChange: (String) -> Unit,
    onMore: () -> Unit,
    onVoice: (() -> Unit)?,
    category: TaskCategory,
    priority: TaskPriority,
    draft: TaskCaptureDraft,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("接下来要做什么？", style = MaterialTheme.typography.headlineSmall)
        Text("先记下，再决定什么时候做。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text,
            enabled = enabled,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().testTag("task-capture-input"),
            placeholder = { Text("写下任务……") },
            label = { Text("任务内容") },
            minLines = 2,
            maxLines = 4,
        )
        Text(listOf("${category.captureLabel()}", "${priority.captureLabel()}优先级",
            if (!draft.durationIsValid) "时长待修改" else draft.selectedDuration?.let { "$it 分钟" } ?: "时长未知",
            draft.dueDate?.let { "截止 ${taskDateLabel(it)}" } ?: "未设截止日",
            draft.scheduledForDate?.let { "计划 ${taskDateLabel(it)}" } ?: "未设计划日").joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onMore, enabled = enabled && text.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp).testTag("task-capture-more")) {
            Text("修改分类与优先级")
        }
        onVoice?.let { TextButton(onClick = it, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag("voice-capture")) { Text("语音输入") } }
    }
}

@Composable
private fun CaptureIntentStep(
    onSaveToday: () -> Unit,
    onChooseDuration: () -> Unit,
    onChooseDeadline: () -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("安排到今天", style = MaterialTheme.typography.headlineSmall)
        Text("计划日期和截止日期分开记录；只选对这件事有用的信息。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CaptureChoice("只加入今日清单", "选择后保存，不占具体时段", Icons.Outlined.DateRange, onSaveToday, "task-capture-save-today", enabled)
        CaptureChoice("补充预计时长", "保存到今天后，仍需制定计划安排时段", Icons.Outlined.PlayArrow, onChooseDuration, "task-capture-choose-duration", enabled)
        CaptureChoice("设置截止日期", "最后期限不是计划日期；选定后保存任务", Icons.Outlined.DateRange, onChooseDeadline, "task-capture-choose-deadline", enabled)
    }
}

@Composable
private fun CaptureChoice(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tag: String,
    enabled: Boolean = true,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick).testTag(tag),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun CaptureDurationStep(
    draft: TaskCaptureDraft,
    onChange: (TaskCaptureDraft) -> Unit,
    enabled: Boolean,
) {
    val isCustomDuration = draft.isCustomDuration
    val customDurationText = draft.customDurationText
    val customDurationIsValid = draft.durationIsValid
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("大约需要多久？", style = MaterialTheme.typography.headlineSmall)
        Text("不确定也没关系，后续可以用真实记录慢慢校准。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 60, 90).forEach { minute ->
                FilterChip(
                    enabled = enabled,
                    selected = !isCustomDuration && draft.duration == minute,
                    onClick = { onChange(draft.copy(isCustomDuration = false, duration = minute)) },
                    label = { Text("$minute 分钟") },
                    modifier = Modifier.testTag("task-capture-duration-$minute"),
                )
            }
        }
        FilterChip(
            enabled = enabled,
            selected = !isCustomDuration && draft.duration == 0,
            onClick = { onChange(draft.copy(isCustomDuration = false, duration = 0)) },
            label = { Text("不确定") },
            modifier = Modifier.testTag("task-capture-duration-unknown"),
        )
        OutlinedButton(
            enabled = enabled,
            onClick = { onChange(draft.copy(isCustomDuration = true)) },
            modifier = Modifier.fillMaxWidth().testTag("task-capture-duration-custom"),
        ) { Text(if (isCustomDuration) "正在输入自定义时长" else "输入其他时长") }
        if (isCustomDuration) {
            OutlinedTextField(
                value = customDurationText,
                enabled = enabled,
                onValueChange = { onChange(draft.copy(customDurationText = it)) },
                modifier = Modifier.fillMaxWidth().testTag("task-capture-duration-custom-input"),
                label = { Text("自定义时长（分钟）") },
                placeholder = { Text("1–1440") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = customDurationText.isNotBlank() && !customDurationIsValid,
                supportingText = {
                    if (customDurationText.isBlank()) Text("例如 45 分钟")
                    else if (!customDurationIsValid) Text("请输入 1–1440 的整数分钟；不接受负数、小数或其他字符")
                },
            )
        }
    }
}

@Composable
private fun CaptureDeadlineStep(
    selectedDate: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    enabled: Boolean,
) {
    val today = LocalDate.now()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("什么时候之前完成？", style = MaterialTheme.typography.headlineSmall)
        Text("快捷日期只是选项；也可以自由翻日历或切换为手动输入。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CaptureChoice("今天", "${today.monthValue} 月 ${today.dayOfMonth} 日", Icons.Outlined.DateRange, { onSelect(today) }, "task-capture-deadline-today", enabled)
        CaptureChoice("明天", "${today.plusDays(1).monthValue} 月 ${today.plusDays(1).dayOfMonth} 日", Icons.Outlined.DateRange, { onSelect(today.plusDays(1)) }, "task-capture-deadline-tomorrow", enabled)
        CaptureChoice("一周后", "${today.plusDays(7).monthValue} 月 ${today.plusDays(7).dayOfMonth} 日前", Icons.Outlined.DateRange, { onSelect(today.plusDays(7)) }, "task-capture-deadline-week", enabled)
        CaptureChoice("选择其他日期", "可选择任意日期，也可手动输入", Icons.Outlined.DateRange, { showDatePicker = true }, "task-capture-deadline-custom", enabled)
    }
    if (showDatePicker) {
        TaskDatePickerDialog(
            initialDate = selectedDate,
            onDismiss = { showDatePicker = false },
            onDateSelected = { selected ->
                showDatePicker = false
                onSelect(selected)
            },
        )
    }
}

@Composable
private fun CaptureDetailsStep(
    category: TaskCategory,
    priority: TaskPriority,
    onCategory: (TaskCategory) -> Unit,
    onPriority: (TaskPriority) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("补充一点信息", style = MaterialTheme.typography.headlineSmall)
        Text("这些信息可选，之后随时能改。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("类别", style = MaterialTheme.typography.labelLarge)
        ChipRow(TaskCategory.entries.toList(), category, { it.captureLabel() }, onCategory, enabled)
        Text("优先级", style = MaterialTheme.typography.labelLarge)
        ChipRow(TaskPriority.entries.toList(), priority, { it.captureLabel() }, onPriority, enabled)
    }
}

@Composable
private fun <T> ChipRow(values: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit, enabled: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(enabled = enabled, selected = selected == value, onClick = { onSelected(value) }, label = { Text(label(value)) }) }
    }
}

private fun Task.summaryLabel(): String = when {
    status == TaskStatus.IN_PROGRESS -> "正在进行"
    status == TaskStatus.COMPLETED -> "已完成"
    status == TaskStatus.POSTPONED -> "已延期"
    dueDate?.isBefore(LocalDate.now()) == true && status.isActive -> "已逾期 · ${taskDateLabel(dueDate)}"
    scheduledForDate?.isBefore(LocalDate.now()) == true && status.isActive -> "待重新安排 · ${taskDateLabel(scheduledForDate)}"
    dueDate != null && scheduledForDate != null && totalDurationMinutes != null -> "计划 ${taskDateLabel(scheduledForDate)} · 截止 ${taskDateLabel(dueDate)} · $totalDurationMinutes 分钟"
    dueDate != null && scheduledForDate != null -> "计划 ${taskDateLabel(scheduledForDate)} · 截止 ${taskDateLabel(dueDate)}"
    scheduledForDate != null && totalDurationMinutes != null -> "计划 ${taskDateLabel(scheduledForDate)} · $totalDurationMinutes 分钟"
    scheduledForDate != null -> "计划 ${taskDateLabel(scheduledForDate)}"
    dueDate != null && totalDurationMinutes != null -> "截止 ${taskDateLabel(dueDate)} · $totalDurationMinutes 分钟"
    dueDate != null -> "截止 ${taskDateLabel(dueDate)}"
    totalDurationMinutes != null -> "待安排 · $totalDurationMinutes 分钟"
    else -> "待安排"
}

private fun TaskCategory.captureLabel(): String = when (this) {
    TaskCategory.COURSE -> "课程"
    TaskCategory.EXTRACURRICULAR -> "课外"
    TaskCategory.OFFICE -> "事务"
    TaskCategory.LEISURE -> "生活"
}

private fun TaskPriority.captureLabel(): String = when (this) {
    TaskPriority.REQUIRED -> "必须"
    TaskPriority.HIGH -> "高"
    TaskPriority.MEDIUM -> "中"
    TaskPriority.LOW -> "低"
}
