package com.swan1127.repland.ui.agent

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.swan1127.repland.data.AgentProfileStore
import com.swan1127.repland.domain.model.AgentChatModel
import com.swan1127.repland.domain.model.AgentProfile
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceResult
import com.swan1127.repland.domain.model.ArrangementClarification
import com.swan1127.repland.domain.model.ArrangementPlacementSource
import com.swan1127.repland.domain.model.GroupChatEngine
import com.swan1127.repland.domain.model.GroupChatEvent
import com.swan1127.repland.domain.model.GroupChatRequest
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskName
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.ToolOutcome
import com.swan1127.repland.domain.model.ToolTrace
import com.swan1127.repland.domain.model.UserProfile
import com.swan1127.repland.ui.components.PlannerIcons
import com.swan1127.repland.ui.components.DesignButton
import com.swan1127.repland.ui.theme.LocalReducedMotion
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The one-sentence entry as a group chat.
 *
 * The user talks to a small team instead of a single assistant: each role has an avatar, a
 * self-intro and a catchphrase; typing `@name` addresses one of them directly, and
 * otherwise the planner coordinates everyone. Every member's line is produced by the
 * provider — nothing here writes a sentence locally — and each message carries the tools
 * that actually ran, so the conversation is a trace rather than a performance.
 *
 * Messages are revealed with a typewriter animation after the full text has arrived. That
 * is a deliberate substitution for token streaming, which the transport cannot do: it
 * returns one complete response body with no SSE channel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatSheet(
    activeDate: LocalDate,
    occupiedEntries: List<TimelineEntry>,
    canRefineWithAi: Boolean,
    agentChat: AgentChatModel,
    onRefineWithAi: suspend (String, LocalDate, List<TimelineEntry>) -> ArrangementAssistantAdviceResult,
    onSubmitTasks: suspend (List<TaskDraft>, List<AgentTaskPlacement>) -> AgentSubmissionResult,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { AgentProfileStore(context) }
    val uiStore = remember { AgentUiStore(context) }
    var receipts by remember { mutableStateOf(uiStore.receipts()) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var submittingPlanId by remember { mutableStateOf<String?>(null) }
    var agents by remember { mutableStateOf(store.loadAgents()) }
    var user by remember { mutableStateOf(store.loadUser()) }
    val planner = agents.firstOrNull { it.isPlanner } ?: agents.last()

    var messages by remember {
        mutableStateOf(
            // Belt and braces: if an older session somehow kept a typing bubble, drop it here
            // too. Restoring one means restoring a promise nobody will keep.
            GroupChatSession.items.filterNot { it is ChatItem.Typing }.ifEmpty {
                val welcome = "大家好，我是${planner.name}。@ 任意成员可以直接找它，或者直接把需求说出来，我来安排。"
                listOf<ChatItem>(ChatItem.Agent("welcome", planner.id, welcome, emptyList()))
            },
        )
    }
    var input by rememberSaveable { mutableStateOf(uiStore.loadDraft("group")) }
    var busy by remember { mutableStateOf(false) }
    var seq by remember { mutableIntStateOf(GroupChatSession.nextId) }
    var job by remember { mutableStateOf<Job?>(null) }
    var activeRequestId by remember { mutableStateOf<Int?>(null) }
    var showManagement by remember { mutableStateOf(false) }
    var voiceMode by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val reducedMotion = LocalReducedMotion.current

    LaunchedEffect(input) { uiStore.saveDraft("group", input) }

    // Closing and reopening the sheet must not erase the conversation: a chat that resets
    // every time the sheet is dismissed is not a chat. The list lives for the process.
    //
    // A typing bubble is deliberately NOT persisted. It is a claim that someone is working
    // right now, and that someone is a coroutine that dies with the sheet — restoring it
    // produces an "正在处理…" that can never resolve, which is the one state a user must
    // never see.
    LaunchedEffect(messages) {
        GroupChatSession.items = messages.filterNot { it is ChatItem.Typing }
        GroupChatSession.nextId = seq
    }

    // Follow the stream: every new agent message or plan keeps the newest item in view.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    /**
     * Types a finished message out character by character.
     *
     * The provider returns the whole string in one response — the transport has no SSE
     * channel — so this is an animation, not a stream. It is called what it is: the timing
     * is a presentation choice, the text is verbatim and complete before the first
     * character appears.
     */
    suspend fun reveal(id: String, agentId: String, text: String, tools: List<ToolTrace>) {
        messages = messages.filterNot { it is ChatItem.Typing } +
            ChatItem.Agent(id, agentId, text, tools, revealed = 0, toolsVisible = false)
        val total = text.length
        // ~48 frames whatever the length: a long report must not type for five seconds.
        val step = if (reducedMotion) maxOf(1, total) else maxOf(1, total / 24)
        var shown = 0
        while (shown < total) {
            if (!reducedMotion) delay(14)
            shown = minOf(total, shown + step)
            messages = messages.map { item ->
                if (item.id == id && item is ChatItem.Agent) item.copy(revealed = shown) else item
            }
        }
        // The tool chips land last, so they read as "what it did to produce that".
        messages = messages.map { item ->
            if (item.id == id && item is ChatItem.Agent) item.copy(toolsVisible = true) else item
        }
    }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isBlank() || busy || submittingPlanId != null) return
        keyboard?.hide()
        busy = true
        val requestId = seq++
        activeRequestId = requestId
        messages = messages + ChatItem.User("u-${seq++}", text)
        job = scope.launch {
            var failed = false
            try {
            GroupChatEngine().run(
                GroupChatRequest(
                    utterance = text,
                    date = activeDate,
                    occupiedEntries = occupiedEntries,
                    agents = agents,
                    cloudEnabled = canRefineWithAi,
                    chat = agentChat,
                    refine = onRefineWithAi,
                ),
            ) { event ->
                when (event) {
                    is GroupChatEvent.AgentTyping ->
                        messages = messages + ChatItem.Typing("t-${seq++}", event.agentId)

                    is GroupChatEvent.AgentSaid ->
                        reveal("a-${seq++}", event.agentId, event.text, event.tools)

                    is GroupChatEvent.PlanReady ->
                        messages = messages + ChatItem.Plan("p-${seq++}", parseProposal(event.candidates, occupiedEntries), date = activeDate)

                    is GroupChatEvent.Failed -> {
                        failed = true
                        messages = messages.filterNot { it is ChatItem.Typing } +
                            ChatItem.Notice("f-${seq++}", event.reason, event.agentName)
                    }

                    else -> Unit
                }
            }
                if (!failed && input.trim() == text) input = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                messages = messages + ChatItem.Error("f-${seq++}", "本次请求未完成，输入已保留，可以重试。")
            } finally {
                if (activeRequestId == requestId) {
                    messages = messages.filterNot { it is ChatItem.Typing }
                    busy = false
                    job = null
                    activeRequestId = null
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        activeRequestId = null
        busy = false
        messages = messages.filterNot { it is ChatItem.Typing }
    }

    fun confirm(planId: String, proposals: List<AgentTaskProposal>, proposalDate: LocalDate) {
        if (proposals.isEmpty() || submittingPlanId != null || busy) return
        val drafts = proposals.map { item ->
            TaskDraft(
                id = item.id,
                displayName = TaskName.fromDescription(item.text),
                description = item.text,
                category = item.category,
                userPriority = item.priority ?: TaskPriority.MEDIUM,
                estimatedDays = 1,
                totalDurationMinutes = item.durationMinutes,
                dueDate = null,
                scheduledForDate = proposalDate.takeIf { item.timeHint.explicitStartMinute != null && item.durationMinutes != null },
            )
        }
        val placements = proposals.filter { it.timeHint.explicitStartMinute != null && it.durationMinutes != null }.map { item ->
            val start = requireNotNull(item.timeHint.explicitStartMinute)
            AgentTaskPlacement(item.id, start, start + requireNotNull(item.durationMinutes), item.trackId, proposalDate)
        }
        submittingPlanId = planId
        scope.launch {
            try {
                when (val result = onSubmitTasks(drafts, placements)) {
                    is AgentSubmissionResult.Success -> {
                        uiStore.record(result.receipt)
                        receipts = uiStore.receipts()
                        messages = messages.map { if (it.id == planId) ChatItem.Receipt(planId, result.receipt) else it }
                    }
                    is AgentSubmissionResult.Failure -> {
                        result.receipt?.let { uiStore.record(it); receipts = uiStore.receipts() }
                        messages = messages.map { item ->
                            if (item is ChatItem.Plan && item.id == planId) item.copy(
                                proposals = item.proposals.map { proposal -> proposal.copy(id = result.savedTaskIds[proposal.id] ?: proposal.id) },
                                error = result.message,
                                receipt = result.receipt,
                            ) else item
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                messages = messages.map { if (it is ChatItem.Plan && it.id == planId) it.copy(error = "未确认写入结果，草案已保留，请重试。") else it }
            } finally {
                submittingPlanId = null
            }
        }
    }

    val systemVoiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val words = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        } else null
        if (!words.isNullOrBlank()) {
            input = words
            send(words)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            voiceMode = true
        } else {
            // No microphone permission: use the system recogniser, which owns its own dialog.
            runCatching {
                systemVoiceLauncher.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    },
                )
            }
        }
    }

    val startVoice: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        when {
            !isInAppVoiceAvailable(context) -> runCatching {
                systemVoiceLauncher.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    },
                )
            }

            granted -> voiceMode = true
            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (submittingPlanId == null) { stop(); onDismiss() } },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Surface(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 8.dp).width(36.dp).height(4.dp).background(
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(2.dp),
                ),
            ) {}
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GroupHeader(
                agents = agents,
                canRefineWithAi = canRefineWithAi,
                onManage = { showManagement = true },
                onDismiss = { stop(); onDismiss() },
                canDismiss = submittingPlanId == null,
            )
            TextButton(onClick = { showHistory = true }, modifier = Modifier.testTag("group-receipt-history")) { Text("最近变更 · ${receipts.size}") }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(messages, key = { it.id }) { item ->
                    when (item) {
                        is ChatItem.User -> UserBubble(user = user, text = item.text)
                        is ChatItem.Typing -> AgentTypingRow(agent = agentOf(item.agentId, agents))
                        is ChatItem.Agent -> AgentBubble(
                            agent = agentOf(item.agentId, agents),
                            text = item.text.take(item.revealed),
                            typing = item.revealed < item.text.length,
                            tools = if (item.toolsVisible) item.tools else emptyList(),
                        )
                        is ChatItem.Notice -> NoticeBubble(item.reason, item.agentName)
                        is ChatItem.Error -> Text(item.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        is ChatItem.Receipt -> AgentReceiptCard(item.receipt)
                        is ChatItem.Plan -> PlanBubble(
                            proposals = item.proposals,
                            isSubmitting = submittingPlanId == item.id,
                            enabled = submittingPlanId == null && !busy,
                            error = item.error,
                            receipt = item.receipt,
                            proposalDate = item.date,
                            onRemove = { proposalId ->
                                messages = messages.map { current ->
                                    if (current is ChatItem.Plan && current.id == item.id) {
                                        current.copy(proposals = current.proposals.filterNot { it.id == proposalId })
                                    } else current
                                }
                            },
                            onEdit = { updated ->
                                messages = messages.map { current ->
                                    if (current is ChatItem.Plan && current.id == item.id) {
                                        current.copy(
                                            proposals = current.proposals.map { if (it.id == updated.id) updated else it },
                                        )
                                    } else current
                                }
                            },
                            onConfirm = { confirm(item.id, item.proposals, item.date) },
                        )
                    }
                }
            }

            if (voiceMode) {
                VoiceInputBar(
                    onFinish = { text, cancelled ->
                        voiceMode = false
                        if (!cancelled && text.isNotBlank()) send(text)
                    },
                    onDismiss = { voiceMode = false },
                )
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    agents.forEach { agent ->
                        Surface(
                            onClick = { input = if (input.isBlank()) "@${agent.name} " else "$input@${agent.name} " },
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape,
                        ) {
                            Text(
                                "${agent.avatar} @${agent.name}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    IconButton(onClick = startVoice, enabled = !busy && submittingPlanId == null, modifier = Modifier.testTag("group-voice")) {
                        Icon(PlannerIcons.Voice, contentDescription = "语音输入", tint = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        enabled = !busy && submittingPlanId == null,
                        modifier = Modifier.weight(1f).testTag("group-input"),
                        label = { Text("给安排助手的话") },
                        placeholder = { Text("说一句话，或 @ 一位成员…") },
                        minLines = 1,
                        maxLines = 4,
                        shape = MaterialTheme.shapes.small,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(6.dp))
                    DesignButton(
                        onClick = { if (busy) stop() else send(input) },
                        enabled = busy || (input.isNotBlank() && submittingPlanId == null),
                        modifier = Modifier.testTag("group-send"),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        if (busy) {
                            Icon(Icons.Outlined.Close, contentDescription = "停止生成", tint = MaterialTheme.colorScheme.error)
                        } else {
                            Icon(PlannerIcons.Send, contentDescription = "发送")
                        }
                    }
                }
            }
        }
    }

    if (showHistory) AgentReceiptHistoryDialog(receipts, onDismiss = { showHistory = false })

    if (showManagement) {
        AgentManagementSheet(
            agents = agents,
            user = user,
            onSaveAgents = { updated ->
                agents = updated
                store.saveAgents(updated)
            },
            onSaveUser = { updated ->
                user = updated
                store.saveUser(updated)
            },
            onDismiss = { showManagement = false },
        )
    }
}

