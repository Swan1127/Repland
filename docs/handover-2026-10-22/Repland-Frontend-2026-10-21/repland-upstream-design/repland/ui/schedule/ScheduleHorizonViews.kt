package com.swan1127.repland.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import com.swan1127.repland.ui.components.DesignFilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swan1127.repland.R
import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.EngagementMode
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.ScheduleTimeline
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TimeBlockValidator
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.WeeklyBlockConflicts
import com.swan1127.repland.ui.components.currentLocale
import com.swan1127.repland.ui.components.rememberDateFormatter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

private enum class MonthProjection(val label: String) { LOAD("占用"), PROGRESS("完成"), CATEGORY("类别"), STATUS("状态") }

@Composable
fun WeekScheduleView(
    date: LocalDate,
    weeklyBlocks: List<WeeklyTimeBlock>,
    dateOverrides: List<DateOverride>,
    planSegments: List<PlannedSegment>,
    tasks: List<Task>,
    semesterFirstWeekMonday: LocalDate?,
    mode: EngagementMode,
    onOpenEntry: (String) -> Unit,
    onOpenBlock: (WeeklyTimeBlock) -> Unit,
) {
    val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val weekDateFormatter = rememberDateFormatter(R.string.date_pattern_numeric_month_day)
    Text("本周节奏", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text("${weekDateFormatter.format(weekStart)}–${weekDateFormatter.format(days.last())}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    val weekEntries = days.flatMap { day -> ScheduleTimeline.entries(day, weeklyBlocks, dateOverrides, planSegments, tasks, semesterFirstWeekMonday) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeekMetric(scheduledDurationLabel(scheduledClockMinutes(weekEntries)), "安排覆盖", Modifier.weight(1f))
            WeekMetric("${weekEntries.count { it.kind == TimelineKind.TASK }}", "任务段", Modifier.weight(1f))
            WeekMetric("${weekEntries.count { it.kind == TimelineKind.COURSE }}", "课程", Modifier.weight(1f))
        }
    }
    Text("重叠时段只计一次；这是安排覆盖时间，不代表实际耗时或可用容量。",
        modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    // Computed from the whole week rather than from `weekEntries`: those have already been resolved
    // into lanes, which is exactly the information the rule needs and the renderer has discarded.
    val conflicts = remember(weeklyBlocks) { WeeklyBlockConflicts.find(weeklyBlocks) }
    if (conflicts.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        WeeklyConflictBanner(conflicts = conflicts, onOpenBlock = onOpenBlock)
    }
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        days.forEach { day ->
            val entries = ScheduleTimeline.entries(
                day, weeklyBlocks, dateOverrides, planSegments, tasks, semesterFirstWeekMonday,
            )
            WeekDayColumn(
                date = day,
                entries = entries,
                selected = day == date,
                mode = mode,
                onOpenEntry = onOpenEntry,
            )
        }
    }
}

