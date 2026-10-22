package com.swan1127.repland.ui.tools

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import com.swan1127.repland.R
import com.swan1127.repland.data.AppearanceMode
import com.swan1127.repland.data.KwlEntry
import com.swan1127.repland.data.ReviewPoint
import com.swan1127.repland.domain.model.MemoryModel
import com.swan1127.repland.domain.model.RecallGrade
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.ThemeAccent
import com.swan1127.repland.ui.components.DesignButton
import com.swan1127.repland.ui.components.DesignOutlinedButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * Identifiers for the auxiliary tools. They are reached from the command palette and
 * render as full-screen destinations, which is why the app shell stays at three tabs:
 * a tool costs nothing on screen until it is asked for.
 */
enum class ToolId(val titleRes: Int) {
    APPEARANCE(R.string.appearance_title),
    ICS_EXPORT(R.string.export_ics),
    INSIGHTS(R.string.insights_title),
    SQ3R(R.string.sq3r_title),
    REFLECTION(R.string.reflection_title),
    KWL(R.string.kwl_title),
    REVIEW_POINTS(R.string.review_points),
    SELF_EXPLANATION(R.string.self_explanation_title),
    TIMETABLE_SETS(R.string.timetable_sets),
    IMAGE_EXPORT(R.string.export_image),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolScaffold(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("tool-close"),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Edge-to-edge is on, so `adjustResize` no longer shrinks the window and the IME
                // only overlays it. Without this the bottom of every tool page stays covered by
                // the keyboard — which silently makes later list rows (e.g. the second saved
                // timetable set) impossible to tap, and because the column is not scrollable at
                // that point there is no gesture that recovers it.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

@Composable
private fun ToolCard(
    title: String?,
    body: String?,
    content: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        title?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
        body?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

// ------------------------------------------------------------------------ D6 外观

/**
 * The resource label for an accent.
 *
 * A function rather than a property on the enum, so the domain stays free of `R`. The `when` has
 * no `else`, which is the point: adding an accent without a label is then a compile error rather
 * than a row that renders as the empty string.
 */
@StringRes
fun accentLabelRes(accent: ThemeAccent): Int = when (accent) {
    ThemeAccent.BAMBOO -> R.string.theme_accent_bamboo
    ThemeAccent.INDIGO -> R.string.theme_accent_indigo
    ThemeAccent.PLUM -> R.string.theme_accent_plum
}

@Composable
fun AppearanceTool(
    currentMode: AppearanceMode,
    onSelectMode: (AppearanceMode) -> Unit,
    currentAccent: ThemeAccent,
    onSelectAccent: (ThemeAccent) -> Unit,
    onClose: () -> Unit,
) {
    val modeOptions = listOf(
        AppearanceMode.SYSTEM to R.string.appearance_mode_system,
        AppearanceMode.LIGHT to R.string.appearance_mode_light,
        AppearanceMode.DARK to R.string.appearance_mode_dark,
    )
    ToolScaffold(title = stringResource(R.string.appearance_title), onClose = onClose) {
        ToolCard(
            title = stringResource(R.string.appearance_mode),
            body = stringResource(R.string.appearance_mode_body),
        ) {
            modeOptions.forEach { (mode, labelRes) ->
                AppearanceChoice(
                    label = stringResource(labelRes),
                    selected = mode == currentMode,
                    testTag = "appearance-${mode.name.lowercase()}",
                    onClick = { onSelectMode(mode) },
                )
            }
        }
        ToolCard(
            title = stringResource(R.string.appearance_accent),
            body = stringResource(R.string.appearance_accent_body),
        ) {
            // Driven off `entries` rather than a hand-written list, so a new accent cannot be
            // added to the enum and silently left out of the picker.
            ThemeAccent.entries.forEach { accent ->
                AppearanceChoice(
                    label = stringResource(accentLabelRes(accent)),
                    selected = accent == currentAccent,
                    testTag = "appearance-accent-${accent.name.lowercase()}",
                    onClick = { onSelectAccent(accent) },
                )
            }
        }
    }
}

/** One selectable row: a check when chosen, and the label tinted with the live brand colour. */
@Composable
private fun AppearanceChoice(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selected) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------------- D3 日历导出

@Composable
fun IcsExportTool(
    today: LocalDate,
    segmentCountToday: Int,
    segmentCountAll: Int,
    message: String?,
    messageIsError: Boolean,
    onExportToday: () -> Unit,
    onExportAll: () -> Unit,
    onClose: () -> Unit,
) {
    ToolScaffold(title = stringResource(R.string.export_ics), onClose = onClose) {
        ToolCard(
            title = stringResource(R.string.export_ics_today),
            body = "把 $today 的 $segmentCountToday 段安排放进系统日历。",
        ) {
            Button(
                onClick = onExportToday,
                modifier = Modifier.testTag("export-ics-today"),
            ) { Text(stringResource(R.string.export_ics_today)) }
        }
        ToolCard(
            title = stringResource(R.string.export_ics_all),
            body = "导出当前计划的全部 $segmentCountAll 段，用于备份或换机。",
        ) {
            OutlinedButton(
                onClick = onExportAll,
                modifier = Modifier.testTag("export-ics-all"),
            ) { Text(stringResource(R.string.export_ics_all)) }
        }
        Text(
            text = "时间是墙上时间，导出时不带 Z 后缀——带了会被 UTC 偏移吃掉一小时。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The write happens after the system picker returns, so without this the failure case is
        // completely invisible: the user chose a destination, the write threw, and nothing on
        // screen changed. Failure uses the warning row rather than coloured text so it reads as a
        // problem rather than as another hint.
        message?.let {
            if (messageIsError) {
                WarningHint(it)
            } else {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ------------------------------------------------------------------------- E2 SQ3R

private data class Sq3rStep(val titleRes: Int, val hintRes: Int)

@Composable
fun Sq3rTool(onClose: () -> Unit) {
    val steps = listOf(
        Sq3rStep(R.string.sq3r_survey, R.string.sq3r_survey_hint),
        Sq3rStep(R.string.sq3r_question, R.string.sq3r_question_hint),
        Sq3rStep(R.string.sq3r_read, R.string.sq3r_read_hint),
        Sq3rStep(R.string.sq3r_recite, R.string.sq3r_recite_hint),
        Sq3rStep(R.string.sq3r_review, R.string.sq3r_review_hint),
    )
    // Every card starts collapsed and expands independently: a scaffold you reach for,
    // not a five-step flow you are marched through.
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    ToolScaffold(title = stringResource(R.string.sq3r_title), onClose = onClose) {
        Text(
            text = stringResource(R.string.sq3r_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        steps.forEachIndexed { index, step ->
            val label = stringResource(step.titleRes)
            val open = expanded.contains(label)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable {
                        expanded = if (open) expanded - label else expanded + label
                    }
                    .padding(16.dp)
                    .testTag("sq3r-step-$index"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "${if (open) "▾" else "▸"}  $label",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                if (open) {
                    Text(
                        text = stringResource(step.hintRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------- E4 反思日志

/**
 * Builds the draft the user edits, from evidence already in the database.
 *
 * The point of the feature is that the user is handed a finished paragraph and may simply
 * tap "就这样". So the draft must be specific — durations, names, the actual stuck point —
 * rather than a template with blanks.
 */
fun buildReflectionDraft(
    date: LocalDate,
    logs: List<TaskExecutionLog>,
    tasks: List<Task>,
): String {
    val nameById = tasks.associate { it.id to it.displayName }
    if (logs.isEmpty()) return ""
    val totalMinutes = logs.mapNotNull { it.feedback.actualDurationMinutes }.sum()
    val lines = mutableListOf<String>()
    lines += if (totalMinutes > 0) {
        "今天投入了 ${totalMinutes / 60} 小时 ${totalMinutes % 60} 分："
    } else {
        "今天有 ${logs.size} 条执行记录："
    }
    logs.take(4).forEach { log ->
        val name = nameById[log.taskId] ?: "某个任务"
        val stuck = log.feedback.completionResult?.takeIf { it.isNotBlank() }
        val done = log.feedback.completedContent?.takeIf { it.isNotBlank() }
        val progress = log.feedback.progressPercent
        lines += when {
            stuck != null -> "· $name 卡在 $stuck"
            done != null -> "· $name 推进到 $done"
            progress != null -> "· $name 完成约 $progress%"
            else -> "· $name 有进展"
        }
    }
    val postponed = tasks.filter { it.postponeCount >= 2 }
    if (postponed.isNotEmpty()) {
        val worst = postponed.maxBy { it.postponeCount }
        lines += "明天把「${worst.displayName}」拆小一点，它已经推迟 ${worst.postponeCount} 次了。"
    }
    return lines.joinToString("\n")
}

@Composable
fun ReflectionTool(
    date: LocalDate,
    draft: String,
    saved: String?,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
) {
    var text by rememberSaveable(date) { mutableStateOf(saved ?: draft) }
    ToolScaffold(title = stringResource(R.string.reflection_title), onClose = onClose) {
        if (draft.isBlank() && saved == null) {
            ToolCard(title = null, body = stringResource(R.string.reflection_empty)) {}
        } else {
            Text(
                text = "草稿由今天的执行记录生成，改一改或者直接确认。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reflection-input"),
                minLines = 5,
                label = { Text(stringResource(R.string.reflection_draft_label)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onSave(text); onClose() },
                    modifier = Modifier.testTag("reflection-keep"),
                ) { Text(stringResource(R.string.reflection_keep)) }
                OutlinedButton(
                    onClick = { text = draft },
                    modifier = Modifier.testTag("reflection-reset"),
                ) { Text("还原草稿") }
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("reflection-skip"),
                ) { Text(stringResource(R.string.reflection_skip)) }
            }
        }
    }
}

// -------------------------------------------------------------------------- E1 KWL

/**
 * Seeds the K and W columns so the user only subtracts.
 *
 * K comes from what they already finished in the same category ("一元函数求导" for a
 * calculus chapter), W from the task's own wording. Both are guesses drawn from history,
 * which is why the UI asks the user to cross things out rather than to write.
 */
fun buildKwlDraft(task: Task, history: List<Task>): KwlEntry {
    val known = history
        .filter { it.category == task.category && it.status.name == "COMPLETED" && it.id != task.id }
        .map { it.displayName }
        .distinct()
        .take(3)
    val want = listOf(
        "${task.displayName}里最难的那一步为什么难",
        "怎么判断自己真的会了",
    )
    return KwlEntry(
        taskId = task.id,
        known = known.joinToString("、"),
        want = want.joinToString("\n"),
        learned = "",
    )
}

@Composable
fun KwlTool(
    tasks: List<Task>,
    draftFor: (Task) -> KwlEntry,
    loadFor: (String) -> KwlEntry?,
    onSave: (KwlEntry) -> Unit,
    onClose: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf(tasks.firstOrNull()?.id) }
    val selected = tasks.firstOrNull { it.id == selectedId }
    val entry = remember(selectedId, tasks) {
        selected?.let { loadFor(it.id) ?: draftFor(it) }
    }
    ToolScaffold(title = stringResource(R.string.kwl_title), onClose = onClose) {
        if (selected == null) {
            ToolCard(title = null, body = "还没有任务可以用来生成 KWL。") {}
        } else {
            Text(
                text = stringResource(R.string.kwl_generated),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var known by rememberSaveable(selected.id) { mutableStateOf(entry?.known.orEmpty()) }
            var want by rememberSaveable(selected.id) { mutableStateOf(entry?.want.orEmpty()) }
            var learned by rememberSaveable(selected.id) { mutableStateOf(entry?.learned.orEmpty()) }

            OutlinedTextField(
                value = known,
                onValueChange = { known = it },
                label = { Text(stringResource(R.string.kwl_known)) },
                modifier = Modifier.fillMaxWidth().testTag("kwl-known"),
            )
            OutlinedTextField(
                value = want,
                onValueChange = { want = it },
                label = { Text(stringResource(R.string.kwl_want)) },
                modifier = Modifier.fillMaxWidth().testTag("kwl-want"),
            )
            OutlinedTextField(
                value = learned,
                onValueChange = { learned = it },
                label = { Text(stringResource(R.string.kwl_learned)) },
                modifier = Modifier.fillMaxWidth().testTag("kwl-learned"),
            )
            Button(
                onClick = {
                    onSave(KwlEntry(selected.id, known, want, learned))
                    onClose()
                },
                modifier = Modifier.testTag("kwl-save"),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

// -------------------------------------------------------------------- D8 复习点

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewPointsTool(
    due: List<ReviewPoint>,
    upcomingCount: Int,
    onGrade: (String, RecallGrade) -> Unit,
    onClose: () -> Unit,
    upcomingReviewPoints: List<ReviewPoint> = emptyList(),
    today: LocalDate = LocalDate.now(),
) {
    var gradedPointIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val duePoints = due.filter { !it.isGraduated && it.isDueOn(today) }.distinctBy(ReviewPoint::id)
    val upcoming = upcomingReviewPoints
        .filter { !it.isGraduated && !it.isDueOn(today) }
        .distinctBy(ReviewPoint::id)
        .sortedBy(ReviewPoint::dueOn)
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    ToolScaffold(title = stringResource(R.string.review_points), onClose = onClose) {
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

// ------------------------------------------------------------------ E3 自我解释

/**
 * One finished task the user can still explain in their own words.
 *
 * [clue] is what they recorded when they finished (or the progress they reported). It exists
 * so the question is answerable without re-reading anything — a prompt with no context is a
 * chore, a prompt with your own note attached is a nudge.
 */
data class ExplanationSlot(
    val logKey: String,
    val taskTitle: String,
    val clue: String?,
    val existing: String?,
)

/**
 * Asks the user to explain a finished task out loud, in one sentence.
 *
 * Note the wording: "how would you explain this to a classmate", not "did you understand
 * this". Explaining is the act that produces understanding, and what gets written here is
 * also what seeds the day 1/2/4/7/15 review points.
 */
@Composable
fun SelfExplanationTool(
    slots: List<ExplanationSlot>,
    onSave: (ExplanationSlot, String) -> Unit,
    onClose: () -> Unit,
) {
    ToolScaffold(title = stringResource(R.string.self_explanation_title), onClose = onClose) {
        Text(
            text = stringResource(R.string.self_explanation_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (slots.isEmpty()) {
            ToolCard(title = null, body = stringResource(R.string.self_explanation_empty)) {}
        } else {
            slots.forEachIndexed { index, slot ->
                var text by rememberSaveable(slot.logKey) { mutableStateOf(slot.existing.orEmpty()) }
                var saved by rememberSaveable(slot.logKey) { mutableStateOf(false) }
                ToolCard(title = slot.taskTitle, body = slot.clue) {
                    Text(
                        text = stringResource(R.string.self_explanation_prompt),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            saved = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("explain-input-$index"),
                        minLines = 2,
                        placeholder = { Text(stringResource(R.string.self_explanation_optional)) },
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                onSave(slot, text)
                                saved = true
                            },
                            enabled = text.isNotBlank(),
                            modifier = Modifier.testTag("explain-save-$index"),
                        ) { Text(stringResource(R.string.self_explanation_save)) }
                        if (saved) {
                            Text(
                                text = stringResource(R.string.self_explanation_seeded),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ D4 图片导出

/**
 * Renders the plan or the weekly timetable as a PNG and writes it into the gallery.
 *
 * The drawing happens on a real Canvas instead of screenshotting the UI, so the output is
 * legible at whatever size it is later viewed at and carries no status bar, scroll position
 * or dark-mode theme. Nothing leaves the device — MediaStore is a local write.
 */
@Composable
fun ImageExportTool(
    planSegmentCount: Int,
    timetableBlockCount: Int,
    message: String?,
    onExportPlan: () -> Unit,
    onExportTimetable: () -> Unit,
    onClose: () -> Unit,
) {
    ToolScaffold(title = stringResource(R.string.export_image), onClose = onClose) {
        Text(
            text = stringResource(R.string.export_image_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToolCard(
            title = stringResource(R.string.export_image_plan),
            body = if (planSegmentCount > 0) {
                "当前计划有 $planSegmentCount 段安排。"
            } else {
                stringResource(R.string.export_image_empty)
            },
        ) {
            Button(
                onClick = onExportPlan,
                enabled = planSegmentCount > 0,
                modifier = Modifier.testTag("export-image-plan"),
            ) { Text(stringResource(R.string.export_image_plan)) }
        }
        ToolCard(
            title = stringResource(R.string.export_image_timetable),
            body = if (timetableBlockCount > 0) {
                "每周有 $timetableBlockCount 段固定时段。"
            } else {
                stringResource(R.string.export_image_empty)
            },
        ) {
            OutlinedButton(
                onClick = onExportTimetable,
                enabled = timetableBlockCount > 0,
                modifier = Modifier.testTag("export-image-timetable"),
            ) { Text(stringResource(R.string.export_image_timetable)) }
        }
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ---------------------------------------------------------------------- D2 课表集

/** A saved timetable flattened for display, so this file needs no knowledge of the store. */
data class TimetableSetRow(
    val name: String,
    val blockCount: Int,
    val active: Boolean,
)

/**
 * Saves the current timetable under a name, and switches between saved ones.
 *
 * The invariant is that exactly one set is active at a time — never a union of several. Two
 * overlapping timetables would stack hard constraints until the planner has no legal slot
 * left, which is precisely the failure the "sets" idea exists to prevent.
 */
@Composable
fun TimetableSetsTool(
    sets: List<TimetableSetRow>,
    currentBlockCount: Int,
    message: String?,
    onSaveCurrent: (String) -> Unit,
    onActivate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    ToolScaffold(title = stringResource(R.string.timetable_sets), onClose = onClose) {
        Text(
            text = stringResource(R.string.timetable_set_switch_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var name by rememberSaveable { mutableStateOf("") }
        ToolCard(
            title = stringResource(R.string.timetable_set_from_current),
            body = "当前每周有 $currentBlockCount 段固定时段。",
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.timetable_set_name)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("timetable-set-name"),
            )
            Button(
                onClick = {
                    onSaveCurrent(name)
                    name = ""
                },
                enabled = name.isNotBlank() && currentBlockCount > 0,
                modifier = Modifier.testTag("timetable-set-save"),
            ) { Text(stringResource(R.string.save)) }
        }
        if (sets.isEmpty()) {
            ToolCard(title = null, body = stringResource(R.string.timetable_set_none)) {}
        } else {
            // Sets exist but none describes the live timetable: say so, rather than leaving the
            // user to guess why no row is marked. This is the normal state after importing a
            // course or editing a block by hand.
            if (sets.none { it.active }) {
                WarningHint(stringResource(R.string.timetable_set_drifted))
            }
            sets.forEach { row ->
                ToolCard(title = row.name, body = "${row.blockCount} 段固定时段") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (row.active) {
                            Text(
                                text = stringResource(R.string.timetable_set_active),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Button(
                                onClick = { onActivate(row.name) },
                                modifier = Modifier.testTag("timetable-set-activate-${row.name}"),
                            ) { Text(stringResource(R.string.timetable_set_activate)) }
                        }
                        TextButton(
                            onClick = { onDelete(row.name) },
                            modifier = Modifier.testTag("timetable-set-delete-${row.name}"),
                        ) { Text(stringResource(R.string.timetable_set_delete)) }
                    }
                }
            }
        }
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Renders whichever tool the palette asked for. */
@Composable
fun ToolHost(
    tool: ToolId,
    today: LocalDate,
    appearanceMode: AppearanceMode,
    onAppearanceChange: (AppearanceMode) -> Unit,
    themeAccent: ThemeAccent,
    onThemeAccentChange: (ThemeAccent) -> Unit,
    segmentCountToday: Int,
    segmentCountAll: Int,
    icsMessage: String?,
    icsMessageIsError: Boolean,
    onExportToday: () -> Unit,
    onExportAll: () -> Unit,
    reflectionDraft: String,
    savedReflection: String?,
    onSaveReflection: (String) -> Unit,
    tasks: List<Task>,
    insightLogs: List<TaskExecutionLog>,
    reviewPointDates: List<LocalDate>,
    kwlDraftFor: (Task) -> KwlEntry,
    kwlLoadFor: (String) -> KwlEntry?,
    onSaveKwl: (KwlEntry) -> Unit,
    dueReviewPoints: List<ReviewPoint>,
    upcomingReviewCount: Int,
    onGradeReviewPoint: (String, RecallGrade) -> Unit,
    explanationSlots: List<ExplanationSlot>,
    onSaveExplanation: (ExplanationSlot, String) -> Unit,
    planSegmentCount: Int,
    timetableBlockCount: Int,
    imageMessage: String?,
    onExportImagePlan: () -> Unit,
    onExportImageTimetable: () -> Unit,
    timetableSetRows: List<TimetableSetRow>,
    timetableSetMessage: String?,
    onSaveTimetableSet: (String) -> Unit,
    onActivateTimetableSet: (String) -> Unit,
    onDeleteTimetableSet: (String) -> Unit,
    onClose: () -> Unit,
    upcomingReviewPoints: List<ReviewPoint> = emptyList(),
) {
    when (tool) {
        ToolId.APPEARANCE -> AppearanceTool(
            currentMode = appearanceMode,
            onSelectMode = onAppearanceChange,
            currentAccent = themeAccent,
            onSelectAccent = onThemeAccentChange,
            onClose = onClose,
        )
        ToolId.ICS_EXPORT -> IcsExportTool(
            today = today,
            segmentCountToday = segmentCountToday,
            segmentCountAll = segmentCountAll,
            message = icsMessage,
            messageIsError = icsMessageIsError,
            onExportToday = onExportToday,
            onExportAll = onExportAll,
            onClose = onClose,
        )

        ToolId.SQ3R -> Sq3rTool(onClose)
        ToolId.INSIGHTS -> InsightsTool(
            tasks = tasks,
            logs = insightLogs,
            reviewPointDates = reviewPointDates,
            today = today,
            onClose = onClose,
        )
        ToolId.REFLECTION -> ReflectionTool(
            date = today,
            draft = reflectionDraft,
            saved = savedReflection,
            onSave = onSaveReflection,
            onClose = onClose,
        )

        ToolId.KWL -> KwlTool(
            tasks = tasks,
            draftFor = kwlDraftFor,
            loadFor = kwlLoadFor,
            onSave = onSaveKwl,
            onClose = onClose,
        )

        ToolId.REVIEW_POINTS -> ReviewPointsTool(
            due = dueReviewPoints,
            upcomingCount = upcomingReviewCount,
            onGrade = onGradeReviewPoint,
            onClose = onClose,
            upcomingReviewPoints = upcomingReviewPoints,
            today = today,
        )

        ToolId.SELF_EXPLANATION -> SelfExplanationTool(
            slots = explanationSlots,
            onSave = onSaveExplanation,
            onClose = onClose,
        )

        ToolId.IMAGE_EXPORT -> ImageExportTool(
            planSegmentCount = planSegmentCount,
            timetableBlockCount = timetableBlockCount,
            message = imageMessage,
            onExportPlan = onExportImagePlan,
            onExportTimetable = onExportImageTimetable,
            onClose = onClose,
        )

        ToolId.TIMETABLE_SETS -> TimetableSetsTool(
            sets = timetableSetRows,
            currentBlockCount = timetableBlockCount,
            message = timetableSetMessage,
            onSaveCurrent = onSaveTimetableSet,
            onActivate = onActivateTimetableSet,
            onDelete = onDeleteTimetableSet,
            onClose = onClose,
        )
    }
}

/** Small helper so callers can show a warning-toned row without pulling in an icon import. */
@Composable
fun WarningHint(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
