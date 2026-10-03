package com.swan1127.repland.ui.agent

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.swan1127.repland.domain.model.ArrangementAssistantInterpreter
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceResult
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.ArrangementCandidate
import com.swan1127.repland.domain.model.ArrangementClarification
import com.swan1127.repland.domain.model.ArrangementIntent
import com.swan1127.repland.domain.model.ArrangementPlacementSource
import com.swan1127.repland.domain.model.ArrangementTimeHint
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskName
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import com.swan1127.repland.domain.model.AssistantTaskProposal
import com.swan1127.repland.domain.model.AssistantWorkspace
import com.swan1127.repland.domain.model.RhythmTrack
import com.swan1127.repland.ui.components.PlannerIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

private typealias AgentTaskProposal = AssistantTaskProposal

/**
 * Conversation-shaped planning: natural language becomes a disposable visual
 * draft over the user's actual day. Nothing writes until explicit confirmation.
 */
@Composable
fun AgentCenterScreen(
    activeDate: LocalDate = LocalDate.now(),
    occupiedEntries: List<TimelineEntry> = emptyList(),
    canRefineWithAi: Boolean = false,
    onRefineWithAi: suspend (String, LocalDate, List<TimelineEntry>) -> ArrangementAssistantAdviceResult = { _, _, _ ->
        ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
    },
    onSaveTasks: (List<TaskDraft>) -> Unit,
    onPlaceTask: (taskId: String, startMinute: Int, endMinute: Int, trackId: String) -> Unit,
    onOpenTimeStudio: () -> Unit,
    onConfirmBatch: ((List<TaskDraft>, List<PlannedSegment>, () -> Unit) -> Unit)? = null,
    onFormulatePlan: () -> Unit = {},
    canFormulatePlan: Boolean = true,
    onArrangeExistingToday: () -> Unit = {},
    initialWorkspace: AssistantWorkspace? = null,
    onWorkspaceChanged: (AssistantWorkspace) -> Unit = {},
    isSaving: Boolean = false,
    contextRevision: String? = null,
    providerRevision: Long? = null,
    availableTracks: List<RhythmTrack> = emptyList(),
    hasExistingTasks: Boolean = true,
    onAddTask: () -> Unit = {},
    hasAvailability: Boolean = true,
    onConfigureAvailability: () -> Unit = {},
) {
    var prompt by rememberSaveable { mutableStateOf(initialWorkspace?.prompt.orEmpty()) }
    var proposals by remember { mutableStateOf(initialWorkspace?.proposals.orEmpty()) }
    var intent by remember { mutableStateOf(initialWorkspace?.intent) }
    var selectedIntent by rememberSaveable { mutableStateOf(initialWorkspace?.selectedIntent) }
    var draftDate by remember { mutableStateOf(initialWorkspace?.date ?: activeDate) }
    var draftRevision by remember { mutableStateOf(initialWorkspace?.sourceRevision) }
    var requestVersion by remember { mutableStateOf(0L) }
    fun persistWorkspace() {
        onWorkspaceChanged(AssistantWorkspace(draftDate, prompt, proposals, intent, selectedIntent, draftRevision))
    }
    var transcriptError by rememberSaveable { mutableStateOf(false) }
    var editingProposal by remember { mutableStateOf<AgentTaskProposal?>(null) }
    var isRefining by remember { mutableStateOf(false) }
    LaunchedEffect(canRefineWithAi, activeDate, occupiedEntries, contextRevision, providerRevision) { requestVersion++; isRefining = false }
    var refinementMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val words = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        } else null
        if (words.isNullOrBlank()) transcriptError = true else {
            requestVersion++
            prompt = words
            proposals = emptyList()
            intent = null
            transcriptError = false
            isRefining = false
            persistWorkspace()
        }
    }
    fun previewCandidates(candidates: List<ArrangementCandidate>): List<AgentTaskProposal> =
        parseProposal(candidates, occupiedEntries).map { proposal ->
            if (selectedIntent != ArrangementIntent.CAPTURE_TASKS) proposal else proposal.copy(
                timeHint = ArrangementTimeHint(), placementSource = ArrangementPlacementSource.UNSCHEDULED,
                needsClarification = proposal.needsClarification - ArrangementClarification.TIME,
            )
        }
    val createPreview = {
        requestVersion++
        draftDate = activeDate
        draftRevision = contextRevision
        val interpretation = ArrangementAssistantInterpreter.interpret(prompt)
        proposals = previewCandidates(interpretation.candidates)
        intent = selectedIntent ?: interpretation.intent
        persistWorkspace()
        refinementMessage = if (canRefineWithAi) "AI 正在结合课程、固定事项和空档生成计划…" else "本地先拆分事项；配置 AI 后可基于今日占用提出时段建议。"
        if (canRefineWithAi) {
            val version = requestVersion
            val requestPrompt = prompt
            scope.launch {
                isRefining = true
                val result = onRefineWithAi(requestPrompt, activeDate, occupiedEntries)
                if (version != requestVersion) return@launch
                when (result) {
                    is ArrangementAssistantAdviceResult.Advice -> {
                        proposals = previewCandidates(result.advice.candidates)
                        refinementMessage = "AI 已生成可编辑计划：${result.advice.confidenceLabel}"
                    }
                    is ArrangementAssistantAdviceResult.Unavailable -> {
                        refinementMessage = result.reason.userMessage("已保留拆分后的本地草案。")
                    }
                    is ArrangementAssistantAdviceResult.Failed -> {
                        refinementMessage = result.reason.userMessage("已保留拆分后的本地草案。")
                    }
                }
                isRefining = false
                persistWorkspace()
            }
        }
    }
    val selectWorkflow: (String) -> Unit = { label ->
        requestVersion++
        isRefining = false
        selectedIntent = if (label == "新增事项") ArrangementIntent.CAPTURE_TASKS else ArrangementIntent.ARRANGE_TODAY
        proposals = emptyList()
        intent = selectedIntent
        persistWorkspace()
        if (prompt.isNotBlank()) createPreview()
        else if (selectedIntent == ArrangementIntent.ARRANGE_TODAY && canFormulatePlan) onArrangeExistingToday()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AgentHero(activeDate, occupiedEntries, onOpenTimeStudio)
        Button(onClick = onFormulatePlan, enabled = canFormulatePlan,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("agent-formulate-plan")) {
            Text("制定计划")
        }
        Text("依据现有任务重新排序并生成计划，确认前不会改动当前安排。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!hasExistingTasks) {
            Text("还没有可规划的任务，先添加一件事再制定计划。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onAddTask, modifier = Modifier.testTag("agent-add-first-task")) { Text("添加第一件事") }
        }
        if (hasExistingTasks && !hasAvailability) {
            Text("还没有设置可用时间，补充后才能自动安排时段。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onConfigureAvailability, modifier = Modifier.testTag("agent-configure-availability")) { Text("补充可用时间") }
        }
        AgentComposer(
            prompt = prompt,
            willUseAi = canRefineWithAi,
            onPromptChange = { requestVersion++; isRefining = false; prompt = it; proposals = emptyList(); intent = null; persistWorkspace() },
            onSeed = selectWorkflow,
            onVoice = {
                transcriptError = false
                val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "说出今天需要安排的事")
                }
                runCatching { speechLauncher.launch(speechIntent) }.onFailure { transcriptError = true }
            },
            onPreview = createPreview,
        )
        selectedIntent?.let { selected -> Text(if (selected == ArrangementIntent.CAPTURE_TASKS) "新增事项：确认后只保存任务，不自动排入时段。" else "安排今天：描述要做的事项，先预览再确认。", style = MaterialTheme.typography.bodySmall) }
        if ((draftDate != activeDate || draftRevision != contextRevision) && proposals.isNotEmpty()) {
            Text("日期、任务或时间设置已变化，请重新生成草案后确认。", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = createPreview, modifier = Modifier.testTag("agent-refresh-draft")) { Text("按今天重新生成") }
        }
        if (transcriptError) Text("语音服务暂不可用；可以直接修改文字后继续。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (proposals.isEmpty()) {
            AgentEmptyState(canRefineWithAi)
        } else {
            AgentProposalPanel(
                activeDate = activeDate,
                intent = intent,
                proposals = proposals,
                occupiedEntries = occupiedEntries,
                canRefineWithAi = canRefineWithAi,
                isRefining = isRefining,
                canConfirm = draftDate == activeDate && draftRevision == contextRevision && !isSaving,
                refinementMessage = refinementMessage,
                onRefine = {
                    requestVersion++
                    val version = requestVersion
                    val requestPrompt = prompt
                    scope.launch {
                        isRefining = true
                        refinementMessage = null
                        val result = onRefineWithAi(requestPrompt, activeDate, occupiedEntries)
                        if (version != requestVersion) return@launch
                        when (result) {
                            is ArrangementAssistantAdviceResult.Advice -> {
                                draftDate = activeDate
                                draftRevision = contextRevision
                                proposals = previewCandidates(result.advice.candidates)
                                refinementMessage = "AI 已校对草案：${result.advice.confidenceLabel}"
                            }
                            is ArrangementAssistantAdviceResult.Unavailable -> {
                                refinementMessage = result.reason.userMessage("已保留本地草案。")
                            }
                            is ArrangementAssistantAdviceResult.Failed -> {
                                refinementMessage = result.reason.userMessage("已保留本地草案。")
                            }
                        }
                        isRefining = false
                        persistWorkspace()
                    }
                },
                onRemove = { item -> requestVersion++; isRefining = false; proposals = proposals - item; persistWorkspace() },
                onEdit = { editingProposal = it },
                onConfirm = {
                    val confirmedPrompt = prompt
                    val confirmedProposals = proposals
                    val confirmedIntent = intent
                    val taskDrafts = proposals.map { item ->
                        TaskDraft(
                            id = item.id,
                            displayName = TaskName.fromDescription(item.text),
                            description = item.text,
                            category = item.category,
                            userPriority = TaskPriority.MEDIUM,
                            estimatedDays = 1,
                            totalDurationMinutes = item.durationMinutes,
                            dueDate = null,
                            scheduledForDate = activeDate.takeIf {
                                intent != ArrangementIntent.CAPTURE_TASKS && item.timeHint.explicitStartMinute != null && item.durationMinutes != null
                            },
                        )
                    }
                    val segments = proposals.filter { intent != ArrangementIntent.CAPTURE_TASKS && it.timeHint.explicitStartMinute != null && it.durationMinutes != null }.map { item ->
                        val start = requireNotNull(item.timeHint.explicitStartMinute)
                        PlannedSegment(id = "proposal-${item.id}", taskId = item.id, date = activeDate,
                            startMinute = start, endMinute = start + requireNotNull(item.durationMinutes), trackId = item.trackId)
                    }
                    val clearAfterSave = {
                        if (prompt == confirmedPrompt && proposals == confirmedProposals && intent == confirmedIntent) {
                            requestVersion++; isRefining = false; prompt = ""; proposals = emptyList<AgentTaskProposal>(); intent = null
                        }
                    }
                    if (onConfirmBatch != null) onConfirmBatch(taskDrafts, segments, clearAfterSave)
                    else {
                        onSaveTasks(taskDrafts)
                        segments.forEach { onPlaceTask(it.taskId, it.startMinute, it.endMinute, it.trackId) }
                        clearAfterSave()
                        persistWorkspace()
                    }
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
    editingProposal?.let { proposal ->
        AgentPlacementDialog(
            proposal = proposal,
            allProposals = proposals,
            occupiedEntries = occupiedEntries,
            availableTracks = availableTracks,
            onDismiss = { editingProposal = null },
            onSave = { updated -> requestVersion++; isRefining = false; proposals = proposals.map { if (it.id == updated.id) updated else it }; editingProposal = null; persistWorkspace() },
        )
    }
}

@Composable
private fun AgentHero(activeDate: LocalDate, occupiedEntries: List<TimelineEntry>, onOpenTimeStudio: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("安排助手", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(activeDate.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(onClick = onOpenTimeStudio, modifier = Modifier.size(width = 78.dp, height = 56.dp).testTag("agent-import-timetable"), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(PlannerIcons.Calendar, contentDescription = null, modifier = Modifier.size(19.dp))
                Text("课表", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        ContextPill("今天", PlannerIcons.Schedule)
        ContextPill("已有 ${occupiedEntries.size} 项", PlannerIcons.Calendar)
        ContextPill("先预览，后写入", PlannerIcons.Send)
    }
}

@Composable
private fun ContextPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(50)) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AgentComposer(prompt: String, willUseAi: Boolean, onPromptChange: (String) -> Unit, onSeed: (String) -> Unit, onVoice: () -> Unit, onPreview: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(26.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(42.dp)) {
                    Icon(PlannerIcons.Send, contentDescription = null, modifier = Modifier.padding(11.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("说出你的安排", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (willUseAi) "理解事项，并根据今日固定安排提出可编辑时段。" else "先拆分事项、时间、时长和并行关系。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(value = prompt, onValueChange = onPromptChange, modifier = Modifier.fillMaxWidth().testTag("agent-prompt"), minLines = 3, maxLines = 5, label = { Text("今天想怎么安排？") }, placeholder = { Text("例如：15:00 复习数据结构 90 分钟；同一时间跑步 40 分钟") })
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComposerPreset("安排今天") { onSeed("安排今天") }
                ComposerPreset("新增事项") { onSeed("新增事项") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = onVoice, modifier = Modifier.height(44.dp).testTag("agent-voice-input"), label = { Text("语音输入") }, leadingIcon = { Icon(PlannerIcons.Voice, contentDescription = null, modifier = Modifier.size(18.dp)) }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer))
                Spacer(Modifier.weight(1f))
                Button(onClick = onPreview, enabled = prompt.isNotBlank(), modifier = Modifier.height(44.dp).testTag("agent-preview")) {
                    Icon(PlannerIcons.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(if (willUseAi) "AI 生成计划" else "生成草案")
                }
            }
        }
    }
}

@Composable
private fun ComposerPreset(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(50), modifier = Modifier.testTag("agent-prompt-$label")) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AgentEmptyState(willUseAi: Boolean) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (willUseAi) "先给建议，再由你定案" else "先拆分，再决定时间", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (willUseAi) "AI 会避开今天的课程、固定事项和休息，提出可编辑的时间与轨道建议；确认前不会写入。" else "本地模式只拆分你说的事项；不清楚的时间与时长会留在“待决定”。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun AgentProposalPanel(activeDate: LocalDate, intent: ArrangementIntent?, proposals: List<AgentTaskProposal>, occupiedEntries: List<TimelineEntry>, canRefineWithAi: Boolean, isRefining: Boolean, refinementMessage: String?, onRefine: () -> Unit, onRemove: (AgentTaskProposal) -> Unit, onEdit: (AgentTaskProposal) -> Unit, onConfirm: () -> Unit, canConfirm: Boolean = true) {
    val placed = proposals.filter { it.timeHint.explicitStartMinute != null && it.durationMinutes != null }
    val undecided = proposals - placed.toSet()
    val missing = proposals.flatMap(AgentTaskProposal::needsClarification).toSet()
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(26.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("今天的安排预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(intent.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f), shape = RoundedCornerShape(50)) { Text("${placed.size} 已落位 · ${undecided.size} 待决定", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall) }
            }
            Text("浅色块是已占用时段，彩色块是本次草案。AI 建议的时段会明确标出；点彩色块可改时间、时长或轨道。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (canRefineWithAi) {
                Surface(onClick = onRefine, enabled = !isRefining, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("agent-refine-with-ai")) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(PlannerIcons.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (isRefining) "正在生成计划…" else "重新用 AI 校对计划", style = MaterialTheme.typography.labelLarge)
                            Text("仅发送本次输入和今天已占用时段；AI 只提建议，仍需你确认写入。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                Text("当前为本地即时拆分；配置密钥并开启规划助手后，会结合今天已占用时段生成建议。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            refinementMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer) }
            AgentDraftTimeline(activeDate, proposals, occupiedEntries, onEdit)
            if (undecided.isNotEmpty()) UndecidedProposalShelf(undecided, onEdit, onRemove)
            if (missing.isNotEmpty()) Text("还缺：${missing.joinToString("、") { it.label() }}。点“待决定”逐项补全，也可以直接确认把它们留在事项库。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Button(onClick = onConfirm, enabled = canConfirm && !isRefining, modifier = Modifier.fillMaxWidth().testTag("agent-confirm-tasks")) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("确认创建 ${proposals.size} 项${if (placed.isNotEmpty()) "并放入 ${placed.size} 个时段" else ""}")
            }
            Text("确认后才会创建任务；草案中的时间只会写入你看见的彩色块。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun UndecidedProposalShelf(items: List<AgentTaskProposal>, onEdit: (AgentTaskProposal) -> Unit, onRemove: (AgentTaskProposal) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("待决定", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        items.forEach { proposal ->
            Surface(onClick = { onEdit(proposal) }, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f), shape = RoundedCornerShape(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(proposal.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(proposal.previewLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { onRemove(proposal) }) { Icon(Icons.Outlined.Close, contentDescription = "移除 ${proposal.text}", modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AgentDraftTimeline(activeDate: LocalDate, proposals: List<AgentTaskProposal>, occupiedEntries: List<TimelineEntry>, onEdit: (AgentTaskProposal) -> Unit) {
    val anchored = proposals.filter { it.timeHint.explicitStartMinute != null && it.durationMinutes != null }
    val tracks = (occupiedEntries.map(TimelineEntry::trackId) + listOf("focus", "parallel-2") + anchored.map(AgentTaskProposal::trackId)).distinct()
    val earliest = (occupiedEntries.map(TimelineEntry::startMinute) + anchored.mapNotNull { it.timeHint.explicitStartMinute }).minOrNull() ?: 8 * 60
    val latest = (occupiedEntries.map(TimelineEntry::endMinute) + anchored.mapNotNull { item -> item.timeHint.explicitStartMinute?.plus(item.durationMinutes ?: 0) }).maxOrNull() ?: 20 * 60
    val startHour = ((earliest / 60) - 1).coerceIn(0, 22)
    val endHour = ((latest + 59) / 60 + 1).coerceIn(startHour + 1, 24)
    val hourHeight = 44.dp
    val rulerWidth = 38.dp
    val columnWidth = 118.dp
    val headerHeight = 30.dp
    val canvasHeight = headerHeight + hourHeight * (endHour - startHour)
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(canvasHeight + 20.dp).horizontalScroll(rememberScrollState()).padding(10.dp)) {
            val contentWidth = rulerWidth + columnWidth * tracks.size
            Box(Modifier.width(contentWidth).height(canvasHeight)) {
                tracks.forEachIndexed { index, track ->
                    Surface(modifier = Modifier.padding(start = rulerWidth + columnWidth * index).width(columnWidth - 6.dp).height(24.dp), shape = RoundedCornerShape(8.dp), color = if (track == "focus") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(track.label(), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                (startHour..endHour).forEach { hour ->
                    val y = headerHeight + hourHeight * (hour - startHour)
                    Text(String.format("%02d", hour % 24), Modifier.padding(top = y - 8.dp).width(rulerWidth), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.padding(start = rulerWidth, top = y).width(columnWidth * tracks.size).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)))
                }
                occupiedEntries.forEach { entry ->
                    val trackIndex = tracks.indexOf(entry.trackId).coerceAtLeast(0)
                    val y = headerHeight + hourHeight * ((entry.startMinute - startHour * 60) / 60f)
                    val blockHeight = (hourHeight * ((entry.endMinute - entry.startMinute) / 60f)).coerceAtLeast(29.dp)
                    Surface(modifier = Modifier.padding(start = rulerWidth + columnWidth * trackIndex, top = y).width(columnWidth - 6.dp).heightIn(min = blockHeight).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.padding(6.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatMinute(entry.startMinute), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                anchored.forEach { proposal ->
                    val start = requireNotNull(proposal.timeHint.explicitStartMinute)
                    val trackIndex = tracks.indexOf(proposal.trackId).coerceAtLeast(0)
                    val y = headerHeight + hourHeight * ((start - startHour * 60) / 60f)
                    val blockHeight = (hourHeight * (requireNotNull(proposal.durationMinutes) / 60f)).coerceAtLeast(32.dp)
                    Surface(onClick = { onEdit(proposal) }, modifier = Modifier.padding(start = rulerWidth + columnWidth * trackIndex, top = y).width(columnWidth - 6.dp).heightIn(min = blockHeight).testTag("agent-proposal-${proposal.id}"), color = previewColor(proposal.category), shape = RoundedCornerShape(11.dp)) {
                        Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(proposal.text, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text("${formatMinute(start)} · ${proposal.durationMinutes} 分 · ${proposal.placementSource.label()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (occupiedEntries.isEmpty() && anchored.isEmpty()) Text("还没有落位的事项", modifier = Modifier.padding(start = rulerWidth + 12.dp, top = headerHeight + 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text("${activeDate.format(DateTimeFormatter.ofPattern("M月d日", Locale.SIMPLIFIED_CHINESE))} · 真实时间刻度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
}

@Composable
private fun AgentPlacementDialog(proposal: AgentTaskProposal, allProposals: List<AgentTaskProposal>, occupiedEntries: List<TimelineEntry>, onDismiss: () -> Unit, onSave: (AgentTaskProposal) -> Unit, availableTracks: List<RhythmTrack> = emptyList()) {
    val startingMinute = proposal.timeHint.explicitStartMinute ?: 9 * 60
    var hour by remember(proposal.id) { mutableStateOf((startingMinute / 60).toString()) }
    var minute by remember(proposal.id) { mutableStateOf((startingMinute % 60).toString().padStart(2, '0')) }
    var duration by remember(proposal.id) { mutableStateOf((proposal.durationMinutes ?: 30).toString()) }
    var selectedTrack by remember(proposal.id) { mutableStateOf(proposal.trackId) }
    val tracks = (availableTracks.map { it.id } + occupiedEntries.map(TimelineEntry::trackId) + listOf("focus", "parallel-2", "parallel-3") + allProposals.map(AgentTaskProposal::trackId)).distinct()
    val start = ((hour.toIntOrNull() ?: -1) * 60 + (minute.toIntOrNull() ?: -1)).takeIf { it in 0 until 1_440 }
    val durationMinutes = duration.toIntOrNull()?.takeIf { it in 5..720 }
    val collision = start != null && durationMinutes != null && (
        occupiedEntries.any { entry ->
            start < entry.endMinute && start + durationMinutes > entry.startMinute &&
                (entry.kind in setOf(TimelineKind.COURSE, TimelineKind.REST, TimelineKind.COMMITMENT) || entry.trackId == selectedTrack)
        } || allProposals.any { other ->
            other.id != proposal.id && other.trackId == selectedTrack && other.timeHint.explicitStartMinute != null && other.durationMinutes != null &&
                start < other.timeHint.explicitStartMinute + other.durationMinutes && start + durationMinutes > other.timeHint.explicitStartMinute
        }
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("安排「${proposal.text}」") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("可选任意时刻和轨道。课程、固定事项和休息不可重叠；普通任务可换轨并行。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = hour, onValueChange = { hour = it.filter(Char::isDigit).take(2) }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("时") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
                    Text(":", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(value = minute, onValueChange = { minute = it.filter(Char::isDigit).take(2) }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("分") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = duration, onValueChange = { duration = it.filter(Char::isDigit).take(3) }, modifier = Modifier.weight(1.25f), singleLine = true, label = { Text("分钟") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Text("轨道", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { tracks.forEach { track -> FilterChip(selected = selectedTrack == track, onClick = { selectedTrack = track }, label = { Text(availableTracks.firstOrNull { it.id == track }?.name ?: track.label()) }) } }
                when {
                    start == null || durationMinutes == null -> Text("请输入 00:00–23:59，以及 5–720 分钟。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    start + durationMinutes > 1_440 -> Text("结束时间不能超过当天 24:00。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    collision -> Text("这个时段与课程、固定事项或同轨安排冲突；可改时间或换轨处理普通任务并行。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(proposal.copy(durationMinutes = requireNotNull(durationMinutes), timeHint = ArrangementTimeHint(explicitStartMinute = requireNotNull(start), windowLabel = null), needsClarification = emptySet(), placementSource = ArrangementPlacementSource.USER_EXPLICIT, trackId = selectedTrack)) }, enabled = start != null && durationMinutes != null && start + durationMinutes <= 1_440 && !collision) { Text("更新草案") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun parseProposal(candidates: List<ArrangementCandidate>, occupiedEntries: List<TimelineEntry>): List<AgentTaskProposal> {
    val allocated = mutableListOf<AgentTaskProposal>()
    candidates.forEach { candidate ->
        var start = candidate.timeHint.explicitStartMinute
        val duration = candidate.durationMinutes
        var source = candidate.placementSource
        var track = candidate.preferredTrackId ?: "focus"
        if (start != null && duration != null && source == ArrangementPlacementSource.AI_SUGGESTED) {
            val placement = findSafeAiPlacement(start, duration, candidate.preferredTrackId, occupiedEntries, allocated)
            if (placement == null) {
                start = null
                source = ArrangementPlacementSource.UNSCHEDULED
            } else {
                start = placement.startMinute
                track = placement.trackId
            }
        } else if (start != null && duration != null) {
            // Explicit user times stay where the user said. We only choose a
            // non-overlapping visual lane for ordinary simultaneous work.
            track = chooseTrackAt(start, duration, candidate.preferredTrackId, occupiedEntries, allocated, rejectHardBusy = false)
                ?: (candidate.preferredTrackId ?: "focus")
            if (source == ArrangementPlacementSource.UNSCHEDULED) source = ArrangementPlacementSource.USER_EXPLICIT
        }
        val hint = candidate.timeHint.copy(explicitStartMinute = start)
        val missing = candidate.needsClarification.toMutableSet().apply {
            if (start == null) add(ArrangementClarification.TIME) else remove(ArrangementClarification.TIME)
            if (duration == null) add(ArrangementClarification.DURATION) else remove(ArrangementClarification.DURATION)
        }
        allocated += AgentTaskProposal(
            id = UUID.randomUUID().toString(),
            text = candidate.title,
            category = candidate.category,
            durationMinutes = duration,
            timeHint = hint,
            needsClarification = missing,
            placementSource = source,
            trackId = track,
        )
    }
    return allocated
}

private data class SuggestedPlacement(val startMinute: Int, val trackId: String)

/**
 * The model proposes a start, but the client verifies it against real fixed
 * commitments. If that point became unavailable, it searches forward in
 * five-minute steps and never uses a second track to tunnel through a class.
 */
private fun findSafeAiPlacement(
    preferredStart: Int,
    duration: Int,
    preferredTrackId: String?,
    occupiedEntries: List<TimelineEntry>,
    allocated: List<AgentTaskProposal>,
): SuggestedPlacement? {
    val starts = buildList {
        addAll(preferredStart.coerceIn(8 * 60, 22 * 60 - duration)..(22 * 60 - duration) step 5)
        addAll(8 * 60 until preferredStart.coerceIn(8 * 60, 22 * 60 - duration) step 5)
    }.distinct()
    return starts.firstNotNullOfOrNull { start ->
        chooseTrackAt(start, duration, preferredTrackId, occupiedEntries, allocated, rejectHardBusy = true)
            ?.let { track -> SuggestedPlacement(start, track) }
    }
}

private fun chooseTrackAt(
    start: Int,
    duration: Int,
    preferredTrackId: String?,
    occupiedEntries: List<TimelineEntry>,
    allocated: List<AgentTaskProposal>,
    rejectHardBusy: Boolean,
): String? {
    val end = start + duration
    if (rejectHardBusy && occupiedEntries.any { entry ->
            entry.kind in setOf(TimelineKind.COURSE, TimelineKind.REST, TimelineKind.COMMITMENT) && start < entry.endMinute && end > entry.startMinute
        }
    ) return null
    val tracks = listOfNotNull(preferredTrackId) + listOf("focus") + (2..8).map { "parallel-$it" } + occupiedEntries.map(TimelineEntry::trackId)
    return tracks.distinct().firstOrNull { candidateTrack ->
        occupiedEntries.none { entry -> entry.trackId == candidateTrack && start < entry.endMinute && end > entry.startMinute } &&
            allocated.none { other ->
                other.trackId == candidateTrack && other.timeHint.explicitStartMinute != null && other.durationMinutes != null &&
                    start < other.timeHint.explicitStartMinute + other.durationMinutes && end > other.timeHint.explicitStartMinute
            }
    }
}

private fun AgentTaskProposal.previewLabel(): String = buildList {
    add(timeHint.explicitStartMinute?.let(::formatMinute) ?: timeHint.windowLabel ?: "时间待定")
    add(durationMinutes?.let { "$it 分钟" } ?: "时长待定")
    add(placementSource.label())
}.joinToString(" · ")

private fun ArrangementPlacementSource.label(): String = when (this) {
    ArrangementPlacementSource.USER_EXPLICIT -> "用户指定"
    ArrangementPlacementSource.AI_SUGGESTED -> "AI 建议"
    ArrangementPlacementSource.UNSCHEDULED -> "待决定"
}

private fun AiAdvisorFailureReason.userMessage(fallback: String): String = when (this) {
    AiAdvisorFailureReason.CONFIGURATION_CHANGED -> "模型配置已变化，请重新生成；$fallback"
    AiAdvisorFailureReason.INVALID_RESPONSE -> "AI 的回复格式不完整，已自动保留本地草案；可点“重新用 AI 校对计划”重试。"
    AiAdvisorFailureReason.TIMEOUT -> "AI 响应超时，$fallback"
    AiAdvisorFailureReason.TRANSPORT_FAILURE -> "无法连接 AI 服务，请检查网络、接口地址和模型名；$fallback"
    AiAdvisorFailureReason.AUTHENTICATION_FAILURE -> "AI 拒绝了鉴权，请检查密钥所属服务区与密钥状态；$fallback"
    AiAdvisorFailureReason.RATE_LIMITED -> "AI 当前触发了请求额度或频率限制，请稍后重试；$fallback"
    AiAdvisorFailureReason.REMOTE_FAILURE -> "AI 服务暂时异常，请稍后重试；$fallback"
    AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED -> "请先在“我的”配置接口、模型与密钥；$fallback"
    AiAdvisorFailureReason.DISABLED -> "规划助手尚未开启；$fallback"
}

private fun ArrangementIntent?.label(): String = when (this) {
    ArrangementIntent.ARRANGE_TODAY -> "识别为：安排今天"
    ArrangementIntent.CAPTURE_TASKS -> "识别为：收进事项库"
    ArrangementIntent.REPLAN -> "识别为：先生成可编辑调整草案"
    ArrangementIntent.REVIEW -> "识别为：先整理成可回看的事项"
    null -> "可继续逐项调整"
}

private fun ArrangementClarification.label(): String = when (this) {
    ArrangementClarification.TIME -> "什么时候安排"
    ArrangementClarification.DURATION -> "预计多久"
}

private fun String.label(): String = when (this) {
    "focus" -> "专注"
    "course" -> "课程"
    "fixed" -> "固定"
    "rest" -> "休息"
    "parallel-2" -> "并行 2"
    "parallel-3" -> "并行 3"
    else -> removePrefix("parallel-").toIntOrNull()?.let { "并行 $it" } ?: this
}

private fun previewColor(category: TaskCategory): Color = when (category) {
    TaskCategory.COURSE -> Color(0xFFB7C9F5)
    TaskCategory.EXTRACURRICULAR -> Color(0xFFAFE2D2)
    TaskCategory.OFFICE -> Color(0xFFF1C78D)
    TaskCategory.LEISURE -> Color(0xFFDCC1EB)
}

private fun formatMinute(minute: Int): String = String.format("%02d:%02d", minute / 60, minute % 60)
