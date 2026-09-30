package com.swan1127.repland.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swan1127.repland.domain.model.TimeBlockValidator
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A compact home surface: one visual container, one time ruler, and one agenda rail.
 * It intentionally avoids rendering every task as a detached card.
 */
@Composable
fun DailyDesk(
    date: LocalDate,
    entries: List<TimelineEntry>,
    unplacedCount: Int,
    unplacedTasks: List<Task>,
    plannedMinutes: Int,
    onOpenEntry: (String) -> Unit,
    onOpenTask: (Task) -> Unit,
    onOpenPlan: () -> Unit,
    onAdd: () -> Unit,
) {
    val dateLabel = date.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA))
    val next = entries.firstOrNull { it.endMinute > java.time.LocalTime.now().let { time -> time.hour * 60 + time.minute } }
        ?: entries.firstOrNull()
    val parallelNext = next?.let { target -> entries.filter { it.id != target.id && it.startMinute == target.startMinute } }.orEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("daily-desk"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("今天", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatPlannedTime(plannedMinutes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = onAdd, modifier = Modifier.size(48.dp).testTag("add-task")) {
                    Icon(Icons.Outlined.Add, contentDescription = "添加事件")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        next?.title ?: "给今天留一段开始",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        next?.let {
                            "${TimeBlockValidator.formatTime(it.startMinute)}–${TimeBlockValidator.formatTime(it.endMinute)}" +
                                if (parallelNext.isEmpty()) " · 下一段" else " · 另有 ${parallelNext.size} 件并行"
                        } ?: "添加事件后，再到日轨道安排它",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButtonCompact(label = "专注", onClick = { next?.let { onOpenEntry(it.id) } ?: onOpenPlan() })
            }

            if (unplacedTasks.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("事件库", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButtonCompact(label = "日轨道", onClick = onOpenPlan)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("课程", "事务", "生活", "课外").forEach { label ->
                        Text(label, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                unplacedTasks.take(3).forEach { task ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onOpenTask(task) }
                            .testTag("task-card-${task.displayName}")
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.outline, RoundedCornerShape(50)))
                        Text(task.displayName, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(categoryLabel(task), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (unplacedCount == 0) Text("今天的事件已全部放入轨道", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TextButtonCompact(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier = modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

private fun categoryLabel(task: Task): String = when (task.category) {
    com.swan1127.repland.domain.model.TaskCategory.COURSE -> "课程"
    com.swan1127.repland.domain.model.TaskCategory.OFFICE -> "事务"
    com.swan1127.repland.domain.model.TaskCategory.LEISURE -> "生活"
    com.swan1127.repland.domain.model.TaskCategory.EXTRACURRICULAR -> "课外"
}

private fun formatPlannedTime(minutes: Int): String = when {
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    minutes > 0 -> "${minutes}m"
    else -> "计划 0 分钟"
}