@Composable
private fun GroupHeader(
    agents: List<AgentProfile>,
    canRefineWithAi: Boolean,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
    canDismiss: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val showAvatars = maxWidth >= 480.dp && LocalDensity.current.fontScale < 1.5f
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (showAvatars) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        agents.take(4).forEach { agent ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(34.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text(agent.avatar, fontSize = 16.sp) }
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    "规划小组",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onManage) { Text("管理") }
                IconButton(onClick = onDismiss, enabled = canDismiss) { Icon(Icons.Outlined.Close, contentDescription = "关闭") }
            }
            Text(
                "${agents.size} 位成员 · " + if (canRefineWithAi) "已连接云端" else "云端未启用",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UserBubble(user: UserProfile, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp, 16.dp, 6.dp, 16.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text,
                modifier = Modifier.padding(12.dp, 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(8.dp))
        Avatar(user.avatar, size = 32)
    }
}

@Composable
private fun AgentBubble(agent: AgentProfile, text: String, typing: Boolean, tools: List<ToolTrace>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Avatar(agent.avatar, size = 32)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                agent.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(6.dp, 16.dp, 16.dp, 16.dp),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(
                    if (typing) "$text▍" else text,
                    modifier = Modifier.padding(12.dp, 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (tools.isNotEmpty()) ToolChips(tools)
        }
    }
}

