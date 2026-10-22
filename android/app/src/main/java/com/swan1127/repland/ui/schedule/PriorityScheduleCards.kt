package com.swan1127.repland.ui.schedule

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.components.DesignButton
import com.swan1127.repland.ui.components.DesignClickableSurface
import com.swan1127.repland.ui.components.DesignIcons
import com.swan1127.repland.ui.theme.LocalReducedMotion
import com.swan1127.repland.ui.theme.MotionDisclosure
import com.swan1127.repland.ui.theme.ReplandMotion
import com.swan1127.repland.ui.theme.replandAccent
import com.swan1127.repland.ui.theme.LocalReplandVisuals
import java.time.LocalDateTime
import java.util.Locale

private fun paletteIndex(entry: TimelineEntry): Int = when (entry.kind) {
    TimelineKind.COURSE -> 0
    TimelineKind.TASK -> 1
    TimelineKind.COMMITMENT -> 2
    TimelineKind.REST -> 3
}
fun scheduleMinuteLabel(minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

@Composable
fun ScheduleCreateCard(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val visuals = LocalReplandVisuals.current
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DesignClickableSurface(onClick = onAdd, modifier = Modifier.size(56.dp).testTag("schedule-create-plus"),
                    shape = CircleShape, color = visuals.mainAction, pressedScale = .98f) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(DesignIcons.Plus, "新增日程", Modifier.size(26.dp), tint = visuals.onMainAction)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("新增「日程」", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("先记下事项，再安排时间", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DesignButton(onClick = onAdd, modifier = Modifier.align(Alignment.End).testTag("schedule-create-task")) { Text("新增") }
        }
    }
}

@Composable
fun PriorityScheduleCards(
    entries: List<TimelineEntry>,
    priorities: Map<String, TaskPriority>,
    completedTaskIds: Set<String>,
    cancelledTaskIds: Set<String>,
    detailed: Boolean,
    now: LocalDateTime,
    onClick: (TimelineEntry) -> Unit,
) {
    val completed = entries.filter { it.taskId in completedTaskIds }
    val cancelled = entries.filter { it.taskId in cancelledTaskIds }
    val ended = entries.filter { it.taskId !in completedTaskIds && it.taskId !in cancelledTaskIds &&
        ((it.kind != TimelineKind.TASK && !now.isBefore(it.end)) || it.taskClosed) }
    val archivedIds = (completed + cancelled + ended).map(TimelineEntry::id).toSet()
    val active = entries.filterNot { it.id in archivedIds }
    val reduced = LocalReducedMotion.current
    Column(Modifier.fillMaxWidth().animateContentSize(tween(if (reduced) 0 else ReplandMotion.Expand)),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (active.isNotEmpty()) {
            if (detailed) {
                val groups = PriorityScheduleProjection.ordered(active, priorities).groupBy { PriorityScheduleProjection.rank(it, priorities) }
                groups.forEach { (rank, group) ->
                    val title = if (rank == 110) "固定安排" else "${PriorityScheduleProjection.label(group.first(), priorities)}优先级"
                    ScheduleGroup(title, group.size, collapsible = false) {
                        group.forEachIndexed { index, entry ->
                            DetailedScheduleRow(entry, priorities, onClick, muted = false)
                            if (index != group.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(horizontal = 18.dp))
                        }
                    }
                }
            } else {
                ScheduleGroup("时段总计", active.size, collapsible = false) {
                    val slices = remember(active, priorities) { PriorityScheduleProjection.slices(active, priorities) }
                    slices.forEachIndexed { index, slice ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ScheduleTimeRail(slice.startMinute, slice.endMinute, MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).testTag("priority-slot-${slice.startMinute}"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                slice.entries.forEachIndexed { order, entry -> SummaryScheduleChip(entry, priorities, slice.startMinute, order, onClick) }
                            }
                        }
                        if (index != slices.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(horizontal = 18.dp))
                    }
                }
            }
        }
        listOf("已完成" to completed, "已取消" to cancelled, "已结束" to ended).forEach { (title, group) ->
            if (group.isNotEmpty()) ScheduleGroup(title, group.size, collapsible = true) {
                group.sortedBy(TimelineEntry::startMinute).forEachIndexed { index, entry ->
                    DetailedScheduleRow(entry, priorities, onClick, muted = true)
                    if (index != group.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(horizontal = 18.dp))
                }
            }
        }
    }
}

