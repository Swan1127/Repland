package com.swan1127.repland.ui.schedule

import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import com.swan1127.repland.domain.model.ScheduleTimeline
import com.swan1127.repland.domain.model.EngagementMode
import com.swan1127.repland.domain.model.TimeBlockValidator
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import com.swan1127.repland.domain.model.TimelinePhase
import com.swan1127.repland.domain.model.TaskCategory
import java.time.LocalDateTime
import java.time.LocalDate
import kotlinx.coroutines.delay

private val CourseAccent = Color(0xFF4F65A6)
private val RestAccent = Color(0xFF7C7C9C)
private val CommitmentAccent = Color(0xFFA66B42)

data class TimelineEventObject(
    val id: String,
    val title: String,
    val durationMinutes: Int?,
    val category: TaskCategory = TaskCategory.EXTRACURRICULAR,
)

private data class TimelineTrackOption(val id: String, val label: String)

data class CourseInsertionRequest(
    val title: String,
    val startMinute: Int,
    val durationMinutes: Int,
    val trackId: String,
)

private data class PendingCourse(
    val title: String,
    val durationMinutes: Int,
    val preferredTrackId: String,
)

private enum class TimelineViewMode { MERGED, SPLIT }

/** A multi-track projection of the confirmed schedule; no timer here records execution. */
@Composable
fun TimelineDashboard(
    entries: List<TimelineEntry>,
    mode: EngagementMode,
    onOpenEntry: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onEditEntry: (TimelineEntry) -> Unit = {},
    eventObjects: List<TimelineEventObject> = emptyList(),
    onPlaceEvent: (TimelineEventObject, String, Int) -> Unit = { _, _, _ -> },
    scheduleDate: LocalDate = entries.firstOrNull()?.date ?: LocalDate.now(),
    onCreateCourse: (CourseInsertionRequest) -> Unit = {},
    onFocusStarted: (String) -> Unit = {},
    onFocusCompleted: (String, Int) -> Unit = { _, _ -> },
    onFocusNotCompleted: (String, Int) -> Unit = { _, _ -> },
    onRemovePlacement: (String) -> Unit = {},
    onMoveEntry: (TimelineEntry, Int) -> Unit = { _, _ -> },
    onUpdateEntryTime: (TimelineEntry, Int, Int) -> Unit = { entry, startMinute, _ ->
        onMoveEntry(entry, startMinute)
    },
) {
    val context = LocalContext.current
    val motionScale = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var showAll by remember { mutableStateOf(false) }
    var customTrackNames by remember { mutableStateOf(listOf<String>()) }
    var showTrackComposer by remember { mutableStateOf(false) }
    var newTrackName by remember { mutableStateOf("") }
    var showEventLibrary by rememberSaveable { mutableStateOf(false) }
    var focusEntry by remember { mutableStateOf<TimelineEntry?>(null) }
    var viewMode by rememberSaveable { mutableStateOf(TimelineViewMode.SPLIT) }
    var showCourseComposer by rememberSaveable { mutableStateOf(false) }
    var pendingCourse by remember { mutableStateOf<PendingCourse?>(null) }
    var selectedEntry by remember { mutableStateOf<TimelineEntry?>(null) }
    LaunchedEffect(mode) { showAll = false }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(15_000)
        }
    }
    val tryUpdateEntryTime: (TimelineEntry, Int, Int) -> Boolean = { entry, startMinute, endMinute ->
        val validRange = startMinute in 0 until 1_440 && endMinute in 1..1_440 && startMinute < endMinute
        val conflicts = entries.any { other ->
            other.id != entry.id && other.trackId == entry.trackId &&
                startMinute < other.endMinute && endMinute > other.startMinute
        }
        if (validRange && !conflicts) onUpdateEntryTime(entry, startMinute, endMinute)
        validRange && !conflicts
    }
    val tryMoveEntry: (TimelineEntry, Int) -> Boolean = { entry, requestedStartMinute ->
        val duration = entry.endMinute - entry.startMinute
        val startMinute = requestedStartMinute.coerceIn(0, 1_440 - duration)
        tryUpdateEntryTime(entry, startMinute, startMinute + duration)
    }
    val visibleEntries = if (mode == EngagementMode.EXECUTOR && !showAll) {
        entries.filter { !it.end.isBefore(now) }.take(3).ifEmpty { entries.takeLast(3) }
    } else entries
    val automaticTrackNames = if (visibleEntries.isEmpty()) {
        emptyList()
    } else {
        val automaticTrackCount = (visibleEntries.maxOfOrNull(TimelineEntry::lane) ?: 0) + 1
        (0 until automaticTrackCount).map { index ->
            val baseName = visibleEntries.firstOrNull { it.lane == index }?.let { entry ->
                when (entry.kind) {
                    TimelineKind.COURSE -> "课程"
                    TimelineKind.TASK -> "专注"
                    TimelineKind.REST -> "休息"
                    TimelineKind.COMMITMENT -> "固定事项"
                }
            } ?: "轨道 ${index + 1}"
            val duplicateOrdinal = (0 until index).count { previous ->
                visibleEntries.firstOrNull { it.lane == previous }?.kind == visibleEntries.firstOrNull { it.lane == index }?.kind
            }
            if (duplicateOrdinal == 0) baseName else "$baseName · 并行 ${duplicateOrdinal + 1}"
        }
    }
    val trackNames = automaticTrackNames + customTrackNames
    val trackIds = trackNames.indices.map { index ->
        visibleEntries.firstOrNull { it.lane == index }?.trackId ?: "custom-$index"
    }
    val trackOptions = trackNames.zip(trackIds).map { (name, id) -> TimelineTrackOption(id, name) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("日轨道", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("长按事项可直接移动；时间在左", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(999.dp)) {
                Text("${entries.size} 项", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = viewMode == TimelineViewMode.MERGED,
                onClick = { viewMode = TimelineViewMode.MERGED },
                label = { Text("合轨") },
            )
            FilterChip(
                selected = viewMode == TimelineViewMode.SPLIT,
                onClick = { viewMode = TimelineViewMode.SPLIT },
                label = { Text("分轨") },
            )
            Text(
                if (viewMode == TimelineViewMode.MERGED) "看时间关系" else "编辑每条轨道",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entries.isEmpty()) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("给每一种节奏留位置", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("添加课程与任务后，重叠安排会分轨呈现，时间始终一目了然。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(
                        Triple("课程", CourseAccent, 0.78f),
                        Triple("专注", MaterialTheme.colorScheme.primary, 0.54f),
                        Triple("休息", RestAccent, 0.36f),
                    ).forEach { (title, tint, fraction) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(title, modifier = Modifier.width(36.dp),
                                style = MaterialTheme.typography.labelSmall)
                            Box(Modifier.fillMaxWidth(fraction).height(8.dp)
                                .background(tint.copy(alpha = 0.65f), RoundedCornerShape(8.dp)))
                        }
                    }
                    Text("轨道示意 · 不代表真实安排", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showEventLibrary = true },
                            modifier = Modifier.testTag("empty-event-library-trigger"),
                        ) { Text("放入待安排事项") }
                        OutlinedButton(
                            onClick = { showCourseComposer = true },
                            modifier = Modifier.testTag("empty-add-course-trigger"),
                        ) { Text("新增课程") }
                    }
                }
            }
        } else {
            TrackStrip(
                names = trackNames,
                eventCount = eventObjects.size,
                showComposer = showTrackComposer,
                draftName = newTrackName,
                onDraftNameChange = { newTrackName = it },
                onAddClick = { showTrackComposer = true },
                onOpenEventLibrary = { showEventLibrary = true },
                onAddCourse = { showCourseComposer = true },
                onCancel = { showTrackComposer = false; newTrackName = "" },
                onConfirm = {
                    val name = newTrackName.trim()
                    if (name.isNotEmpty()) {
                        customTrackNames = customTrackNames + name
                        newTrackName = ""
                        showTrackComposer = false
                    }
                },
            )
            val onEntryClick: (TimelineEntry) -> Unit = { entry ->
                // A short tap always opens a stable detail sheet. Keeping actions out of the
                // scrolling canvas avoids an expanded card covering another time block.
                selectedEntry = entry
                onOpenEntry(entry.id)
            }
            if (viewMode == TimelineViewMode.SPLIT) {
                DayTimelineGrid(
                    entries = visibleEntries,
                    trackNames = trackNames,
                    now = now,
                    motionDuration = if (motionScale == 0f) 0 else 220,
                    pendingCourse = pendingCourse,
                    onPendingCourseDropped = { course, startMinute, trackId ->
                        onCreateCourse(CourseInsertionRequest(course.title, startMinute, course.durationMinutes, trackId))
                        pendingCourse = null
                    },
                    onClick = onEntryClick,
                    onMoveEntry = tryMoveEntry,
                )
            } else {
                MergedTimelineGrid(
                    entries = visibleEntries,
                    now = now,
                    onClick = onEntryClick,
                )
            }
            if (showEventLibrary) {
                EventLibrarySheet(
                    events = eventObjects,
                    tracks = trackOptions.ifEmpty { listOf(TimelineTrackOption("focus", "专注")) },
                    occupiedEntries = entries,
                    onDismiss = { showEventLibrary = false },
                    onPlace = { event, trackId, minute ->
                        onPlaceEvent(event, trackId, minute)
                        showEventLibrary = false
                    },
                )
            }
            if (showCourseComposer) {
                CourseComposerDialog(
                    tracks = trackOptions.ifEmpty { listOf(TimelineTrackOption("course", "课程")) },
                    occupiedEntries = entries,
                    onDismiss = { showCourseComposer = false },
                    onManualCreate = { request ->
                        onCreateCourse(request)
                        showCourseComposer = false
                    },
                    onBeginDrag = { course ->
                        pendingCourse = course
                        viewMode = TimelineViewMode.SPLIT
                        showCourseComposer = false
                    },
                )
            }
            if (mode == EngagementMode.EXECUTOR && entries.size > visibleEntries.size) {
                Text("${if (showAll) "收起" else "查看完整日程"} →",
                    modifier = Modifier.clickable { showAll = !showAll }.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
        if (entries.isEmpty() && showEventLibrary) {
            EventLibrarySheet(
                events = eventObjects,
                tracks = listOf(TimelineTrackOption("focus", "主线")),
                occupiedEntries = entries,
                onDismiss = { showEventLibrary = false },
                onPlace = { event, trackId, minute ->
                    onPlaceEvent(event, trackId, minute)
                    showEventLibrary = false
                },
            )
        }
        if (entries.isEmpty() && showCourseComposer) {
            CourseComposerDialog(
                tracks = listOf(TimelineTrackOption("course", "课程")),
                occupiedEntries = entries,
                allowDragPlacement = false,
                onDismiss = { showCourseComposer = false },
                onManualCreate = { request ->
                    onCreateCourse(request)
                    showCourseComposer = false
                },
                onBeginDrag = {},
            )
        }
        selectedEntry?.let { entry ->
            TimelineEntryDetailSheet(
                entry = entry,
                now = now,
                onDismiss = { selectedEntry = null },
                onEdit = {
                    selectedEntry = null
                    onEditEntry(entry)
                },
                onOpenTask = entry.taskId?.let { taskId ->
                    {
                        selectedEntry = null
                        onOpenTask(taskId)
                    }
                },
                onFocus = entry.taskId?.let {
                    {
                        selectedEntry = null
                        focusEntry = entry
                    }
                },
                onMove = { startMinute ->
                    val moved = tryMoveEntry(entry, startMinute)
                    if (moved) selectedEntry = null
                    moved
                },
                onUpdateTime = { startMinute, endMinute ->
                    val updated = tryUpdateEntryTime(entry, startMinute, endMinute)
                    if (updated) selectedEntry = null
                    updated
                },
                onRemovePlacement = entry.id.removePrefix("segment:")
                    .takeIf { entry.id.startsWith("segment:") }
                    ?.let { segmentId ->
                        {
                            selectedEntry = null
                            onRemovePlacement(segmentId)
                        }
                    },
            )
        }
        focusEntry?.let { entry ->
            FocusSessionScreen(
                entry = entry,
                onDismiss = { focusEntry = null },
                onStart = onFocusStarted,
                onComplete = onFocusCompleted,
                onNotCompleted = onFocusNotCompleted,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventLibrarySheet(
    events: List<TimelineEventObject>,
    tracks: List<TimelineTrackOption>,
    occupiedEntries: List<TimelineEntry>,
    onDismiss: () -> Unit,
    onPlace: (TimelineEventObject, String, Int) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf<TaskCategory?>(null) }
    var selectedEvent by remember { mutableStateOf<TimelineEventObject?>(null) }
    var selectedTrack by remember { mutableStateOf(tracks.first().id) }
    var hourText by remember { mutableStateOf("08") }
    var minuteText by remember { mutableStateOf("00") }
    val selectedMinute = ((hourText.toIntOrNull() ?: -1) * 60 + (minuteText.toIntOrNull() ?: -1))
        .takeIf { it in 0 until 1_440 }
    val selectedDuration = selectedEvent?.durationMinutes?.coerceIn(5, 720) ?: 30
    val sameTrackCollision = selectedMinute != null && selectedEvent != null && occupiedEntries.any { entry ->
        entry.trackId == selectedTrack &&
            selectedMinute < entry.endMinute &&
            selectedMinute + selectedDuration > entry.startMinute
    }
    val visibleEvents = events.filter { selectedCategory == null || it.category == selectedCategory }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (selectedEvent == null) "待安排事件" else "放到日轨道", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (selectedEvent == null) {
                Text("选择一个事件后，再指定时间和轨道；这里不会自动安排。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("全部") })
                    TaskCategory.entries.forEach { category ->
                        FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category }, label = { Text(category.shortLabel()) })
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleEvents.forEach { event ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { selectedEvent = event }.testTag("event-object-${event.id}"),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(event.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${event.category.shortLabel()} · ${event.durationMinutes?.let { "预计 ${it} 分钟" } ?: "默认 30 分钟"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("选择", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            } else {
                val event = requireNotNull(selectedEvent)
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(event.title, style = MaterialTheme.typography.titleMedium)
                        Text("${event.category.shortLabel()} · ${event.durationMinutes?.let { "预计 ${it} 分钟" } ?: "默认 30 分钟"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Text("时间", style = MaterialTheme.typography.titleSmall)
                Text("任意时刻插入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.weight(1f).testTag("event-hour-input"),
                        label = { Text("时") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text(":", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.weight(1f).testTag("event-minute-input"),
                        label = { Text("分") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                if (selectedMinute == null) Text("请输入 00:00–23:59。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (sameTrackCollision) Text("该轨道这个时段已有事件；选择另一条轨道即可并行。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Text("轨道", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tracks.distinctBy(TimelineTrackOption::id).forEach { track ->
                        FilterChip(selected = selectedTrack == track.id, onClick = { selectedTrack = track.id }, label = { Text(track.label) })
                    }
                }
                Button(onClick = { onPlace(event, selectedTrack, requireNotNull(selectedMinute)) }, enabled = selectedMinute != null && !sameTrackCollision, modifier = Modifier.fillMaxWidth().testTag("event-place-confirm")) {
                    Text("安排到 ${selectedMinute?.let(TimeBlockValidator::formatTime) ?: "--:--"}")
                }
                TextButton(onClick = { selectedEvent = null }) { Text("返回事件库") }
            }
        }
    }
}

@Composable
private fun DayTimelineGrid(
    entries: List<TimelineEntry>,
    trackNames: List<String>,
    now: LocalDateTime,
    motionDuration: Int,
    pendingCourse: PendingCourse?,
    onPendingCourseDropped: (PendingCourse, Int, String) -> Unit,
    onClick: (TimelineEntry) -> Unit,
    onMoveEntry: (TimelineEntry, Int) -> Boolean,
) {
    // Compact enough to scan a full day, while keeping the fixed time ruler legible.
    val hourHeight = 34.dp
    val dayStart = 0
    val totalHeight = hourHeight * 24
    val laneCount = maxOf((entries.maxOfOrNull(TimelineEntry::lane) ?: 0) + 1, trackNames.size)
    val currentMinute = if (now.toLocalDate() == entries.firstOrNull()?.date) now.hour * 60 + now.minute else -1
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth()) {
            if (laneCount > 1) {
                Text(
                    "并行时段 · $laneCount 条轨道",
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 58.dp, end = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                trackNames.forEachIndexed { index, name ->
                    Surface(modifier = Modifier.width(100.dp), color = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(10.dp)) {
                        Text(name, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
                BoxWithConstraints(Modifier.fillMaxWidth().height(totalHeight).padding(vertical = 10.dp)) {
                    val rulerWidth = 56.dp
                    val laneWidth = 108.dp
                    val timelineWidth = maxOf(maxWidth - rulerWidth, laneWidth * laneCount)
                    val horizontalScroll = rememberScrollState()

                    // The ruler deliberately sits outside the horizontal scroller. It remains visible
                    // while the user pans across a multi-track day.
                    for (hour in dayStart..24) {
                        val y = hourHeight * hour
                        Text(
                            text = String.format("%02d:00", hour.coerceAtMost(23)),
                            modifier = Modifier
                                .offset(y = (y - 10.dp).coerceAtLeast(0.dp))
                                .width(rulerWidth)
                                .height(20.dp)
                                .padding(end = 7.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        Modifier
                            .padding(start = rulerWidth)
                            .fillMaxWidth()
                            .height(totalHeight)
                            .horizontalScroll(horizontalScroll),
                    ) {
                        Box(Modifier.width(timelineWidth).height(totalHeight)) {
                            for (hour in dayStart..24) {
                                Box(
                                    Modifier
                                        .offset(y = hourHeight * hour)
                                        .width(timelineWidth)
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                                )
                            }
                            if (currentMinute >= 0) {
                                Box(
                                    Modifier.offset(y = hourHeight * (currentMinute / 60f))
                                        .width(timelineWidth)
                                        .height(2.dp)
                                        .background(MaterialTheme.colorScheme.error),
                                )
                            }
                            entries.forEach { entry ->
                                TimelineBlock(
                                    entry = entry,
                                    now = now,
                                    x = laneWidth * entry.lane,
                                    width = laneWidth - 8.dp,
                                    y = hourHeight * (entry.startMinute / 60f),
                                    height = (hourHeight * ((entry.endMinute - entry.startMinute) / 60f)).coerceAtLeast(38.dp),
                                    motionDuration = motionDuration,
                                    onClick = { onClick(entry) },
                                    onMove = { startMinute -> onMoveEntry(entry, startMinute) },
                                )
                            }
                            pendingCourse?.let { course ->
                                PendingCourseBlock(
                                    course = course,
                                    occupiedEntries = entries,
                                    trackIds = trackNames.indices.map { index ->
                                        entries.firstOrNull { it.lane == index }?.trackId ?: "custom-$index"
                                    },
                                    laneWidth = laneWidth,
                                    hourHeight = hourHeight,
                                    onDrop = onPendingCourseDropped,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingCourseBlock(
    course: PendingCourse,
    occupiedEntries: List<TimelineEntry>,
    trackIds: List<String>,
    laneWidth: Dp,
    hourHeight: Dp,
    onDrop: (PendingCourse, Int, String) -> Unit,
) {
    val density = LocalDensity.current
    val minutePerPixel = with(density) { 60f / hourHeight.toPx() }
    val laneWidthPx = with(density) { laneWidth.toPx() }
    var dragX by remember(course) { mutableStateOf(0f) }
    var dragY by remember(course) { mutableStateOf(0f) }
    var lane by remember(course) { mutableStateOf(trackIds.indexOf(course.preferredTrackId).coerceAtLeast(0)) }
    var previewMinute by remember(course) { mutableStateOf(8 * 60) }
    var dragStartLane by remember(course) { mutableStateOf(0) }
    var dragStartMinute by remember(course) { mutableStateOf(8 * 60) }
    val duration = course.durationMinutes.coerceIn(15, 360)
    val targetTrackId = trackIds.getOrElse(lane) { "course" }
    val collides = occupiedEntries.any { entry ->
        entry.trackId == targetTrackId && previewMinute < entry.endMinute && previewMinute + duration > entry.startMinute
    }
    Surface(
        modifier = Modifier
            .offset(x = laneWidth * lane, y = hourHeight * (previewMinute / 60f))
            .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
            .width(laneWidth - 8.dp)
            .pointerInput(course, trackIds) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragX = 0f
                        dragY = 0f
                        dragStartLane = lane
                        dragStartMinute = previewMinute
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount.x
                        dragY += amount.y
                        lane = (dragStartLane + dragX / laneWidthPx).roundToInt().coerceIn(0, trackIds.lastIndex.coerceAtLeast(0))
                        previewMinute = (dragStartMinute + dragY * minutePerPixel).roundToInt()
                            .coerceIn(0, 1_440 - duration)
                    },
                    onDragCancel = { dragX = 0f; dragY = 0f },
                    onDragEnd = {
                        val snapped = (previewMinute / 15f).roundToInt().times(15)
                        if (!collides) onDrop(course, snapped.coerceIn(0, 1_440 - duration), targetTrackId)
                    },
                )
            }
            .testTag("pending-course-drop"),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, if (collides) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("拖动中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(course.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${TimeBlockValidator.formatTime(previewMinute)} · ${duration} 分钟",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (collides) Text("该轨道有冲突，横向拖到另一轨道", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CourseComposerDialog(
    tracks: List<TimelineTrackOption>,
    occupiedEntries: List<TimelineEntry>,
    allowDragPlacement: Boolean = true,
    onDismiss: () -> Unit,
    onManualCreate: (CourseInsertionRequest) -> Unit,
    onBeginDrag: (PendingCourse) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var durationText by remember { mutableStateOf("90") }
    var hourText by remember { mutableStateOf("08") }
    var minuteText by remember { mutableStateOf("00") }
    var trackId by remember { mutableStateOf(tracks.first().id) }
    var placementMode by remember(allowDragPlacement) {
        mutableStateOf(if (allowDragPlacement) "drag" else "manual")
    }
    val duration = durationText.toIntOrNull()?.coerceIn(15, 360)
    val startMinute = ((hourText.toIntOrNull() ?: -1) * 60 + (minuteText.toIntOrNull() ?: -1))
        .takeIf { it in 0 until 1_440 }
    val manualCollision = startMinute != null && duration != null && occupiedEntries.any { entry ->
        entry.trackId == trackId && startMinute < entry.endMinute && startMinute + duration > entry.startMinute
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增课程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth().testTag("course-title-input"),
                    singleLine = true,
                    label = { Text("课程名称") },
                    placeholder = { Text("例如：数据结构") },
                )
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.fillMaxWidth().testTag("course-duration-input"),
                    singleLine = true,
                    label = { Text("时长（分钟）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (allowDragPlacement) {
                    Text("插入方式", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = placementMode == "drag", onClick = { placementMode = "drag" }, label = { Text("拖入时间轴") })
                        FilterChip(selected = placementMode == "manual", onClick = { placementMode = "manual" }, label = { Text("手动设时间") })
                    }
                } else {
                    Text(
                        "先设定首个课程的时间；创建后即可在日轨道上拖动调整。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (placementMode == "manual") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = hourText, onValueChange = { hourText = it.filter(Char::isDigit).take(2) }, modifier = Modifier.weight(1f), label = { Text("时") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Text(":")
                        OutlinedTextField(value = minuteText, onValueChange = { minuteText = it.filter(Char::isDigit).take(2) }, modifier = Modifier.weight(1f), label = { Text("分") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    if (manualCollision) Text("该轨道这个时间已有事项；请选择其他轨道或修改时间。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text("放入轨道", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tracks.distinctBy(TimelineTrackOption::id).forEach { track ->
                        FilterChip(selected = trackId == track.id, onClick = { trackId = track.id }, label = { Text(track.label) })
                    }
                }
            }
        },
        confirmButton = {
            val canCreate = title.isNotBlank() && duration != null && (placementMode == "drag" || startMinute != null && !manualCollision)
            TextButton(
                enabled = canCreate,
                onClick = {
                    if (placementMode == "drag") onBeginDrag(PendingCourse(title.trim(), requireNotNull(duration), trackId))
                    else onManualCreate(CourseInsertionRequest(title.trim(), requireNotNull(startMinute), requireNotNull(duration), trackId))
                },
            ) { Text(if (placementMode == "drag") "开始拖动" else "保存课程") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private data class TimelineBundle(
    val startMinute: Int,
    val endMinute: Int,
    val entries: List<TimelineEntry>,
)

private fun List<TimelineEntry>.toTimelineBundles(): List<TimelineBundle> =
    sortedBy(TimelineEntry::startMinute).fold(mutableListOf()) { bundles, entry ->
        val last = bundles.lastOrNull()
        if (last == null || entry.startMinute >= last.endMinute) {
            bundles += TimelineBundle(entry.startMinute, entry.endMinute, listOf(entry))
        } else {
            bundles[bundles.lastIndex] = last.copy(
                endMinute = maxOf(last.endMinute, entry.endMinute),
                entries = last.entries + entry,
            )
        }
        bundles
    }

@Composable
private fun MergedTimelineGrid(
    entries: List<TimelineEntry>,
    now: LocalDateTime,
    onClick: (TimelineEntry) -> Unit,
) {
    val hourHeight = 34.dp
    val totalHeight = hourHeight * 24
    val bundles = remember(entries) { entries.toTimelineBundles() }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text("同一时间段收进一个容器；点分轨可回到逐条编辑。", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(totalHeight).padding(vertical = 10.dp)) {
                    for (hour in 0..24) {
                        val y = hourHeight * hour
                        Text(String.format("%02d:00", hour.coerceAtMost(23)), modifier = Modifier.offset(y = (y - 10.dp).coerceAtLeast(0.dp)).width(56.dp).height(20.dp).padding(end = 7.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    bundles.forEach { bundle ->
                        val bundleHeight = (hourHeight * ((bundle.endMinute - bundle.startMinute) / 60f)).coerceAtLeast(48.dp)
                        val accent = if (bundle.entries.size > 1) MaterialTheme.colorScheme.secondary else entryAccent(bundle.entries.first())
                        Surface(
                            modifier = Modifier.offset(x = 58.dp, y = hourHeight * (bundle.startMinute / 60f)).fillMaxWidth().padding(end = 10.dp).height(bundleHeight),
                            color = accent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                if (bundle.entries.size > 1) Text("${TimeBlockValidator.formatTime(bundle.startMinute)}–${TimeBlockValidator.formatTime(bundle.endMinute)} · 并行 ${bundle.entries.size} 项", style = MaterialTheme.typography.labelSmall, color = accent)
                                bundle.entries.forEach { entry ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().clickable { onClick(entry) },
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.width(3.dp).height(22.dp).background(entryAccent(entry), RoundedCornerShape(4.dp)))
                                            Text(entry.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${TimeBlockValidator.formatTime(entry.startMinute)}–${TimeBlockValidator.formatTime(entry.endMinute)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun entryAccent(entry: TimelineEntry): Color = when (entry.kind) {
    TimelineKind.COURSE -> CourseAccent
    TimelineKind.REST -> RestAccent
    TimelineKind.COMMITMENT -> CommitmentAccent
    TimelineKind.TASK -> Color(0xFF4E64B5)
}

@Composable
private fun TrackStrip(
    names: List<String>,
    eventCount: Int,
    showComposer: Boolean,
    draftName: String,
    onDraftNameChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onOpenEventLibrary: () -> Unit,
    onAddCourse: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.height(48.dp).clickable(onClick = onAddClick).testTag("add-track-trigger"),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "添加轨道")
                    Text("轨道", style = MaterialTheme.typography.labelMedium)
                }
            }
            Surface(
                modifier = Modifier.height(48.dp).clickable(onClick = onAddCourse).testTag("add-course-trigger"),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text("课程", style = MaterialTheme.typography.labelMedium) }
            }
            Surface(
                modifier = Modifier.height(48.dp).clickable(onClick = onOpenEventLibrary).testTag("event-library-trigger"),
                color = if (eventCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("待安排", style = MaterialTheme.typography.labelMedium)
                    Text(eventCount.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (showComposer) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = onDraftNameChange,
                        modifier = Modifier.weight(1f).testTag("track-name-input"),
                        singleLine = true,
                        label = { Text("新轨道名称") },
                        placeholder = { Text("例如：运动、语言学习") },
                    )
                    Button(onClick = onConfirm, enabled = draftName.isNotBlank(), modifier = Modifier.testTag("add-track")) {
                        Text("添加")
                    }
                    Text(
                        "取消",
                        modifier = Modifier.clickable(onClick = onCancel).padding(10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineBlock(
    entry: TimelineEntry,
    now: LocalDateTime,
    x: Dp,
    width: Dp,
    y: Dp,
    height: Dp,
    motionDuration: Int,
    onClick: () -> Unit,
    onMove: (Int) -> Boolean,
) {
    val accent = when (entry.kind) {
        TimelineKind.COURSE -> CourseAccent
        TimelineKind.REST -> RestAccent
        TimelineKind.COMMITMENT -> CommitmentAccent
        TimelineKind.TASK -> MaterialTheme.colorScheme.primary
    }
    val phase = ScheduleTimeline.clock(entry, now, userFinished = entry.taskClosed)
    val density = LocalDensity.current
    var dragDeltaPx by remember(entry.id) { mutableStateOf(0f) }
    var dragPreviewMinute by remember(entry.id) { mutableStateOf<Int?>(null) }
    val durationMinutes = entry.endMinute - entry.startMinute
    val minutePerPixel = with(density) { 60f / 34.dp.toPx() }
    val previewStart = dragPreviewMinute ?: entry.startMinute
    val previewEnd = previewStart + durationMinutes
    // A quick press opens detail; a long press keeps the object under the finger and moves it.
    // The click modifier deliberately comes first so the short-tap path remains available.
    val directManipulationModifier = Modifier.pointerInput(entry.id, entry.startMinute, entry.endMinute) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    dragDeltaPx = 0f
                    dragPreviewMinute = entry.startMinute
                },
                onDrag = { change, amount ->
                    change.consume()
                    dragDeltaPx += amount.y
                    dragPreviewMinute = (entry.startMinute + dragDeltaPx * minutePerPixel)
                        .roundToInt()
                        .coerceIn(0, 1_440 - durationMinutes)
                },
                onDragCancel = {
                    dragDeltaPx = 0f
                    dragPreviewMinute = null
                },
                onDragEnd = {
                    val target = ((dragPreviewMinute ?: entry.startMinute) / 15f)
                        .roundToInt()
                        .times(15)
                        .coerceIn(0, 1_440 - durationMinutes)
                    dragDeltaPx = 0f
                    dragPreviewMinute = null
                    if (target != entry.startMinute) onMove(target)
                },
            )
        }
    Box(
        modifier = Modifier.offset(x = x, y = y)
            .offset { IntOffset(0, dragDeltaPx.roundToInt()) }
            .width(width)
            .zIndex(if (dragPreviewMinute == null) 1f else 2f)
            .animateContentSize(animationSpec = tween(motionDuration))
            .clickable(onClick = onClick)
            .then(directManipulationModifier)
            .testTag("timeline-${entry.id}"),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = height),
            color = accent.copy(alpha = 0.13f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${TimeBlockValidator.formatTime(previewStart)}–${TimeBlockValidator.formatTime(previewEnd)} · ${phase.shortLabel()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineEntryDetailSheet(
    entry: TimelineEntry,
    now: LocalDateTime,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onOpenTask: (() -> Unit)?,
    onFocus: (() -> Unit)?,
    onMove: (Int) -> Boolean,
    onUpdateTime: (Int, Int) -> Boolean,
    onRemovePlacement: (() -> Unit)?,
) {
    val accent = entryAccent(entry)
    val phase = ScheduleTimeline.clock(entry, now, userFinished = entry.taskClosed)
    val durationMinutes = entry.endMinute - entry.startMinute
    val note = entry.note?.trim().takeIf { !it.isNullOrBlank() }
    var timeError by remember(entry.id) { mutableStateOf<String?>(null) }
    var showTimeEditor by remember(entry.id) { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        modifier = Modifier.testTag("timeline-entry-detail-${entry.id}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("今日安排", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(entry.kind.displayLabel(), style = MaterialTheme.typography.labelLarge, color = accent)
                    Text(entry.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${TimeBlockValidator.formatTime(entry.startMinute)}–${TimeBlockValidator.formatTime(entry.endMinute)} · ${phase.copyForDisplay()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("备注", style = MaterialTheme.typography.labelLarge)
                    Text(
                        note ?: "暂无备注。可以写地点、准备物、联系人或这次安排的上下文。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("timeline-note-${entry.id}"),
                    ) { Text(if (note == null) "添加备注" else "编辑备注") }
                }
            }
            Text("时间", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        timeError = if (onMove((entry.startMinute - 15).coerceAtLeast(0))) null
                        else "同一轨道的这个时段已有安排。"
                    },
                    enabled = entry.startMinute >= 15,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("提前 15 分") }
                OutlinedButton(
                    onClick = {
                        timeError = if (onMove((entry.startMinute + 15).coerceAtMost(1_440 - durationMinutes))) null
                        else "同一轨道的这个时段已有安排。"
                    },
                    enabled = entry.endMinute <= 1_425,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("推后 15 分") }
            }
            timeError?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(
                onClick = { showTimeEditor = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("timeline-time-editor-${entry.id}"),
            ) { Text("精确调整时间") }
            Button(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("timeline-edit-${entry.id}"),
            ) { Text("编辑详情与备注") }
            if (onFocus != null) {
                Button(
                    onClick = onFocus,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("timeline-focus-${entry.id}"),
                ) { Text("进入专注") }
            }
            if (onOpenTask != null) {
                OutlinedButton(
                    onClick = onOpenTask,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open-task-${entry.id}"),
                ) { Text("打开任务详情") }
            }
            Text(
                "长按轨道块后上下拖动，也可以直接调整时段。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            onRemovePlacement?.let { removePlacement ->
                TextButton(
                    onClick = removePlacement,
                    modifier = Modifier.testTag("timeline-remove-${entry.id}"),
                ) { Text("移出今日安排") }
            }
        }
    }
    if (showTimeEditor) {
        TimelineTimeEditorDialog(
            entry = entry,
            onDismiss = { showTimeEditor = false },
            onSave = { startMinute, endMinute ->
                val updated = onUpdateTime(startMinute, endMinute)
                if (updated) showTimeEditor = false
                updated
            },
        )
    }
}

@Composable
private fun TimelineTimeEditorDialog(
    entry: TimelineEntry,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Boolean,
) {
    var startTime by remember(entry.id) { mutableStateOf(TimeBlockValidator.formatTime(entry.startMinute)) }
    var endTime by remember(entry.id) { mutableStateOf(TimeBlockValidator.formatTime(entry.endMinute)) }
    var errorMessage by remember(entry.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整 ${entry.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "可以让同一时段的事项并行，只要它们在不同轨道。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it; errorMessage = null },
                    modifier = Modifier.fillMaxWidth().testTag("timeline-start-${entry.id}"),
                    label = { Text("开始时间") },
                    placeholder = { Text("09:00") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it; errorMessage = null },
                    modifier = Modifier.fillMaxWidth().testTag("timeline-end-${entry.id}"),
                    label = { Text("结束时间") },
                    placeholder = { Text("10:00") },
                    singleLine = true,
                )
                errorMessage?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val startMinute = TimeBlockValidator.parseTime(startTime)
                    val endMinute = TimeBlockValidator.parseTime(endTime)
                    errorMessage = when {
                        startMinute == null || endMinute == null || startMinute >= endMinute -> "请输入有效的开始与结束时间。"
                        !onSave(startMinute, endMinute) -> "同一轨道的这个时段已有安排；可换时间或保留并行轨道。"
                        else -> null
                    }
                },
                modifier = Modifier.testTag("timeline-time-save-${entry.id}"),
            ) { Text("保存时间") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun TimelineKind.displayLabel(): String = when (this) {
    TimelineKind.COURSE -> "课程"
    TimelineKind.REST -> "休息"
    TimelineKind.COMMITMENT -> "固定事项"
    TimelineKind.TASK -> "任务"
}

@Composable
private fun FocusSessionScreen(
    entry: TimelineEntry,
    onDismiss: () -> Unit,
    onStart: (String) -> Unit,
    onComplete: (String, Int) -> Unit,
    onNotCompleted: (String, Int) -> Unit,
) {
    // A focus round is intentionally a small, calm state: one 25-minute Pomodoro or the
    // remaining scheduled slot, whichever is shorter. The counter itself does not animate.
    val totalSeconds = minOf(25 * 60, (entry.endMinute - entry.startMinute).coerceAtLeast(1) * 60)
    var elapsedSeconds by remember(entry.id) { mutableStateOf(0) }
    LaunchedEffect(entry.id) {
        entry.taskId?.let(onStart)
        while (elapsedSeconds < totalSeconds) {
            delay(1_000)
            elapsedSeconds += 1
        }
    }
    val remainingSeconds = (totalSeconds - elapsedSeconds).coerceAtLeast(0)
    val actualMinutes = ((elapsedSeconds + 59) / 60).coerceAtLeast(1)
    val progress = elapsedSeconds.toFloat() / totalSeconds.toFloat()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "专注中 · ${TimeBlockValidator.formatTime(entry.startMinute)}–${TimeBlockValidator.formatTime(entry.endMinute)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(244.dp)) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 10.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        Text(
                            String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60),
                            style = MaterialTheme.typography.displayLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(entry.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("这一轮只处理这一件事", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    entry.taskId?.let { taskId ->
                        Button(
                            onClick = { onComplete(taskId, actualMinutes); onDismiss() },
                            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("focus-complete"),
                        ) { Text("本轮完成") }
                        OutlinedButton(
                            onClick = { onNotCompleted(taskId, actualMinutes); onDismiss() },
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("focus-not-complete"),
                        ) { Text("暂未完成，继续进行中") }
                    } ?: TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("结束本轮") }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("退出专注") }
                }
            }
        }
    }
}

private fun TaskCategory.shortLabel(): String = when (this) {
    TaskCategory.COURSE -> "课程"
    TaskCategory.EXTRACURRICULAR -> "课外"
    TaskCategory.OFFICE -> "事务"
    TaskCategory.LEISURE -> "生活"
}

@Composable
private fun TimelineCard(
    entry: TimelineEntry,
    now: LocalDateTime,
    compact: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onOpenTask: (String) -> Unit,
    motionDuration: Int,
) {
    val accent = when (entry.kind) {
        TimelineKind.COURSE -> CourseAccent
        TimelineKind.REST -> RestAccent
        TimelineKind.COMMITMENT -> CommitmentAccent
        TimelineKind.TASK -> MaterialTheme.colorScheme.primary
    }
    val phase = ScheduleTimeline.clock(
        entry, now,
        userFinished = entry.taskClosed || (entry.kind != TimelineKind.TASK && !now.isBefore(entry.end)),
    )
    val label = when (entry.kind) {
        TimelineKind.COURSE -> "课程"
        TimelineKind.REST -> "休息"
        TimelineKind.COMMITMENT -> "固定事项"
        TimelineKind.TASK -> "任务"
    }
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(motionDuration))
            .clickable(onClick = onClick).testTag("timeline-${entry.id}"),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(if (compact) 13.dp else 17.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("$label · ${TimeBlockValidator.formatTime(entry.startMinute)}–${TimeBlockValidator.formatTime(entry.endMinute)}",
                style = MaterialTheme.typography.labelSmall, color = accent,
                maxLines = if (compact) 2 else 1, overflow = TextOverflow.Ellipsis)
            Text(entry.title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, maxLines = if (expanded) 3 else 2,
                overflow = TextOverflow.Ellipsis)
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                Text(phase.copyForDisplay(), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                entry.taskId?.let { taskId ->
                    Spacer(Modifier.height(4.dp))
                    Text("打开任务与记录反馈 →",
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable { onOpenTask(taskId) }.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.labelLarge, color = accent)
                }
            } else if (!compact) {
                Text(phase.shortLabel(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun com.swan1127.repland.domain.model.TimelineClock.shortLabel(): String = when (phase) {
    TimelinePhase.UPCOMING -> "${untilStartMinutes} 分钟后开始"
    TimelinePhase.ACTIVE -> "正在进行 · 还剩 ${remainingMinutes} 分钟"
    TimelinePhase.OVERRUN -> "计划已结束 · 超时 ${overtimeMinutes} 分钟"
    TimelinePhase.ENDED -> "已结束"
}

private fun com.swan1127.repland.domain.model.TimelineClock.copyForDisplay(): String = when (phase) {
    TimelinePhase.UPCOMING -> "还没开始，距离开始约 ${untilStartMinutes} 分钟。"
    TimelinePhase.ACTIVE -> "计划进行中：已过 ${elapsedMinutes} 分钟，距离计划结束约 ${remainingMinutes} 分钟。实际完成情况由你确认。"
    TimelinePhase.OVERRUN -> "计划时间已结束 ${overtimeMinutes} 分钟；这不代表你仍在执行，请记录真实情况。"
    TimelinePhase.ENDED -> "已结束。"
}

/** Connected overlap components; lanes are assigned by the domain model. */
private fun overlapGroups(entries: List<TimelineEntry>): List<List<TimelineEntry>> {
    val groups = mutableListOf<MutableList<TimelineEntry>>()
    var maxEnd = -1
    entries.sortedWith(compareBy(TimelineEntry::startMinute, TimelineEntry::endMinute)).forEach { entry ->
        if (groups.isEmpty() || entry.startMinute >= maxEnd) {
            groups += mutableListOf(entry)
            maxEnd = entry.endMinute
        } else {
            groups.last() += entry
            maxEnd = maxOf(maxEnd, entry.endMinute)
        }
    }
    return groups
}
