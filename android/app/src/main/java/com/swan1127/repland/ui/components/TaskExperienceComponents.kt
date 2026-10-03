package com.swan1127.repland.ui.components

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
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
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
import com.swan1127.repland.domain.model.TaskName
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import java.time.LocalDate

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

private enum class CaptureStage { CAPTURE, INTENT, DURATION, DEADLINE, DETAILS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCaptureSheet(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (TaskDraft) -> Unit,
    onVoice: (() -> Unit)? = null,
) {
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }
    var stage by rememberSaveable { mutableStateOf(CaptureStage.CAPTURE) }
    var dueDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var scheduledForDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var duration by rememberSaveable { mutableIntStateOf(0) }
    var category by rememberSaveable { mutableStateOf(TaskCategory.COURSE) }
    var priority by rememberSaveable { mutableStateOf(TaskPriority.MEDIUM) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun saveDraft() {
        val body = text.trim()
        if (body.isBlank()) return
        onSave(
            TaskDraft(
                displayName = TaskName.fromDescription(body),
                description = body,
                category = category,
                userPriority = priority,
                estimatedDays = 1,
                totalDurationMinutes = duration.takeIf { it > 0 },
                dueDate = dueDate,
                scheduledForDate = scheduledForDate,
            ),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            onVoice?.let { action ->
                TextButton(onClick = action, modifier = Modifier.testTag("voice-capture")) { Text("语音输入") }
            }
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (fadeIn(tween(160)) + slideInHorizontally(tween(180)) { it / 12 }) togetherWith
                        (fadeOut(tween(100)) + slideOutHorizontally(tween(140)) { -it / 16 })
                },
                label = "task-capture-step",
            ) { currentStage ->
                when (currentStage) {
                    CaptureStage.CAPTURE -> CaptureInputStep(
                        text = text,
                        onTextChange = { text = it },
                        onSaveInbox = { saveDraft() },
                        onArrangeToday = { if (text.isNotBlank()) stage = CaptureStage.INTENT },
                        onMore = { if (text.isNotBlank()) stage = CaptureStage.DETAILS },
                    )

                    CaptureStage.INTENT -> CaptureIntentStep(
                        onSaveToday = { scheduledForDate = LocalDate.now(); saveDraft() },
                        onChooseDuration = { scheduledForDate = LocalDate.now(); stage = CaptureStage.DURATION },
                        onChooseDeadline = { stage = CaptureStage.DEADLINE },
                        onBack = { stage = CaptureStage.CAPTURE },
                    )

                    CaptureStage.DURATION -> CaptureDurationStep(
                        selectedDuration = duration,
                        onSelect = { duration = it },
                        onSave = { selectedDuration -> duration = selectedDuration; saveDraft() },
                        onBack = { stage = CaptureStage.INTENT },
                    )

                    CaptureStage.DEADLINE -> CaptureDeadlineStep(
                        selectedDate = dueDate,
                        onSelect = { selected -> dueDate = selected; saveDraft() },
                        onBack = { stage = CaptureStage.INTENT },
                    )

                    CaptureStage.DETAILS -> CaptureDetailsStep(
                        category = category,
                        priority = priority,
                        onCategory = { category = it },
                        onPriority = { priority = it },
                        onSave = { saveDraft() },
                        onBack = { stage = CaptureStage.CAPTURE },
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureInputStep(
    text: String,
    onTextChange: (String) -> Unit,
    onSaveInbox: () -> Unit,
    onArrangeToday: () -> Unit,
    onMore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("接下来要做什么？", style = MaterialTheme.typography.headlineSmall)
        Text("先记下，再决定什么时候做。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().testTag("task-capture-input"),
            placeholder = { Text("写下任务……") },
            minLines = 2,
            maxLines = 4,
            trailingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onSaveInbox,
                enabled = text.isNotBlank(),
                modifier = Modifier.weight(1f).testTag("task-capture-save-inbox"),
            ) { Text("先保存") }
            Button(
                onClick = onArrangeToday,
                enabled = text.isNotBlank(),
                modifier = Modifier.weight(1f).testTag("task-capture-arrange-today"),
            ) { Text("安排今天") }
        }
        TextButton(onClick = onMore, enabled = text.isNotBlank(), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("补充分类与优先级")
        }
    }
}

@Composable
private fun CaptureIntentStep(
    onSaveToday: () -> Unit,
    onChooseDuration: () -> Unit,
    onChooseDeadline: () -> Unit,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("安排到今天", style = MaterialTheme.typography.headlineSmall)
        Text("计划日期和截止日期分开记录；只选对这件事有用的信息。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CaptureChoice("只安排到今天", "先进入今日清单，不占具体时段", Icons.Outlined.DateRange, onSaveToday, "task-capture-save-today")
        CaptureChoice("安排具体时段", "先选择预计时长，再由计划页安排", Icons.Outlined.PlayArrow, onChooseDuration, "task-capture-choose-duration")
        CaptureChoice("设置截止时间", "适合有明确最后期限的任务", Icons.Outlined.DateRange, onChooseDeadline, "task-capture-choose-deadline")
        TextButton(onClick = onBack) { Text("返回修改任务") }
    }
}

@Composable
private fun CaptureChoice(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tag: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).testTag(tag),
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
    selectedDuration: Int,
    onSelect: (Int) -> Unit,
    onSave: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var isCustomDuration by rememberSaveable { mutableStateOf(false) }
    var customDurationText by rememberSaveable { mutableStateOf("") }
    val customDuration = customDurationText.toIntOrNull()
    val customDurationIsValid = customDuration != null && customDuration in 1..1_440
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("大约需要多久？", style = MaterialTheme.typography.headlineSmall)
        Text("不确定也没关系，后续可以用真实记录慢慢校准。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 60, 90).forEach { minute ->
                FilterChip(
                    selected = !isCustomDuration && selectedDuration == minute,
                    onClick = { isCustomDuration = false; onSelect(minute) },
                    label = { Text("$minute 分钟") },
                    modifier = Modifier.testTag("task-capture-duration-$minute"),
                )
            }
        }
        FilterChip(
            selected = !isCustomDuration && selectedDuration == 0,
            onClick = { isCustomDuration = false; onSelect(0) },
            label = { Text("不确定") },
        )
        OutlinedButton(
            onClick = { isCustomDuration = true },
            modifier = Modifier.fillMaxWidth().testTag("task-capture-duration-custom"),
        ) { Text(if (isCustomDuration) "正在输入自定义时长" else "输入其他时长") }
        if (isCustomDuration) {
            OutlinedTextField(
                value = customDurationText,
                onValueChange = { customDurationText = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth().testTag("task-capture-duration-custom-input"),
                label = { Text("自定义时长（分钟）") },
                placeholder = { Text("1–1440") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = customDurationText.isNotBlank() && !customDurationIsValid,
                supportingText = {
                    if (customDurationText.isBlank()) Text("例如 45 分钟")
                    else if (!customDurationIsValid) Text("请输入 1–1440 之间的分钟数")
                },
            )
        }
        Button(
            onClick = { onSave(if (isCustomDuration) requireNotNull(customDuration) else selectedDuration) },
            enabled = !isCustomDuration || customDurationIsValid,
            modifier = Modifier.fillMaxWidth().testTag("task-capture-save-duration"),
        ) { Text("保存到今天") }
        TextButton(onClick = onBack) { Text("返回") }
    }
}

@Composable
private fun CaptureDeadlineStep(
    selectedDate: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    val today = LocalDate.now()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("什么时候之前完成？", style = MaterialTheme.typography.headlineSmall)
        Text("快捷日期只是选项；也可以自由翻日历或切换为手动输入。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CaptureChoice("今天", "${today.monthValue} 月 ${today.dayOfMonth} 日", Icons.Outlined.DateRange, { onSelect(today) }, "task-capture-deadline-today")
        CaptureChoice("明天", "${today.plusDays(1).monthValue} 月 ${today.plusDays(1).dayOfMonth} 日", Icons.Outlined.DateRange, { onSelect(today.plusDays(1)) }, "task-capture-deadline-tomorrow")
        CaptureChoice("一周后", "${today.plusDays(7).monthValue} 月 ${today.plusDays(7).dayOfMonth} 日前", Icons.Outlined.DateRange, { onSelect(today.plusDays(7)) }, "task-capture-deadline-week")
        CaptureChoice("选择其他日期", "可选择任意日期，也可手动输入", Icons.Outlined.DateRange, { showDatePicker = true }, "task-capture-deadline-custom")
        TextButton(onClick = onBack) { Text("返回") }
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
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("补充一点信息", style = MaterialTheme.typography.headlineSmall)
        Text("这些信息可选，之后随时能改。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("类别", style = MaterialTheme.typography.labelLarge)
        ChipRow(TaskCategory.entries.toList(), category, { it.captureLabel() }, onCategory)
        Text("优先级", style = MaterialTheme.typography.labelLarge)
        ChipRow(TaskPriority.entries.toList(), priority, { it.captureLabel() }, onPriority)
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().testTag("task-capture-save-details")) { Text("保存任务") }
        TextButton(onClick = onBack) { Text("返回") }
    }
}

@Composable
private fun <T> ChipRow(values: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected = selected == value, onClick = { onSelected(value) }, label = { Text(label(value)) }) }
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
