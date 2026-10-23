package com.swan1127.repland.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swan1127.repland.R
import com.swan1127.repland.domain.model.SubTasks
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskName
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.ui.theme.ReplandAccent
import com.swan1127.repland.ui.theme.replandAccent
import java.time.LocalDate
import java.util.Locale

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
    /**
     * `null` when the list is not in selection mode, otherwise whether this row is ticked.
     *
     * One nullable field rather than a `selecting` flag beside a `selected` flag: that pair has an
     * impossible combination (`selecting = false, selected = true`), and a shape that cannot express
     * it cannot be got wrong. It is also what makes the row's own behaviour total — in selection
     * mode a tap ticks, out of it a tap opens, and there is no third state to decide.
     */
    selected: Boolean? = null,
    onToggleSelect: () -> Unit = {},
) {
    val inSelection = selected != null
    val accent = replandAccent(task.id.hashCode())
    val statusColor = when {
        task.status == TaskStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
        task.dueDate?.isBefore(LocalDate.now()) == true && task.status.isActive -> MaterialTheme.colorScheme.error
        emphasis == TaskRowEmphasis.ATTENTION -> MaterialTheme.colorScheme.error
        else -> accent.ink
    }
    val summary = task.summaryLabel(
        today = LocalDate.now(),
        labels = rememberTaskDateLabels(),
        texts = rememberTaskSummaryTexts(),
        locale = currentLocale(),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(accent.surface)
            .then(
                if (selected == true) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            )
            .clickable(role = Role.Button, onClick = if (inSelection) onToggleSelect else onOpen)
            .testTag("task-card-${task.displayName}")
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (inSelection) {
            // A tick, not the play button: in this mode the leading control answers "is this row in
            // the batch", and leaving 开始 there would offer a single-row action in the middle of a
            // multi-row gesture.
            Checkbox(
                checked = selected == true,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("task-select-${task.id}"),
            )
        } else {
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
                TaskMetaLabel(task.category.captureLabel(), accent)
                TaskMetaLabel(task.userPriority.captureLabel(), accent)
                task.totalDurationMinutes?.let { TaskMetaLabel("${it} 分钟", accent) }
                if (task.subtasks.isNotEmpty()) {
                    val progress = SubTasks.progress(task)
                    TaskMetaLabel(stringResource(R.string.subtask_progress_label, progress.done, progress.total), accent)
                }
            }
            val progress = when (task.status) {
                TaskStatus.COMPLETED -> 1f
                else -> (task.progressPercent ?: 0).coerceIn(0, 100) / 100f
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                color = statusColor,
                trackColor = accent.ink.copy(alpha = .12f),
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
private fun TaskMetaLabel(label: String, accent: ReplandAccent) {
    Surface(color = accent.ink.copy(alpha = .10f), shape = RoundedCornerShape(6.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = accent.ink,
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
            listOf(15, 30, 60, 90).forEachIndexed { index, minute ->
                DesignFilterChip(
                    selected = !isCustomDuration && selectedDuration == minute,
                    onClick = { isCustomDuration = false; onSelect(minute) },
                    label = { Text("$minute 分钟") },
                    modifier = Modifier.testTag("task-capture-duration-$minute"),
                    accentIndex = index,
                )
            }
        }
        DesignFilterChip(
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
    // One formatter for the whole step: the three shortcut rows must agree with each other and
    // with the picker, and a shared instance is the only way that is guaranteed rather than
    // hoped for.
    val monthDay = rememberDateFormatter(R.string.date_pattern_month_day)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.task_capture_deadline_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.task_capture_deadline_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CaptureChoice(stringResource(R.string.task_date_today), monthDay.format(today), Icons.Outlined.DateRange, { onSelect(today) }, "task-capture-deadline-today")
        CaptureChoice(stringResource(R.string.task_date_tomorrow), monthDay.format(today.plusDays(1)), Icons.Outlined.DateRange, { onSelect(today.plusDays(1)) }, "task-capture-deadline-tomorrow")
        CaptureChoice(stringResource(R.string.task_capture_deadline_week), stringResource(R.string.task_capture_deadline_week_hint, monthDay.format(today.plusDays(7))), Icons.Outlined.DateRange, { onSelect(today.plusDays(7)) }, "task-capture-deadline-week")
        CaptureChoice(stringResource(R.string.task_capture_deadline_custom), stringResource(R.string.task_capture_deadline_custom_hint), Icons.Outlined.DateRange, { showDatePicker = true }, "task-capture-deadline-custom")
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
        values.forEachIndexed { index, value ->
            DesignFilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(label(value)) },
                accentIndex = index,
            )
        }
    }
}

/**
 * The words a task card's one-line summary is assembled from.
 *
 * A value rather than a `Context` for the same reason as [TaskDateLabels]: the branch logic below
 * is the part worth testing, and it is only reachable from a plain JVM test if the words arrive as
 * data. It also makes the format-string arguments checkable — `%1$s` here must line up with the
 * `String.format` call, and `values-en/` must declare the same specifiers, which
 * [com.swan1127.repland.i18n.I18nResourceTest] enforces.
 */
internal data class TaskSummaryTexts(
    val inProgress: String,
    val completed: String,
    val postponed: String,
    /** `%1$s` is the overdue date. */
    val overdue: String,
    /** `%1$s` is the date the task was meant to run. */
    val needsRescheduling: String,
    /** `%1$s` planned date, `%2$s` due date, `%3$d` minutes. */
    val plannedDueMinutes: String,
    /** `%1$s` planned date, `%2$s` due date. */
    val plannedDue: String,
    /** `%1$s` planned date, `%2$d` minutes. */
    val plannedMinutes: String,
    /** `%1$s` planned date. */
    val planned: String,
    /** `%1$s` due date, `%2$d` minutes. */
    val dueMinutes: String,
    /** `%1$s` due date. */
    val due: String,
    /** `%1$d` minutes. */
    val unscheduledMinutes: String,
    val unscheduled: String,
)

@Composable
private fun rememberTaskSummaryTexts(): TaskSummaryTexts = TaskSummaryTexts(
    inProgress = stringResource(R.string.task_summary_in_progress),
    completed = stringResource(R.string.task_summary_completed),
    postponed = stringResource(R.string.task_summary_postponed),
    overdue = stringResource(R.string.task_summary_overdue),
    needsRescheduling = stringResource(R.string.task_summary_needs_rescheduling),
    plannedDueMinutes = stringResource(R.string.task_summary_planned_due_minutes),
    plannedDue = stringResource(R.string.task_summary_planned_due),
    plannedMinutes = stringResource(R.string.task_summary_planned_minutes),
    planned = stringResource(R.string.task_summary_planned),
    dueMinutes = stringResource(R.string.task_summary_due_minutes),
    due = stringResource(R.string.task_summary_due),
    unscheduledMinutes = stringResource(R.string.task_summary_unscheduled_minutes),
    unscheduled = stringResource(R.string.task_summary_unscheduled),
)

/**
 * One line describing where a task stands, for the row users scan all day.
 *
 * **The order of the branches is the specification**, and two parts of it are easy to get wrong:
 *
 *   - the three *status* words win outright, so a completed task never reads as overdue;
 *   - among the rest, an **overdue due date outranks a past planned date** — a missed deadline is
 *     the more urgent fact, and the reschedule hint is for the case where only the plan slipped.
 *
 * Every branch that mentions a date renders it through [taskDateLabel] with the *same* [today], so
 * a row can never disagree with itself about which day it is (the previous version called
 * `LocalDate.now()` once per branch, which would have crossed midnight between two branches in the
 * same expression).
 *
 * Pure: [today], the words and the locale are all parameters, so this is driven directly by
 * `TaskSummaryTest` with no clock and no Android runtime.
 */
internal fun Task.summaryLabel(
    today: LocalDate,
    labels: TaskDateLabels,
    texts: TaskSummaryTexts,
    locale: Locale,
): String {
    fun label(date: LocalDate): String = taskDateLabel(date, today, labels, locale)
    return when {
        status == TaskStatus.IN_PROGRESS -> texts.inProgress
        status == TaskStatus.COMPLETED -> texts.completed
        status == TaskStatus.POSTPONED -> texts.postponed
        dueDate?.isBefore(today) == true && status.isActive -> String.format(locale, texts.overdue, label(dueDate))
        scheduledForDate?.isBefore(today) == true && status.isActive -> String.format(locale, texts.needsRescheduling, label(scheduledForDate))
        dueDate != null && scheduledForDate != null && totalDurationMinutes != null -> String.format(locale, texts.plannedDueMinutes, label(scheduledForDate), label(dueDate), totalDurationMinutes)
        dueDate != null && scheduledForDate != null -> String.format(locale, texts.plannedDue, label(scheduledForDate), label(dueDate))
        scheduledForDate != null && totalDurationMinutes != null -> String.format(locale, texts.plannedMinutes, label(scheduledForDate), totalDurationMinutes)
        scheduledForDate != null -> String.format(locale, texts.planned, label(scheduledForDate))
        dueDate != null && totalDurationMinutes != null -> String.format(locale, texts.dueMinutes, label(dueDate), totalDurationMinutes)
        dueDate != null -> String.format(locale, texts.due, label(dueDate))
        totalDurationMinutes != null -> String.format(locale, texts.unscheduledMinutes, totalDurationMinutes)
        else -> texts.unscheduled
    }
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
