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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.EngagementMode
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.ScheduleTimeline
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TimeBlockValidator
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.RhythmTrack
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val WeekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.CHINA)
private val WeekDateFormatter = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)
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
    tracks: List<RhythmTrack> = emptyList(),
) {
    val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    Text("本周节奏", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    Text("${weekStart.format(WeekDateFormatter)}–${days.last().format(WeekDateFormatter)} · 用一周看负荷、截止与留白，而不是重复日视图。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    val weekEntries = days.flatMap { day -> ScheduleTimeline.entries(day, weeklyBlocks, dateOverrides, planSegments, tasks, semesterFirstWeekMonday) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeekMetric("${weekEntries.sumOf { it.endMinute - it.startMinute } / 60}h", "已占用", Modifier.weight(1f))
            WeekMetric("${weekEntries.count { it.kind == TimelineKind.TASK }}", "任务段", Modifier.weight(1f))
            WeekMetric("${weekEntries.count { it.kind == TimelineKind.COURSE }}", "课程", Modifier.weight(1f))
        }
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
                tracks = tracks,
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
    tracks: List<RhythmTrack>,
) {
    Surface(
        modifier = Modifier.width(152.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(date.format(WeekdayFormatter), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date.format(WeekDateFormatter), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val taskCount = entries.count { it.kind == TimelineKind.TASK }
            Text("${entries.size} 项 · $taskCount 个任务", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entries.isEmpty()) {
                Text("留白", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                entries.take(if (mode == EngagementMode.EXECUTOR) 3 else 5).forEach { entry ->
                    CompactEvent(entry, onClick = { onOpenEntry(entry.id) }, trackName = tracks.firstOrNull { it.id == entry.trackId }?.name)
                }
                if (entries.size > 5) Text("还有 ${entries.size - 5} 项", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
private fun CompactEvent(entry: TimelineEntry, onClick: () -> Unit, trackName: String? = null) {
    val accent = eventAccent(entry.kind)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = accent.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            trackName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(
                "${TimeBlockValidator.formatTime(entry.startMinute)}–${TimeBlockValidator.formatTime(entry.endMinute)}",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
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
    tracks: List<RhythmTrack> = emptyList(),
) {
    var projection by rememberSaveable { androidx.compose.runtime.mutableStateOf(MonthProjection.LOAD) }
    val month = YearMonth.from(date)
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = (0 until 42).map { first.plusDays(it.toLong()) }
    Text("月度节奏", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    Text("同一个月用不同问题阅读：哪里拥挤、推进到哪、时间给了什么、哪些还需处理。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MonthProjection.entries.forEach { option ->
            FilterChip(selected = projection == option, onClick = { projection = option }, label = { Text(option.label) })
        }
    }
    Spacer(Modifier.height(10.dp))
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
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
            Text("${date.monthValue}月${date.dayOfMonth}日 · ${selectedEntries.size} 项", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            selectedEntries.take(4).forEach { entry -> CompactEvent(entry, onClick = { onOpenEntry(entry.id) }, trackName = tracks.firstOrNull { it.id == entry.trackId }?.name) }
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
        modifier = Modifier.weight(1f).height(68.dp).clickable(onClick = onClick),
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
            val fraction = (entries.sumOf { it.endMinute - it.startMinute } / 480f).coerceIn(0f, 1f)
            Spacer(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f + fraction * 0.72f), MaterialTheme.shapes.extraSmall))
            Text("${(fraction * 8).toInt()} / 8", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MonthProjection.PROGRESS -> {
            val taskEntries = entries.filter { it.kind == TimelineKind.TASK }
            val completed = taskEntries.count { it.taskId?.let(taskMap::get)?.status == com.swan1127.repland.domain.model.TaskStatus.COMPLETED }
            val fraction = if (taskEntries.isEmpty()) 0f else completed.toFloat() / taskEntries.size
            Spacer(Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraSmall))
            Spacer(Modifier.fillMaxWidth(fraction).height(10.dp).background(Color(0xFF2F7C67), MaterialTheme.shapes.extraSmall))
            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MonthProjection.CATEGORY -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            entries.map(TimelineEntry::kind).distinct().take(3).forEach { kind -> Spacer(Modifier.weight(1f).height(8.dp).background(eventAccent(kind), MaterialTheme.shapes.extraSmall)) }
        }
        MonthProjection.STATUS -> {
            val pending = entries.count { it.kind == TimelineKind.TASK && it.taskId?.let(taskMap::get)?.status?.isActive == true }
            val complete = entries.count { it.kind == TimelineKind.TASK && it.taskId?.let(taskMap::get)?.status == com.swan1127.repland.domain.model.TaskStatus.COMPLETED }
            Text(if (pending > 0) "$pending 待处理" else if (complete > 0) "$complete 完成" else "留白", style = MaterialTheme.typography.labelSmall, color = if (pending > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun eventAccent(kind: TimelineKind): Color = when (kind) {
    TimelineKind.COURSE -> Color(0xFF4F65A6)
    TimelineKind.REST -> Color(0xFF7C7C9C)
    TimelineKind.COMMITMENT -> Color(0xFFA66B42)
    TimelineKind.TASK -> Color(0xFF2F7C67)
}