@Composable
private fun ScheduleGroup(title: String, count: Int, collapsible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(true) }
    val reduced = LocalReducedMotion.current
    val arrow by animateFloatAsState(if (expanded) 180f else 0f,
        tween(if (reduced) 0 else ReplandMotion.Collapse), label = "group expansion")
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column {
            if (collapsible) DesignClickableSurface(onClick = { expanded = !expanded }, color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("schedule-group-$title")
                    .semantics { stateDescription = if (expanded) "已展开" else "已收起" }) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$title（$count）", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.rotate(arrow), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("$count 项", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MotionDisclosure(!collapsible || expanded, content = content)
        }
    }
}

@Composable
private fun ScheduleTimeRail(start: Int, end: Int, color: Color) {
    val width = (54f * LocalDensity.current.fontScale).coerceIn(54f, 110f).dp
    Row(Modifier.width(width), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(scheduleMinuteLabel(start), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            Text(scheduleMinuteLabel(end), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        }
        Box(Modifier.width(3.dp).height(56.dp).background(color, RoundedCornerShape(3.dp)))
    }
}

@Composable
private fun SummaryScheduleChip(entry: TimelineEntry, priorities: Map<String, TaskPriority>, start: Int, order: Int, onClick: (TimelineEntry) -> Unit) {
    val index = paletteIndex(entry)
    val accent = replandAccent(index)
    val card = MaterialTheme.colorScheme.surfaceContainerLow
    val rank = PriorityScheduleProjection.rank(entry, priorities)
    val fading = (when { rank >= 80 -> .08f; rank >= 50 -> .30f; else -> .52f } + order.coerceAtMost(4) * .06f).coerceAtMost(.76f)
    val background = lerp(accent.surface, card, fading)
    val railAlpha = when { rank >= 80 -> 1f; rank >= 50 -> .65f; else -> .35f }
    DesignClickableSurface(onClick = { onClick(entry) }, color = background, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.width((136f * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)).dp)
            .heightIn(min = 72.dp).testTag("summary-$start-${entry.id}")
            .semantics { contentDescription = "${entry.title}，${PriorityScheduleProjection.label(entry, priorities)}，${scheduleMinuteLabel(entry.startMinute)}至${scheduleMinuteLabel(entry.endMinute)}" }) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(3.dp).height(40.dp).background(accent.ink.copy(alpha = railAlpha), RoundedCornerShape(3.dp)))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = if (rank >= 80) FontWeight.SemiBold else FontWeight.Medium)
                Text(PriorityScheduleProjection.label(entry, priorities), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailedScheduleRow(entry: TimelineEntry, priorities: Map<String, TaskPriority>, onClick: (TimelineEntry) -> Unit, muted: Boolean) {
    val index = paletteIndex(entry)
    val palette = replandAccent(index)
    val rank = PriorityScheduleProjection.rank(entry, priorities)
    val accent = palette.ink
    val rail = if (muted) MaterialTheme.colorScheme.outlineVariant else accent.copy(alpha = if (rank >= 80) .95f else if (rank >= 50) .65f else .4f)
    val background = if (muted) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow
    DesignClickableSurface(onClick = { onClick(entry) }, color = background, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).heightIn(min = 80.dp).testTag("timeline-${entry.id}")) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            val stacked = maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.5f
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${scheduleMinuteLabel(entry.startMinute)}–${scheduleMinuteLabel(entry.endMinute)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, softWrap = false)
                    ScheduleEntryContent(entry, priorities, index, muted)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ScheduleTimeRail(entry.startMinute, entry.endMinute, rail)
                    Column(Modifier.weight(1f)) {
                        ScheduleEntryContent(entry, priorities, index, muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleEntryContent(entry: TimelineEntry, priorities: Map<String, TaskPriority>, paletteIndex: Int, muted: Boolean) {
    val palette = replandAccent(paletteIndex)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(entry.title, style = MaterialTheme.typography.titleMedium,
            fontWeight = if (muted) FontWeight.Medium else FontWeight.SemiBold,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        entry.note?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Surface(color = palette.surface, shape = RoundedCornerShape(6.dp)) {
            Text(PriorityScheduleProjection.label(entry, priorities), Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium, color = palette.ink)
        }
    }
}