/**
 * A step that did not run. Kept visually distinct from an agent bubble on purpose: the user
 * must be able to tell "the team said nothing" from "the team could not be reached".
 */
@Composable
private fun NoticeBubble(reason: AiAdvisorFailureReason, agentName: String?) {
    val who = agentName?.let { "$it " }.orEmpty()
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${who}这一步没跑通",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "${reason.label()} · ${reason.hint()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun AgentTypingRow(agent: AgentProfile) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Avatar(agent.avatar, size = 32)
        Spacer(Modifier.width(8.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
            Row(
                modifier = Modifier.padding(10.dp, 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                Text("${agent.name} 正在处理…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ToolChips(tools: List<ToolTrace>) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tools.forEach { tool ->
            val tone = when (tool.outcome) {
                ToolOutcome.DONE -> MaterialTheme.colorScheme.secondaryContainer
                ToolOutcome.SKIPPED -> MaterialTheme.colorScheme.surfaceContainerHigh
                ToolOutcome.FAILED -> MaterialTheme.colorScheme.errorContainer
            }
            Surface(color = tone, shape = CircleShape) {
                Text(
                    "${tool.label} · ${tool.outcome.label()}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun PlanBubble(
    proposals: List<AgentTaskProposal>,
    isSubmitting: Boolean,
    enabled: Boolean,
    error: String?,
    receipt: AgentActionReceipt?,
    proposalDate: LocalDate,
    onRemove: (String) -> Unit,
    onEdit: (AgentTaskProposal) -> Unit,
    onConfirm: () -> Unit,
) {
    var editing by remember { mutableStateOf<AgentTaskProposal?>(null) }

    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$proposalDate · 可编辑草案 ${proposals.size} 项", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "点任意一项可以改名称、时长和开始时间。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            proposals.forEach { proposal ->
                Surface(
                    onClick = { editing = proposal },
                    enabled = enabled,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp, 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(4.dp).heightIn(min = 32.dp).background(previewInk(proposal.category), RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(proposal.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(proposalSummary(proposal), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onRemove(proposal.id) }, enabled = enabled) {
                            Icon(Icons.Outlined.Close, contentDescription = "移除", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            receipt?.let { AgentReceiptCard(it) }
            DesignButton(onClick = onConfirm, enabled = enabled && proposals.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("group-confirm")) {
                val plannedCount = proposals.count { it.timeHint.explicitStartMinute != null && it.durationMinutes != null }
                Text(if (isSubmitting) "正在确认写入…" else "确认 ${proposals.size} 项 · 安排 $plannedCount 项")
            }
            Text("确认后才会创建任务；时间只写入草案里对应的时段。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }

    editing?.let { target ->
        ProposalEditDialog(
            proposal = target,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                onEdit(updated)
            },
            onDelete = {
                editing = null
                onRemove(target.id)
            },
        )
    }
}

/**
 * Editing has to happen here, not in another screen: the user's complaint is exactly that
 * adjusting a time means leaving the conversation. Everything the confirm step consumes —
 * name, length, start — is editable, and the result is still only a draft.
 */
@Composable
private fun ProposalEditDialog(
    proposal: AgentTaskProposal,
    onDismiss: () -> Unit,
    onSave: (AgentTaskProposal) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(proposal.text) }
    var minutes by remember { mutableStateOf(proposal.durationMinutes?.toString().orEmpty()) }
    var start by remember {
        mutableStateOf(proposal.timeHint.explicitStartMinute?.let(::formatMinute).orEmpty())
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("改这一项") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("proposal-title"),
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                    label = { Text("时长（分钟）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("proposal-duration"),
                )
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text("开始时间 HH:mm，留空表示待定") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("proposal-start"),
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val duration = minutes.toIntOrNull()
                val startMinute = parseMinuteOfDay(start)
                when {
                    title.isBlank() -> error = "名称不能为空"
                    duration == null || duration !in 5..720 -> error = "时长请填 5–720 分钟"
                    start.isNotBlank() && startMinute == null -> error = "时间请填 HH:mm"
                    else -> onSave(
                        proposal.copy(
                            text = title.trim(),
                            durationMinutes = duration,
                            // The user just supplied it, so it is no longer an estimate.
                            durationEstimated = false,
                            timeHint = proposal.timeHint.copy(explicitStartMinute = startMinute),
                            needsClarification = proposal.needsClarification - ArrangementClarification.TIME - ArrangementClarification.DURATION,
                        ),
                    )
                }
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

private fun parseMinuteOfDay(raw: String): Int? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val parts = text.split(':', '：')
    if (parts.size != 2) return null
    val hour = parts[0].trim().toIntOrNull() ?: return null
    val minute = parts[1].trim().toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

@Composable
private fun Avatar(emoji: String, size: Int) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape, modifier = Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = (size * 0.55f).sp) }
    }
}

private fun agentOf(id: String, agents: List<AgentProfile>): AgentProfile =
    agents.firstOrNull { it.id == id } ?: agents.first()

private fun ToolOutcome.label(): String = when (this) {
    ToolOutcome.DONE -> "完成"
    ToolOutcome.SKIPPED -> "跳过"
    ToolOutcome.FAILED -> "失败"
}

/**
 * One line per item, and it says where the time came from. "15:00" that the user typed and
 * "15:00" that the client chose must not look identical: the second one is the app's
 * proposal and is the thing they may want to move.
 */
private fun proposalSummary(item: AgentTaskProposal): String = buildString {
    append(item.timeHint.explicitStartMinute?.let(::formatMinute) ?: item.timeHint.windowLabel ?: "时间待定")
    append(" · ")
    append(
        item.durationMinutes?.let { if (item.durationEstimated) "$it 分钟（估算）" else "$it 分钟" }
            ?: "时长待定",
    )
    if (item.placementSource == ArrangementPlacementSource.AI_SUGGESTED) append(" · 自动排")
    item.priority?.let { append(" · ").append(it.label()) }
}

private fun TaskPriority.label(): String = when (this) {
    TaskPriority.REQUIRED -> "必做"
    TaskPriority.HIGH -> "高优先"
    TaskPriority.MEDIUM -> "中优先"
    TaskPriority.LOW -> "低优先"
}

/**
 * The conversation, kept outside the composable so dismissing the sheet does not throw it
 * away. In-memory only: the chat is a working session, not a record, and nothing here is
 * written to Room.
 */
fun clearGroupChatSession() {
    GroupChatSession.items = emptyList()
    GroupChatSession.nextId = 0
}

private object GroupChatSession {
    var items: List<ChatItem> = emptyList()
    var nextId: Int = 0
}

private sealed interface ChatItem {
    val id: String

    data class User(override val id: String, val text: String) : ChatItem
    data class Agent(
        override val id: String,
        val agentId: String,
        val text: String,
        val tools: List<ToolTrace>,
        /** How many characters are on screen; the typewriter advances this. */
        val revealed: Int = text.length,
        val toolsVisible: Boolean = true,
    ) : ChatItem
    data class Typing(override val id: String, val agentId: String) : ChatItem
    data class Plan(override val id: String, val proposals: List<AgentTaskProposal>, val date: LocalDate, val error: String? = null, val receipt: AgentActionReceipt? = null) : ChatItem
    data class Receipt(override val id: String, val receipt: AgentActionReceipt) : ChatItem
    data class Error(override val id: String, val text: String) : ChatItem
    data class Notice(
        override val id: String,
        val reason: AiAdvisorFailureReason,
        val agentName: String?,
    ) : ChatItem
}

/** What actually happened, then what the user can do about it. */
private fun AiAdvisorFailureReason.label(): String = when (this) {
    AiAdvisorFailureReason.DISABLED -> "云端未启用"
    AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED -> "没有可用的服务商配置"
    AiAdvisorFailureReason.TRANSPORT_FAILURE -> "连不上服务商"
    AiAdvisorFailureReason.TIMEOUT -> "请求超时"
    AiAdvisorFailureReason.AUTHENTICATION_FAILURE -> "密钥被拒绝"
    AiAdvisorFailureReason.RATE_LIMITED -> "被限流"
    AiAdvisorFailureReason.REMOTE_FAILURE -> "服务商返回错误"
    AiAdvisorFailureReason.INVALID_RESPONSE -> "返回内容无法解析"
}

private fun AiAdvisorFailureReason.hint(): String = when (this) {
    AiAdvisorFailureReason.DISABLED -> "到设置里打开 AI 并同意授权"
    AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED -> "到设置里填 baseUrl、模型和 API Key"
    AiAdvisorFailureReason.TRANSPORT_FAILURE -> "检查网络，以及 baseUrl 是否填错"
    AiAdvisorFailureReason.TIMEOUT -> "稍后重试，或换一个更快的模型"
    AiAdvisorFailureReason.AUTHENTICATION_FAILURE -> "检查 API Key 是否属于这个地址"
    AiAdvisorFailureReason.RATE_LIMITED -> "等一会儿再发"
    AiAdvisorFailureReason.REMOTE_FAILURE -> "换个模型或稍后重试"
    AiAdvisorFailureReason.INVALID_RESPONSE -> "换个模型，或把需求说得更具体"
}
