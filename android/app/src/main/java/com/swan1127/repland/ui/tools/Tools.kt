package com.swan1127.repland.ui.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swan1127.repland.R
import com.swan1127.repland.data.ReviewPoint
import com.swan1127.repland.domain.model.MemoryModel
import com.swan1127.repland.domain.model.RecallGrade
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.ui.components.DesignButton
import com.swan1127.repland.ui.components.DesignOutlinedButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolScaffold(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("tool-close")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

@Composable
private fun ToolCard(title: String?, body: String?, content: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium) }
        body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        content()
    }
}

@Composable
private fun MemoryCapture(tasks: List<Task>, onAddPoint: (Task, String) -> Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var taskMenu by remember { mutableStateOf(false) }
    var selectedTaskId by rememberSaveable { mutableStateOf(tasks.firstOrNull()?.id) }
    var content by rememberSaveable { mutableStateOf("") }
    var hasError by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val selectedTask = tasks.firstOrNull { it.id == selectedTaskId }
    DesignOutlinedButton(
        onClick = { expanded = !expanded; saved = false },
        modifier = Modifier.testTag("memory-capture-toggle"),
    ) { Text(stringResource(R.string.memory_capture_title)) }
    if (saved) {
        Text(
            stringResource(R.string.memory_capture_saved),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (expanded) {
        ToolCard(title = null, body = null) {
            if (tasks.isEmpty()) {
                Text(stringResource(R.string.memory_capture_no_tasks), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.memory_capture_choose_task), style = MaterialTheme.typography.labelLarge)
                Box {
                    DesignOutlinedButton(
                        onClick = { taskMenu = true },
                        modifier = Modifier.fillMaxWidth().testTag("memory-capture-task"),
                    ) { Text(selectedTask?.displayName ?: stringResource(R.string.memory_capture_choose_task)) }
                    DropdownMenu(
                        expanded = taskMenu,
                        onDismissRequest = { taskMenu = false },
                        modifier = Modifier.heightIn(max = 360.dp),
                    ) {
                        tasks.forEach { task ->
                            DropdownMenuItem(
                                text = { Text(task.displayName) },
                                onClick = { selectedTaskId = task.id; taskMenu = false; hasError = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it; hasError = false; saved = false },
                    modifier = Modifier.fillMaxWidth().testTag("memory-capture-content"),
                    label = { Text(stringResource(R.string.memory_capture_content)) },
                    minLines = 3,
                    isError = hasError,
                )
                if (hasError) {
                    Text(
                        stringResource(R.string.memory_capture_duplicate_or_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                DesignButton(
                    onClick = {
                        selectedTask?.let { owner ->
                            if (onAddPoint(owner, content.trim())) {
                                content = ""
                                hasError = false
                                saved = true
                                expanded = false
                            } else {
                                hasError = true
                            }
                        }
                    },
                    enabled = selectedTask != null && content.isNotBlank(),
                    modifier = Modifier.testTag("memory-capture-save"),
                ) { Text(stringResource(R.string.memory_capture_save)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewPointsTool(
    due: List<ReviewPoint>,
    upcomingCount: Int,
    onGrade: (String, RecallGrade) -> Unit,
    onClose: () -> Unit,
    upcomingReviewPoints: List<ReviewPoint> = emptyList(),
    today: LocalDate = LocalDate.now(),
    tasks: List<Task> = emptyList(),
    onAddPoint: ((Task, String) -> Boolean)? = null,
) {
    BackHandler(onBack = onClose)
    var gradedPointIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val duePoints = due.filter { !it.isGraduated && it.isDueOn(today) }.distinctBy(ReviewPoint::id)
    val upcoming = upcomingReviewPoints
        .filter { !it.isGraduated && !it.isDueOn(today) }
        .distinctBy(ReviewPoint::id)
        .sortedBy(ReviewPoint::dueOn)
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    ToolScaffold(title = stringResource(R.string.review_points), onClose = onClose) {
        if (onAddPoint != null) {
            MemoryCapture(tasks = tasks, onAddPoint = onAddPoint)
        }
        Text(
            text = stringResource(R.string.memory_review_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.memory_review_due_heading, duePoints.size),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (duePoints.isEmpty()) {
            ToolCard(
                title = null,
                body = if (upcoming.isEmpty() && upcomingCount == 0) {
                    stringResource(R.string.review_point_none)
                } else {
                    stringResource(R.string.memory_review_early_hint)
                },
            ) {}
        }
        duePoints.forEach { point ->
            MemoryReviewCard(
                point = point,
                today = today,
                dateFormatter = dateFormatter,
                graded = point.id in gradedPointIds,
                early = false,
                onGrade = { grade ->
                    if (point.id !in gradedPointIds) {
                        gradedPointIds = gradedPointIds + point.id
                        onGrade(point.id, grade)
                    }
                },
                onReviewAgain = { gradedPointIds = gradedPointIds - point.id },
            )
        }
        if (upcoming.isNotEmpty()) {
            Text(
                text = stringResource(R.string.memory_review_upcoming_heading, upcoming.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            upcoming.forEach { point ->
                MemoryReviewCard(
                    point = point,
                    today = today,
                    dateFormatter = dateFormatter,
                    graded = point.id in gradedPointIds,
                    early = true,
                    onGrade = { grade ->
                        if (point.id !in gradedPointIds) {
                            gradedPointIds = gradedPointIds + point.id
                            onGrade(point.id, grade)
                        }
                    },
                    onReviewAgain = { gradedPointIds = gradedPointIds - point.id },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryReviewCard(
    point: ReviewPoint,
    today: LocalDate,
    dateFormatter: DateTimeFormatter,
    graded: Boolean,
    early: Boolean,
    onGrade: (RecallGrade) -> Unit,
    onReviewAgain: () -> Unit,
) {
    val memory = point.memoryState()
    ToolCard(title = point.taskTitle, body = point.content) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.memory_review_due_date, point.dueOn.format(dateFormatter)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.memory_review_importance, memory.importance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(
                R.string.memory_review_estimate,
                (MemoryModel.retrievabilityAt(memory, today) * 100).roundToInt(),
                memory.stabilityDays,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (point.memory == null) {
            Text(
                text = stringResource(R.string.memory_review_legacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (early && !graded) {
            Text(
                text = stringResource(R.string.memory_review_early_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (graded) {
            Text(
                text = stringResource(R.string.memory_review_recorded),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            DesignOutlinedButton(
                onClick = onReviewAgain,
                modifier = Modifier.testTag("review-grade-again"),
            ) { Text(stringResource(R.string.memory_review_grade_again)) }
        } else {
            Text(
                text = stringResource(R.string.review_point_prompt),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DesignButton(
                    onClick = { onGrade(RecallGrade.RECALLED) },
                    modifier = Modifier.testTag("review-remembered"),
                ) { Text(stringResource(R.string.review_point_remembered)) }
                DesignOutlinedButton(
                    onClick = { onGrade(RecallGrade.FUZZY) },
                    modifier = Modifier.testTag("review-fuzzy"),
                ) { Text(stringResource(R.string.memory_review_fuzzy)) }
                DesignOutlinedButton(
                    onClick = { onGrade(RecallGrade.FORGOT) },
                    modifier = Modifier.testTag("review-forgotten"),
                ) { Text(stringResource(R.string.review_point_forgotten)) }
            }
        }
    }
}