@Composable
private fun WeekDayColumn(
    date: LocalDate,
    entries: List<TimelineEntry>,
    selected: Boolean,
    mode: EngagementMode,
    onOpenEntry: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.width(152.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(rememberDateFormatter(R.string.date_pattern_weekday_short).format(date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(rememberDateFormatter(R.string.date_pattern_numeric_month_day).format(date), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val taskCount = entries.count { it.kind == TimelineKind.TASK }
            Text("${entries.size} 项 · $taskCount 个任务", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entries.isEmpty()) {
                Text("留白", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val visibleCount = if (mode == EngagementMode.EXECUTOR) 3 else 5
                entries.take(visibleCount).forEach { entry ->
                    CompactEvent(entry, onClick = { onOpenEntry(entry.id) })
                }
                if (entries.size > visibleCount) Text("还有 ${entries.size - visibleCount} 项", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun WeekMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompactEvent(entry: TimelineEntry, onClick: () -> Unit) {
    val accent = scheduleAccent(entry.kind)
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick),
        color = accent.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${TimeBlockValidator.formatTime(entry.startMinute)}–${TimeBlockValidator.formatTime(entry.endMinute)}",
                style = MaterialTheme.typography.labelSmall,
                color = accent.ink,
            )
        }
    }
}

@Composable
fun MonthScheduleView(
    date: LocalDate,
    weeklyBlocks: List<WeeklyTimeBlock>,
    dateOverrides: List<DateOverride>,
    planSegments: List<PlannedSegment>,
    tasks: List<Task>,
    semesterFirstWeekMonday: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    var projection by rememberSaveable { androidx.compose.runtime.mutableStateOf(MonthProjection.LOAD) }
    val month = YearMonth.from(date)
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = (0 until 42).map { first.plusDays(it.toLong()) }
    Text("月度节奏", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text("查看安排覆盖、任务完成、类别与待处理事项。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MonthProjection.entries.forEachIndexed { index, option ->
            DesignFilterChip(
                selected = projection == option,
                onClick = { projection = option },
                label = { Text(option.label) },
                accentIndex = index,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    // Narrow weekday names come from the locale rather than a hard-coded list: 一..日 in Chinese,
    // M T W T F S S in English. A literal list here would be a second, unguarded copy of the
    // calendar's own vocabulary.
    val weekdays = DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, currentLocale()) }
    Row(Modifier.fillMaxWidth()) {
        weekdays.forEach { Text(it, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        days.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { day ->
                    MonthDayCell(
                        date = day,
                        inMonth = day.month == month.month,
                        selected = day == date,
                        entries = ScheduleTimeline.entries(day, weeklyBlocks, dateOverrides, planSegments, tasks, semesterFirstWeekMonday),
                        tasks = tasks,
                        projection = projection,
                        onClick = { onSelectDate(day) },
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    val selectedEntries = ScheduleTimeline.entries(date, weeklyBlocks, dateOverrides, planSegments, tasks, semesterFirstWeekMonday)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${rememberDateFormatter(R.string.date_pattern_month_day).format(date)} · ${selectedEntries.size} 项", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            selectedEntries.take(4).forEach { CompactEvent(it, onClick = { onOpenEntry(it.id) }) }
            if (selectedEntries.isEmpty()) Text("这一天还没有安排，留白也是一种安排。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RowScope.MonthDayCell(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    entries: List<TimelineEntry>,
    tasks: List<Task>,
    projection: MonthProjection,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.weight(1f).heightIn(min = 68.dp).clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (inMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )
            MonthCellSignal(entries, tasks, projection)
        }
    }
}

@Composable
private fun MonthCellSignal(entries: List<TimelineEntry>, tasks: List<Task>, projection: MonthProjection) {
    val taskMap = tasks.associateBy(Task::id)
    when (projection) {
        MonthProjection.LOAD -> {
            val occupiedMinutes = scheduledClockMinutes(entries)
            val fraction = (occupiedMinutes / 1_440f).coerceIn(0f, 1f)
            Spacer(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f + fraction * 0.72f), MaterialTheme.shapes.extraSmall))
            Text(if (occupiedMinutes == 0) "—" else "${occupiedMinutes / 60}h${if (occupiedMinutes % 60 > 0) "${occupiedMinutes % 60}" else ""}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MonthProjection.PROGRESS -> {
            val taskEntries = entries.filter { it.kind == TimelineKind.TASK }
            val completed = taskEntries.count { it.taskId?.let(taskMap::get)?.status == com.swan1127.repland.domain.model.TaskStatus.COMPLETED }
            val fraction = if (taskEntries.isEmpty()) 0f else completed.toFloat() / taskEntries.size
            Spacer(Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraSmall))
            Spacer(Modifier.fillMaxWidth(fraction).height(10.dp).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall))
            Text(if (taskEntries.isEmpty()) "—" else "${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MonthProjection.CATEGORY -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            entries.map(TimelineEntry::kind).distinct().take(3).forEach { kind -> Spacer(Modifier.weight(1f).height(8.dp).background(scheduleAccent(kind).ink, MaterialTheme.shapes.extraSmall)) }
        }
        MonthProjection.STATUS -> {
            val pending = entries.count { it.kind == TimelineKind.TASK && it.taskId?.let(taskMap::get)?.status?.isActive == true }
            val complete = entries.count { it.kind == TimelineKind.TASK && it.taskId?.let(taskMap::get)?.status == com.swan1127.repland.domain.model.TaskStatus.COMPLETED }
            Text(if (pending > 0) "$pending 待处理" else if (complete > 0) "$complete 完成" else "留白", style = MaterialTheme.typography.labelSmall, color = if (pending > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

