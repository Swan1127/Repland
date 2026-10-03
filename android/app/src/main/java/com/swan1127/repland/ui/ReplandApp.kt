package com.swan1127.repland.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.swan1127.repland.R
import com.swan1127.repland.domain.model.ClassPeriodClock
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.AiProviderConfig
import com.swan1127.repland.domain.model.AiAdvisorRequest
import com.swan1127.repland.domain.model.AiAdvisorResponse
import com.swan1127.repland.domain.model.AiDailySummaryRequest
import com.swan1127.repland.domain.model.AiDailySummaryAdvice
import com.swan1127.repland.domain.model.AiDifficultyAndDurationAdvice
import com.swan1127.repland.domain.model.AiDifficultyAndDurationRequest
import com.swan1127.repland.domain.model.AiReplanRequest
import com.swan1127.repland.domain.model.AiSortingExplanationAdvice
import com.swan1127.repland.domain.model.AiSortingExplanationRequest
import com.swan1127.repland.domain.model.AiTaskBreakdownAdvice
import com.swan1127.repland.domain.model.AiTaskBreakdownRequest
import com.swan1127.repland.domain.model.AiTaskUnderstandingAdvice
import com.swan1127.repland.domain.model.AiTaskUnderstandingRequest
import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.DateOverrideDraft
import com.swan1127.repland.domain.model.DateOverrideType
import com.swan1127.repland.domain.model.DailyReviewSummarizer
import com.swan1127.repland.domain.model.ExecutionLogEventType
import com.swan1127.repland.domain.model.ImportedCourse
import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlanDraftEditor
import com.swan1127.repland.domain.model.PlanGenerationInput
import com.swan1127.repland.domain.model.LocalPriorityAssessment
import com.swan1127.repland.domain.model.PriorityReason
import com.swan1127.repland.domain.model.PriorityReasonKind
import com.swan1127.repland.domain.model.RhythmTrack
import com.swan1127.repland.domain.model.PlanningRevision
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.PlanningAgentState
import com.swan1127.repland.domain.model.PlanningAgentRequestType
import com.swan1127.repland.domain.model.LocalPlanningFallback
import com.swan1127.repland.domain.model.ProfileEvidence
import com.swan1127.repland.domain.model.ProfileEvidenceScope
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskDraftValidator
import com.swan1127.repland.domain.model.TaskName
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.TimeBlockValidator
import com.swan1127.repland.domain.model.ScheduleTimeline
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelinePhase
import com.swan1127.repland.domain.model.TodayFocus
import com.swan1127.repland.domain.model.EngagementMode
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import com.swan1127.repland.domain.model.UnscheduledReason
import com.swan1127.repland.domain.model.PlanDraftReview
import com.swan1127.repland.ui.time.TimeViewModel
import com.swan1127.repland.ui.time.TimetableImportState
import com.swan1127.repland.ui.plan.PlanViewModel
import com.swan1127.repland.ui.preferences.CategoryPreferenceViewModel
import com.swan1127.repland.ui.reminders.ReminderSettingsViewModel
import com.swan1127.repland.ui.profile.ProfileEvidenceViewModel
import com.swan1127.repland.ui.data.DataManagementResult
import com.swan1127.repland.ui.data.DataManagementViewModel
import com.swan1127.repland.ui.ai.PlanningAgentViewModel
import com.swan1127.repland.ui.ai.AiProviderConfigViewModel
import com.swan1127.repland.ui.ai.AiProviderConnectionTest
import com.swan1127.repland.ui.agent.AgentCenterScreen
import com.swan1127.repland.ui.agent.ArrangementAssistantViewModel
import com.swan1127.repland.ui.tasks.TaskViewModel
import com.swan1127.repland.ui.schedule.TimelineDashboard
import com.swan1127.repland.ui.schedule.WeekScheduleView
import com.swan1127.repland.ui.schedule.MonthScheduleView
import com.swan1127.repland.ui.schedule.TimelineEventObject
import com.swan1127.repland.ui.schedule.DailyDesk
import com.swan1127.repland.ui.engagement.EngagementViewModel
import com.swan1127.repland.ui.components.CapacitySummary
import com.swan1127.repland.ui.components.EditorSheet
import com.swan1127.repland.ui.components.AssistantSheet
import com.swan1127.repland.ui.components.NowCard
import com.swan1127.repland.ui.components.TaskCaptureSheet
import com.swan1127.repland.ui.components.TaskDatePickerDialog
import com.swan1127.repland.ui.components.TaskRow
import com.swan1127.repland.ui.components.TaskRowEmphasis
import com.swan1127.repland.ui.components.PlannerIcons
import com.swan1127.repland.ui.components.taskDateLabel
import com.swan1127.repland.reminders.LocalReminderScheduler
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AppTab(
    @param:StringRes val titleRes: Int,
    @param:StringRes val shortRes: Int,
) {
    TODAY(R.string.today_title, R.string.tab_today),
    TASKS(R.string.tasks_title, R.string.tab_tasks),
    AGENT(R.string.agent_title, R.string.tab_agent),
    TIME(R.string.time_title, R.string.tab_time),
    MINE(R.string.mine_title, R.string.tab_mine),
}

private enum class ScheduleRange { OVERVIEW, DAY, WEEK, MONTH }

private const val POSTPONEMENT_ADVICE_THRESHOLD = 3

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ReplandApp(
    taskViewModel: TaskViewModel,
    timeViewModel: TimeViewModel,
    planViewModel: PlanViewModel,
    categoryPreferenceViewModel: CategoryPreferenceViewModel,
    reminderSettingsViewModel: ReminderSettingsViewModel,
    profileEvidenceViewModel: ProfileEvidenceViewModel,
    dataManagementViewModel: DataManagementViewModel,
    planningAgentViewModel: PlanningAgentViewModel,
    aiProviderConfigViewModel: AiProviderConfigViewModel,
    arrangementAssistantViewModel: ArrangementAssistantViewModel,
    engagementViewModel: EngagementViewModel,
) {
    val uiState by taskViewModel.uiState.collectAsStateWithLifecycle()
    val executionSession by taskViewModel.activeSession.collectAsStateWithLifecycle()
    val executionBusy by taskViewModel.sessionBusy.collectAsStateWithLifecycle()
    val executionError by taskViewModel.sessionError.collectAsStateWithLifecycle()
    val executionFinished by taskViewModel.sessionFinished.collectAsStateWithLifecycle()
    var showExecutionSession by rememberSaveable { mutableStateOf(false) }
    val timeUiState by timeViewModel.uiState.collectAsStateWithLifecycle()
    val planUiState by planViewModel.uiState.collectAsStateWithLifecycle()
    val workspaceUiState by planViewModel.workspaceUiState.collectAsStateWithLifecycle()
    val categoryPreferenceUiState by categoryPreferenceViewModel.uiState.collectAsStateWithLifecycle()
    val reminderSettingsUiState by reminderSettingsViewModel.uiState.collectAsStateWithLifecycle()
    val profileEvidenceUiState by profileEvidenceViewModel.uiState.collectAsStateWithLifecycle()
    val dataManagementUiState by dataManagementViewModel.uiState.collectAsStateWithLifecycle()
    val planningAgentUiState by planningAgentViewModel.uiState.collectAsStateWithLifecycle()
    val aiProviderConfigUiState by aiProviderConfigViewModel.uiState.collectAsStateWithLifecycle()
    val arrangementAssistantAccess by arrangementAssistantViewModel.access.collectAsStateWithLifecycle()
    val engagementMode by engagementViewModel.mode.collectAsStateWithLifecycle()
    var activeDate by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            activeDate = LocalDate.now()
        }
    }
    val context = LocalContext.current
    val notificationPermissionGranted = context.canPostLocalNotifications()
    val reminderScheduler = remember(context) { LocalReminderScheduler(context.applicationContext) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) reminderSettingsViewModel.setEnabled(true)
    }
    val localDataExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { destination ->
        destination?.let { uri -> dataManagementViewModel.exportTo(context.contentResolver, uri) }
    }
    var showVoiceComposer by rememberSaveable { mutableStateOf(false) }
    var voiceTranscript by rememberSaveable { mutableStateOf("") }
    var voiceError by rememberSaveable { mutableStateOf(false) }
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull().orEmpty()
            if (words.isNotBlank()) voiceTranscript = words
            else voiceError = true
        }
    }
    val onVoiceCapture: () -> Unit = {
        voiceError = false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说出想安排的事")
        }
        runCatching { speechLauncher.launch(intent) }.onFailure { voiceError = true }
        Unit
    }
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.TODAY) }
    var timeReturnTab by rememberSaveable { mutableStateOf(AppTab.TODAY) }
    var selectedTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var taskEditorTarget by remember { mutableStateOf<Task?>(null) }
    var showTaskEditor by rememberSaveable { mutableStateOf(false) }
    var showQuickAvailability by rememberSaveable { mutableStateOf(false) }
    var showTaskCapture by rememberSaveable { mutableStateOf(false) }
    var taskEditorSeed by rememberSaveable { mutableStateOf("") }
    var completingTask by remember { mutableStateOf<Task?>(null) }
    var partiallyCompletingTask by remember { mutableStateOf<Task?>(null) }
    var feedbackTask by remember { mutableStateOf<Task?>(null) }
    var postponingTask by remember { mutableStateOf<Task?>(null) }
    var cancellingTask by remember { mutableStateOf<Task?>(null) }
    var replacingTask by remember { mutableStateOf<Task?>(null) }
    var correctingLog by remember { mutableStateOf<TaskExecutionLog?>(null) }
    var weeklyBlockEditorTarget by remember { mutableStateOf<WeeklyTimeBlock?>(null) }
    var showWeeklyBlockEditor by rememberSaveable { mutableStateOf(false) }
    var weeklyBlockInitialKind by remember { mutableStateOf(TimeBlockKind.COURSE) }
    var dateOverrideEditorTarget by remember { mutableStateOf<DateOverride?>(null) }
    var showDateOverrideEditor by rememberSaveable { mutableStateOf(false) }
    var deletingWeeklyBlock by remember { mutableStateOf<WeeklyTimeBlock?>(null) }
    var deletingDateOverride by remember { mutableStateOf<DateOverride?>(null) }
    var showSemesterStartEditor by rememberSaveable { mutableStateOf(false) }
    var showPlanOverview by rememberSaveable { mutableStateOf(false) }
    var showClearPlanConfirmation by rememberSaveable { mutableStateOf(false) }
    var showClearLocalDataConfirmation by rememberSaveable { mutableStateOf(false) }
    var showAiConsentDialog by rememberSaveable { mutableStateOf(false) }
    var showDailyReview by rememberSaveable { mutableStateOf(false) }
    var assistantTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAssistant by rememberSaveable { mutableStateOf(false) }
    var lastPlanningAgentTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewingAgentDraft by rememberSaveable { mutableStateOf(false) }
    val reviewDate = activeDate
    val dailyLogsFlow = remember(reviewDate) { taskViewModel.observeExecutionLogsForDate(reviewDate) }
    val dailyLogs by dailyLogsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedTask = selectedTaskId?.let { id -> uiState.tasks.firstOrNull { it.id == id } }
    val selectedTaskLogs = remember(selectedTaskId) {
        selectedTaskId?.let(taskViewModel::observeExecutionLogs) ?: flowOf(emptyList())
    }
    val executionLogs by selectedTaskLogs.collectAsStateWithLifecycle(initialValue = emptyList())
    val planNeedsUpdate = planUiState.currentPlan?.let { plan ->
        uiState.tasks.any { task -> task.updatedAtEpochMillis > plan.createdAtEpochMillis } ||
            timeUiState.weeklyBlocks.any { block -> block.updatedAtEpochMillis > plan.createdAtEpochMillis } ||
            timeUiState.dateOverrides.any { override ->
                override.updatedAtEpochMillis > plan.createdAtEpochMillis
            } || timeUiState.timeConstraintsUpdatedAtEpochMillis > plan.createdAtEpochMillis
    } ?: false
    fun generatePlanDraft(reorder: Boolean = false, orderOnly: Boolean = false, todayOnly: LocalDate? = null) {
        planViewModel.generateDraft(
            tasks = uiState.tasks,
            weeklyBlocks = timeUiState.weeklyBlocks,
            dateOverrides = timeUiState.dateOverrides,
            semesterFirstWeekMonday = timeUiState.semesterFirstWeekMonday,
            lockedSegments = planUiState.currentPlan?.segments
                ?.filter { segment ->
                    (segment.isLocked || executionSession?.taskId == segment.taskId) && !segment.hasEndedBefore(LocalDateTime.now())
                }
                .orEmpty(),
            categoryPreferences = categoryPreferenceUiState.weights,
            manualTaskOrder = if (reorder) emptyList() else planUiState.taskOrder.ifEmpty { planUiState.currentPlan
                ?.takeIf { it.hasManualTaskOrder }
                ?.orderedTaskIds
                .orEmpty() },
            orderOnly = orderOnly,
            todayOnly = todayOnly,
        )
    }
    // The arrangement assistant previews against exactly the same projected day
    // that the user sees on the home timeline; it never schedules into a vacuum.
    val agentTimelineEntries = ScheduleTimeline.entries(
        date = activeDate,
        weeklyBlocks = timeUiState.weeklyBlocks,
        dateOverrides = timeUiState.dateOverrides,
        segments = planUiState.currentPlan?.segments.orEmpty(),
        tasks = uiState.tasks,
        semesterFirstWeekMonday = timeUiState.semesterFirstWeekMonday,
    )

    // Feedback and future-constraint changes can propose a new plan, but only the user
    // can accept it. The draft itself is deliberately not a key: dismissing a draft
    // must not cause the system to immediately recreate it without a new user change.
    // Changed facts show a review affordance, never an unsolicited modal over execution/editing.

    LaunchedEffect(
        reminderSettingsUiState.preferences.isEnabled,
        notificationPermissionGranted,
        planUiState.currentPlan,
        planUiState.planHistory,
        uiState.tasks,
    ) {
        reminderScheduler.sync(
            isEnabled = reminderSettingsUiState.preferences.isEnabled && notificationPermissionGranted,
            currentPlan = planUiState.currentPlan,
            planHistory = planUiState.planHistory,
            tasks = uiState.tasks,
        )
    }
    val onReminderEnabledChange: (Boolean) -> Unit = { enabled ->
        when {
            !enabled -> reminderSettingsViewModel.setEnabled(false)
            notificationPermissionGranted -> reminderSettingsViewModel.setEnabled(true)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val onAiEnabledChange: (Boolean) -> Unit = { enabled ->
        when {
            !enabled -> planningAgentViewModel.setEnabled(false)
            planningAgentUiState.preferences.hasExplicitConsent -> planningAgentViewModel.setEnabled(true)
            else -> showAiConsentDialog = true
        }
    }

    if (selectedTask != null) {
        TaskDetailScreen(
            task = selectedTask,
            executionLogs = executionLogs,
            onBack = { selectedTaskId = null },
            onStart = taskViewModel::startTask,
            onEdit = {
                taskEditorTarget = selectedTask
                showTaskEditor = true
            },
            onPostpone = { postponingTask = selectedTask },
            onCancel = { cancellingTask = selectedTask },
            onComplete = { taskViewModel.completeTask(selectedTask.id) },
            onPartialCompletion = { partiallyCompletingTask = selectedTask },
            onFeedback = { feedbackTask = selectedTask },
            onOpenAssistant = { assistantTaskId = selectedTask.id; showAssistant = true },
            onReplace = { replacingTask = selectedTask },
            onRestore = { taskViewModel.restoreTask(selectedTask.id) },
            onCorrectLog = { correctingLog = it },
            onReviewAdjustmentDraft = {
                selectedTaskId = null
                selectedTab = AppTab.TIME
                if (planUiState.draft == null) generatePlanDraft()
            },
            isAiEnabled = planningAgentUiState.preferences.isEnabled,
            planningAgentState = if (lastPlanningAgentTaskId == selectedTask.id) {
                planningAgentUiState.workflow
            } else {
                PlanningAgentState.Idle
            },
            onRequestAiAdvice = { requestType ->
                lastPlanningAgentTaskId = selectedTask.id
                when (requestType) {
                    PlanningAgentRequestType.TASK_UNDERSTANDING -> planningAgentViewModel.beginTaskUnderstanding(
                        selectedTask, executionLogs, planUiState.currentPlan,
                    )

                    PlanningAgentRequestType.DIFFICULTY_AND_DURATION -> planningAgentViewModel.beginDifficultyAndDuration(
                        selectedTask, executionLogs, planUiState.currentPlan,
                    )

                    PlanningAgentRequestType.TASK_BREAKDOWN -> planningAgentViewModel.beginTaskBreakdown(
                        selectedTask, executionLogs, planUiState.currentPlan,
                    )

                    PlanningAgentRequestType.SORTING_EXPLANATION -> planningAgentViewModel.beginSortingExplanation(
                        selectedTask,
                        executionLogs,
                        planUiState.currentPlan,
                        listOf("本地排序不会改写初始优先级，并使用截止日期、类别偏好和延期记录。"),
                    )

                    PlanningAgentRequestType.REPLAN -> planningAgentViewModel.beginReplan(
                        affectedTasks = listOf(selectedTask),
                        executionLogs = executionLogs,
                        currentPlan = planUiState.currentPlan,
                        localPlanInput = PlanGenerationInput(
                            tasks = uiState.tasks,
                            weeklyBlocks = timeUiState.weeklyBlocks,
                            dateOverrides = timeUiState.dateOverrides,
                            semesterFirstWeekMonday = timeUiState.semesterFirstWeekMonday,
                            lockedSegments = planUiState.currentPlan?.segments
                                ?.filter { it.isLocked && !it.hasEndedBefore(LocalDateTime.now()) }
                                .orEmpty(),
                            categoryPreferences = categoryPreferenceUiState.weights,
                            manualTaskOrder = planUiState.currentPlan
                                ?.takeIf { it.hasManualTaskOrder }
                                ?.orderedTaskIds
                                .orEmpty(),
                        ),
                        constraintSummary = listOf("仅调整当前任务的未来工作；课程、休息、锁定和固定任务保持受保护。"),
                    )

                    PlanningAgentRequestType.DAILY_SUMMARY -> Unit
                }
            },
            onReviewAgentDraft = { draft ->
                planViewModel.showAgentDraft(draft)
                reviewingAgentDraft = planUiState.draft == null
            },
        )
    } else {
        Scaffold(
            topBar = {
                if (selectedTab != AppTab.AGENT) TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (selectedTab == AppTab.TIME) "导入课表" else stringResource(selectedTab.titleRes),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            if (selectedTab == AppTab.TODAY) {
                                Text(
                                    text = activeDate.format(DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (selectedTab == AppTab.TIME) {
                            IconButton(
                                onClick = { selectedTab = timeReturnTab },
                                modifier = Modifier.testTag("timetable-back"),
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                            }
                        }
                    },
                    actions = {
                        if (selectedTab == AppTab.TASKS) {
                            if (workspaceUiState.canUndoOrder) TextButton(
                                onClick = planViewModel::undoTaskOrder,
                                enabled = !planUiState.isWorking && planUiState.draft == null,
                                modifier = Modifier.testTag("undo-task-sort"),
                            ) { Text("撤销排序") }
                            TextButton(
                                onClick = { if (planUiState.draft == null) generatePlanDraft(reorder = true, orderOnly = true) },
                                enabled = !planUiState.isWorking && planUiState.draft == null && uiState.tasks.any { it.status.isActive },
                                modifier = Modifier.testTag("auto-sort-tasks"),
                            ) { Text("自动排序") }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    AppTab.entries.filter { it != AppTab.TIME }.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            modifier = Modifier.testTag("navigation-${tab.name.lowercase()}"),
                            icon = { TabIcon(tab) },
                            label = { Text(stringResource(tab.shortRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            },
            floatingActionButton = {
                when (selectedTab) {
                    AppTab.TASKS -> {
                        FloatingActionButton(
                            onClick = {
                                showTaskCapture = true
                            },
                            modifier = Modifier.testTag("add-task"),
                        ) { Icon(Icons.Outlined.Add, contentDescription = "添加任务") }
                    }

                    AppTab.TODAY -> Unit

                    AppTab.AGENT -> Unit

                    AppTab.TIME -> Unit

                    AppTab.MINE -> Unit
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (selectedTab) {
                    AppTab.TODAY -> TodayScreen(
                        executionSession = executionSession,
                        executionError = executionError,
                        onResumeExecution = { showExecutionSession = true },
                        executionFinished = executionFinished,
                        onReplanRemaining = { taskViewModel.dismissSessionResult(); generatePlanDraft() },
                        onDismissExecutionResult = taskViewModel::dismissSessionResult,
                        planNeedsUpdate = planNeedsUpdate,
                        onReviewPlanChanges = { if (planUiState.draft == null) generatePlanDraft() },
                        date = activeDate,
                        tasks = uiState.tasks.filter(Task::isTodayRelevant),
                        allTasks = uiState.tasks,
                        weeklyBlocks = timeUiState.weeklyBlocks,
                        dateOverrides = timeUiState.dateOverrides,
                        semesterFirstWeekMonday = timeUiState.semesterFirstWeekMonday,
                        engagementMode = engagementMode,
                        tracks = workspaceUiState.tracks,
                        onTracksChanged = planViewModel::saveTracks,
                        onOpenTimelineEntry = engagementViewModel::recordTimelineOpened,
                        onEditTimelineEntry = { entry ->
                            when {
                                entry.id.startsWith("segment:") -> entry.taskId?.let { taskId ->
                                    taskEditorTarget = uiState.tasks.firstOrNull { it.id == taskId }
                                    showTaskEditor = taskEditorTarget != null
                                }

                                entry.id.startsWith("weekly:") -> {
                                    weeklyBlockEditorTarget = timeUiState.weeklyBlocks.firstOrNull {
                                        it.id == entry.id.removePrefix("weekly:")
                                    }
                                    showWeeklyBlockEditor = weeklyBlockEditorTarget != null
                                }

                                entry.id.startsWith("override:") -> {
                                    dateOverrideEditorTarget = timeUiState.dateOverrides.firstOrNull {
                                        it.id == entry.id.removePrefix("override:")
                                    }
                                    showDateOverrideEditor = dateOverrideEditorTarget != null
                                }
                            }
                        },
                        planSegments = planUiState.currentPlan?.segments
                            ?.filter { it.date == activeDate }
                            .orEmpty(),
                        allPlanSegments = planUiState.currentPlan?.segments.orEmpty(),
                        hasConfirmedPlan = planUiState.currentPlan != null,
                        pendingConfirmationSegments = planUiState.currentPlan?.segments
                            ?.filter { segment ->
                                segment.hasEndedBefore(LocalDateTime.now()) &&
                                    uiState.tasks.firstOrNull { it.id == segment.taskId }?.status?.isActive == true
                            }
                            ?.sortedWith(compareBy(PlannedSegment::date, PlannedSegment::startMinute))
                            .orEmpty(),
                        isLoading = uiState.isLoading,
                        onOpen = { selectedTaskId = it.id },
                        onStart = taskViewModel::startTask,
                        onOpenDailyReview = { showDailyReview = true },
                        onAdd = {
                            showTaskCapture = true
                        },
                        onOpenPlan = { selectedTab = AppTab.AGENT },
                        onOpenTaskLibrary = { selectedTab = AppTab.TASKS },
                        onPlaceEvent = planViewModel::placeTask,
                        onFocusStarted = { segmentId ->
                            showExecutionSession = true
                            taskViewModel.startSession(segmentId)
                        },
                        onCreateCourse = { request, courseDate ->
                            timeViewModel.saveWeeklyBlock(
                                WeeklyTimeBlockDraft(
                                    title = request.title,
                                    kind = TimeBlockKind.COURSE,
                                    dayOfWeek = courseDate.dayOfWeek,
                                    startMinute = request.startMinute,
                                    endMinute = (request.startMinute + request.durationMinutes).coerceAtMost(1_440),
                                    trackId = request.trackId,
                                ),
                            )
                        },
                        onRemovePlacement = planViewModel::removePlacement,
                        onMoveEntry = { entry, startMinute ->
                            val duration = entry.endMinute - entry.startMinute
                            val endMinute = startMinute + duration
                            when {
                                entry.id.startsWith("segment:") -> planViewModel.movePlacement(
                                    entry.id.removePrefix("segment:"), startMinute, endMinute, entry.trackId,
                                )

                                entry.id.startsWith("weekly:") -> timeUiState.weeklyBlocks
                                    .firstOrNull { it.id == entry.id.removePrefix("weekly:") }
                                    ?.let { block ->
                                        timeViewModel.saveWeeklyBlock(
                                            WeeklyTimeBlockDraft(
                                                id = block.id,
                                                title = block.title,
                                                kind = block.kind,
                                                dayOfWeek = block.dayOfWeek,
                                                startMinute = startMinute,
                                                endMinute = endMinute,
                                                weekPattern = block.weekPattern,
                                                trackId = block.trackId,
                                                note = block.note,
                                            ),
                                        )
                                    }

                                entry.id.startsWith("override:") -> timeUiState.dateOverrides
                                    .firstOrNull { it.id == entry.id.removePrefix("override:") }
                                    ?.let { override ->
                                        timeViewModel.saveDateOverride(
                                            DateOverrideDraft(
                                                id = override.id,
                                                title = override.title,
                                                type = override.type,
                                                date = override.date,
                                                startMinute = startMinute,
                                                endMinute = endMinute,
                                                note = override.note,
                                            ),
                                        )
                                    }
                            }
                        },
                        onUpdateEntryTime = { entry, startMinute, endMinute ->
                            when {
                                entry.id.startsWith("segment:") -> planViewModel.movePlacement(
                                    entry.id.removePrefix("segment:"), startMinute, endMinute, entry.trackId,
                                )

                                entry.id.startsWith("weekly:") -> timeUiState.weeklyBlocks
                                    .firstOrNull { it.id == entry.id.removePrefix("weekly:") }
                                    ?.let { block ->
                                        timeViewModel.saveWeeklyBlock(
                                            WeeklyTimeBlockDraft(
                                                id = block.id,
                                                title = block.title,
                                                kind = block.kind,
                                                dayOfWeek = block.dayOfWeek,
                                                startMinute = startMinute,
                                                endMinute = endMinute,
                                                weekPattern = block.weekPattern,
                                                trackId = block.trackId,
                                                note = block.note,
                                            ),
                                        )
                                    }

                                entry.id.startsWith("override:") -> timeUiState.dateOverrides
                                    .firstOrNull { it.id == entry.id.removePrefix("override:") }
                                    ?.let { override ->
                                        timeViewModel.saveDateOverride(
                                            DateOverrideDraft(
                                                id = override.id,
                                                title = override.title,
                                                type = override.type,
                                                date = override.date,
                                                startMinute = startMinute,
                                                endMinute = endMinute,
                                                note = override.note,
                                            ),
                                        )
                                    }
                            }
                        },
                    )

                    AppTab.TASKS -> TasksScreen(
                        confirmedSegments = planUiState.currentPlan?.segments.orEmpty(),
                        tasks = uiState.tasks.sortedBy { task -> planUiState.taskOrder.indexOf(task.id).takeIf { it >= 0 } ?: Int.MAX_VALUE },
                        isLoading = uiState.isLoading,
                        onOpen = { selectedTaskId = it.id },
                        onStart = taskViewModel::startTask,
                        onAdd = {
                            showTaskCapture = true
                        },
                    )

                    AppTab.AGENT -> if (!workspaceUiState.isLoading) AgentCenterScreen(
                        activeDate = activeDate,
                        occupiedEntries = agentTimelineEntries,
                        providerRevision = aiProviderConfigUiState.config.updatedAtEpochMillis,
                        canRefineWithAi = arrangementAssistantAccess.isEnabled &&
                            arrangementAssistantAccess.hasExplicitConsent && aiProviderConfigUiState.config.hasApiKey && aiProviderConfigUiState.supportsRemote,
                        onRefineWithAi = arrangementAssistantViewModel::refine,
                        existingTasks = uiState.tasks.filter { it.status.isActive }.take(50).map {
                            com.swan1127.repland.domain.model.ArrangementExistingTask(it.id, it.displayName, it.category, it.totalDurationMinutes)
                        },
                        onRefineWithContext = arrangementAssistantViewModel::refine,
                        availableIntervals = com.swan1127.repland.domain.model.ArrangementAvailability.forDay(
                            com.swan1127.repland.domain.model.PlanGenerationInput(uiState.tasks, timeUiState.weeklyBlocks,
                                timeUiState.dateOverrides, timeUiState.semesterFirstWeekMonday), activeDate, LocalDateTime.now()),
                        onConfirmChanges = planViewModel::saveAssistantChanges,
                        saveReceipt = planUiState.assistantReceipt,
                        onDismissReceipt = planViewModel::dismissAssistantReceipt,
                        onViewTasks = { selectedTab = AppTab.TASKS },
                        onViewSchedule = { date -> activeDate = date; selectedTab = AppTab.TODAY },
                        onSaveTasks = { drafts -> drafts.forEach(taskViewModel::saveTask) },
                        onConfirmBatch = planViewModel::saveTasksAndPlace,
                        initialWorkspace = workspaceUiState.assistant,
                        availableTracks = workspaceUiState.tracks,
                        hasExistingTasks = uiState.tasks.any { it.status.isActive },
                        onAddTask = { selectedTab = AppTab.TASKS; showTaskCapture = true },
                        hasAvailability = timeUiState.weeklyBlocks.any { it.kind == TimeBlockKind.AVAILABLE } ||
                            timeUiState.dateOverrides.any { it.type == DateOverrideType.AVAILABLE && !it.date.isBefore(LocalDate.now()) },
                        onConfigureAvailability = { planViewModel.dismissError(); showQuickAvailability = true },
                        contextRevision = PlanningRevision.of(com.swan1127.repland.domain.model.PlanGenerationInput(
                            uiState.tasks, timeUiState.weeklyBlocks, timeUiState.dateOverrides, timeUiState.semesterFirstWeekMonday,
                            categoryPreferences = categoryPreferenceUiState.weights), planUiState.currentPlan, planUiState.taskOrder),
                        onWorkspaceChanged = planViewModel::saveAssistantWorkspace,
                        isSaving = planUiState.isWorking,
                        onFormulatePlan = { generatePlanDraft(reorder = true) },
                        onArrangeExistingToday = { generatePlanDraft(todayOnly = LocalDate.now()) },
                        canFormulatePlan = !planUiState.isWorking && planUiState.draft == null && uiState.tasks.any { it.status.isActive },
                        onPlaceTask = { taskId, startMinute, endMinute, trackId ->
                            planViewModel.placeTask(taskId, activeDate, startMinute, endMinute, trackId)
                        },
                        onOpenTimeStudio = {
                            timeReturnTab = AppTab.AGENT
                            selectedTab = AppTab.TIME
                        },
                    )

                    AppTab.TIME -> TimeScreen(
                        timetableImport = timeUiState.timetableImport,
                        onImportPdf = timeViewModel::readTimetable,
                        onClearTimetableImport = timeViewModel::clearTimetableImport,
                    )
                    AppTab.MINE -> MineScreen(
                        weights = categoryPreferenceUiState.weights,
                        profileEvidence = profileEvidenceUiState.evidence,
                        isLoading = categoryPreferenceUiState.isLoading ||
                            reminderSettingsUiState.isLoading || profileEvidenceUiState.isLoading ||
                            planningAgentUiState.isLoading || aiProviderConfigUiState.isLoading,
                        onSave = categoryPreferenceViewModel::save,
                        remindersEnabled = reminderSettingsUiState.preferences.isEnabled,
                        notificationsAllowed = notificationPermissionGranted,
                        onRemindersEnabledChange = onReminderEnabledChange,
                        onGenerateProfileEvidence = profileEvidenceViewModel::generate,
                        onUpdateProfileEvidence = profileEvidenceViewModel::updateConclusion,
                        onDeleteProfileEvidence = profileEvidenceViewModel::delete,
                        onExportLocalData = {
                            localDataExportLauncher.launch("repland-local-export-${LocalDate.now()}.json")
                        },
                        onClearLocalData = { showClearLocalDataConfirmation = true },
                        isDataWorking = dataManagementUiState.isWorking,
                        dataResult = dataManagementUiState.result,
                        hasProfileActionError = profileEvidenceUiState.hasActionError,
                        aiEnabled = planningAgentUiState.preferences.isEnabled,
                        aiConsented = planningAgentUiState.preferences.hasExplicitConsent,
                        onAiEnabledChange = onAiEnabledChange,
                        aiProviderConfig = aiProviderConfigUiState.config,
                        remoteAiSupported = aiProviderConfigUiState.supportsRemote,
                        aiProviderConfigError = aiProviderConfigUiState.errorMessage,
                        aiProviderConnectionTest = aiProviderConfigUiState.connectionTest,
                        onSaveAiProviderConfig = aiProviderConfigViewModel::save,
                        onClearAiProviderKey = aiProviderConfigViewModel::clearApiKey,
                        onTestAiProviderConnection = aiProviderConfigViewModel::testConnection,
                        engagementMode = engagementMode,
                        onEngagementModeChange = engagementViewModel::setMode,
                    )
                }
            }
        }
    }

    if (showExecutionSession && executionSession != null) {
        com.swan1127.repland.ui.schedule.ExecutionSessionDialog(
            session = executionSession!!,
            busy = executionBusy,
            error = executionError,
            onDismiss = { showExecutionSession = false },
            onPause = { taskViewModel.pauseSession(executionSession!!.id) },
            onResume = { taskViewModel.resumeSession(executionSession!!.id) },
            onFinish = { outcome, feedback -> taskViewModel.finishSession(executionSession!!.id, outcome, feedback) },
        )
    }
    if (showTaskCapture) {
        TaskCaptureSheet(
            initialText = taskEditorSeed,
            onVoice = { showTaskCapture = false; showVoiceComposer = true; voiceError = false },
            onDismiss = { showTaskCapture = false; taskEditorSeed = "" },
            onSave = {
                taskViewModel.saveTask(it)
                showTaskCapture = false
                taskEditorSeed = ""
            },
        )
    }

    if (showTaskEditor) {
        TaskEditorDialog(
            task = taskEditorTarget,
            initialText = taskEditorSeed,
            onDismiss = { showTaskEditor = false; taskEditorSeed = "" },
            onSave = {
                taskViewModel.saveTask(it)
                showTaskEditor = false
                taskEditorSeed = ""
            },
        )
    }

    if (showVoiceComposer) {
        AlertDialog(
            onDismissRequest = { showVoiceComposer = false },
            title = { Text("说出你的安排") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("语音由设备选择的识别服务处理，可能联网；请先检查文字，再决定是否创建任务。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = voiceTranscript,
                        onValueChange = { voiceTranscript = it },
                        modifier = Modifier.fillMaxWidth().testTag("voice-transcript"),
                        label = { Text("识别文字（可编辑）") },
                        minLines = 3,
                    )
                    if (voiceError) Text("语音服务不可用或没有识别出内容；你仍可在这里输入。",
                        color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onVoiceCapture) { Text("开始语音识别") }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        taskEditorTarget = null
                        taskEditorSeed = voiceTranscript.trim()
                        showVoiceComposer = false
                        showTaskCapture = true
                    },
                    enabled = voiceTranscript.isNotBlank(),
                    modifier = Modifier.testTag("voice-to-task"),
                ) { Text("转成任务草稿") }
            },
            dismissButton = { TextButton(onClick = { showVoiceComposer = false }) { Text("取消") } },
        )
    }

    if (showAssistant) {
        val task = assistantTaskId?.let { id -> uiState.tasks.firstOrNull { it.id == id } }
            ?: selectedTask
        task?.let {
            AssistantSheet(
                taskTitle = it.displayName,
                onDismiss = { assistantTaskId = null; showAssistant = false },
                onSubmit = { prompt ->
                    assistantTaskId = null
                    showAssistant = false
                    val type = when {
                        prompt.contains("时间") || prompt.contains("多久") -> PlanningAgentRequestType.DIFFICULTY_AND_DURATION
                        prompt.contains("拆") || prompt.contains("步骤") -> PlanningAgentRequestType.TASK_BREAKDOWN
                        prompt.contains("调整") || prompt.contains("安排") -> PlanningAgentRequestType.REPLAN
                        else -> PlanningAgentRequestType.TASK_UNDERSTANDING
                    }
                    lastPlanningAgentTaskId = it.id
                    when (type) {
                        PlanningAgentRequestType.TASK_UNDERSTANDING -> planningAgentViewModel.beginTaskUnderstanding(it, executionLogs, planUiState.currentPlan)
                        PlanningAgentRequestType.DIFFICULTY_AND_DURATION -> planningAgentViewModel.beginDifficultyAndDuration(it, executionLogs, planUiState.currentPlan)
                        PlanningAgentRequestType.TASK_BREAKDOWN -> planningAgentViewModel.beginTaskBreakdown(it, executionLogs, planUiState.currentPlan)
                        PlanningAgentRequestType.REPLAN -> planningAgentViewModel.beginReplan(
                            affectedTasks = listOf(it), executionLogs = executionLogs,
                            currentPlan = planUiState.currentPlan,
                            localPlanInput = PlanGenerationInput(
                                tasks = uiState.tasks, weeklyBlocks = timeUiState.weeklyBlocks,
                                dateOverrides = timeUiState.dateOverrides,
                                semesterFirstWeekMonday = timeUiState.semesterFirstWeekMonday,
                                lockedSegments = planUiState.currentPlan?.segments?.filter { it.isLocked && !it.hasEndedBefore(LocalDateTime.now()) }.orEmpty(),
                                categoryPreferences = categoryPreferenceUiState.weights,
                                manualTaskOrder = planUiState.currentPlan?.takeIf { it.hasManualTaskOrder }?.orderedTaskIds.orEmpty(),
                            ),
                            constraintSummary = listOf("只处理未来安排，结果需要你确认。"),
                        )
                        else -> Unit
                    }
                },
            )
        }
    }

    partiallyCompletingTask?.let { task ->
        PartialCompletionDialog(
            task = task,
            onDismiss = { partiallyCompletingTask = null },
            onConfirm = { feedback ->
                taskViewModel.recordPartialCompletion(task.id, feedback)
                partiallyCompletingTask = null
            },
        )
    }

    feedbackTask?.let { task ->
        ExecutionFeedbackDialog(
            task = task,
            onDismiss = { feedbackTask = null },
            onConfirm = { feedback ->
                taskViewModel.recordFeedback(task.id, feedback)
                feedbackTask = null
            },
        )
    }

    postponingTask?.let { task ->
        PostponeTaskDialog(
            onDismiss = { postponingTask = null },
            onConfirm = { reason ->
                taskViewModel.postponeTask(task.id, reason)
                postponingTask = null
            },
        )
    }

    cancellingTask?.let { task ->
        ConfirmTaskStatusDialog(
            title = stringResource(R.string.task_cancel),
            message = stringResource(R.string.cancel_task_message),
            confirmLabel = stringResource(R.string.task_cancel),
            onDismiss = { cancellingTask = null },
            onConfirm = {
                taskViewModel.cancelTask(task.id)
                cancellingTask = null
            },
        )
    }

    replacingTask?.let { task ->
        TaskEditorDialog(
            task = task,
            dialogTitle = R.string.replace_task,
            confirmLabel = R.string.replace_task,
            allowPriorityChange = true,
            onDismiss = { replacingTask = null },
            onSave = { replacement ->
                taskViewModel.replaceTask(task.id, replacement.copy(id = null))
                replacingTask = null
            },
        )
    }

    correctingLog?.let { log ->
        CorrectExecutionLogDialog(
            log = log,
            onDismiss = { correctingLog = null },
            onConfirm = { feedback ->
                taskViewModel.correctExecutionLog(log.taskId, log.id, feedback)
                correctingLog = null
            },
        )
    }

    if (showWeeklyBlockEditor) {
        WeeklyTimeBlockEditorDialog(
            block = weeklyBlockEditorTarget,
            initialKind = weeklyBlockInitialKind,
            onDismiss = { showWeeklyBlockEditor = false; weeklyBlockInitialKind = TimeBlockKind.COURSE },
            onSave = {
                timeViewModel.saveWeeklyBlock(it)
                showWeeklyBlockEditor = false
                weeklyBlockInitialKind = TimeBlockKind.COURSE
            },
        )
    }

    if (showDateOverrideEditor) {
        DateOverrideEditorDialog(
            dateOverride = dateOverrideEditorTarget,
            onDismiss = { showDateOverrideEditor = false },
            onSave = {
                timeViewModel.saveDateOverride(it)
                showDateOverrideEditor = false
            },
        )
    }

    if (showSemesterStartEditor) {
        SemesterStartEditorDialog(
            semesterFirstWeekMonday = timeUiState.semesterFirstWeekMonday,
            onDismiss = { showSemesterStartEditor = false },
            onSave = {
                timeViewModel.saveSemesterFirstWeekMonday(it)
                showSemesterStartEditor = false
            },
        )
    }

    deletingWeeklyBlock?.let { block ->
        DeleteTimeEntryDialog(
            title = block.title,
            onDismiss = { deletingWeeklyBlock = null },
            onConfirm = {
                timeViewModel.deleteWeeklyBlock(block.id)
                deletingWeeklyBlock = null
            },
        )
    }

    deletingDateOverride?.let { dateOverride ->
        DeleteTimeEntryDialog(
            title = dateOverride.title,
            onDismiss = { deletingDateOverride = null },
            onConfirm = {
                timeViewModel.deleteDateOverride(dateOverride.id)
                deletingDateOverride = null
            },
        )
    }

    planUiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = planViewModel::dismissError,
            title = { Text("安排未保存") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = planViewModel::dismissError) { Text("知道了") } },
        )
    }
    if (showQuickAvailability) {
        com.swan1127.repland.ui.plan.QuickAvailabilityDialog(
            busy = planUiState.isWorking, error = planUiState.errorMessage,
            onDismiss = { showQuickAvailability = false },
            onSave = { value -> planViewModel.saveAvailabilityAndGenerate(value, categoryPreferenceUiState.weights) { showQuickAvailability = false } },
        )
    }
    planUiState.draft?.takeIf { !showQuickAvailability && !(showExecutionSession && executionSession != null) }?.let { draft ->
        PlanDraftDialog(
            draft = draft,
            currentPlan = planUiState.currentPlan,
            currentTaskOrder = planUiState.taskOrder,
            isSaving = planUiState.isWorking,
            tracks = workspaceUiState.tracks,
            onCompleteTaskDetails = { task -> planViewModel.discardDraft(); taskEditorTarget = task; showTaskEditor = true },
            onConfigureAvailability = { planViewModel.dismissError(); showQuickAvailability = true },
            tasks = uiState.tasks,
            weeklyBlocks = timeUiState.weeklyBlocks,
            dateOverrides = timeUiState.dateOverrides,
            semesterFirstWeekMonday = timeUiState.semesterFirstWeekMonday,
            onDismiss = {
                planViewModel.discardDraft()
                if (reviewingAgentDraft) {
                    planningAgentViewModel.dismiss()
                    reviewingAgentDraft = false
                }
            },
            onUpdateDraft = planViewModel::updateDraft,
            onAccept = {
                if (reviewingAgentDraft) {
                    planViewModel.acceptDraft {
                        planningAgentViewModel.markAccepted()
                        reviewingAgentDraft = false
                    }
                } else {
                    planViewModel.acceptDraft()
                }
            },
        )
    }

    if (showDailyReview) {
        DailyReviewDialog(
            date = reviewDate,
            summary = DailyReviewSummarizer.summarize(
                date = reviewDate,
                tasks = uiState.tasks,
                plan = planUiState.currentPlan,
                logs = dailyLogs,
            ),
            tasks = uiState.tasks,
            isAiEnabled = planningAgentUiState.preferences.isEnabled,
            planningAgentState = planningAgentUiState.workflow,
            onRequestAgentSummary = {
                planningAgentViewModel.beginDailySummary(reviewDate, dailyLogs)
            },
            onDismiss = { showDailyReview = false },
        )
    }

    if (showPlanOverview) {
        PlanOverviewDialog(
            plans = planUiState.planHistory,
            tasks = uiState.tasks,
            onDismiss = { showPlanOverview = false },
            onRestore = { planId ->
                planViewModel.restore(planId)
                showPlanOverview = false
            },
            onClearCurrent = {
                showPlanOverview = false
                showClearPlanConfirmation = true
            },
            onToggleSegmentLock = planViewModel::setSegmentLocked,
        )
    }

    if (showClearPlanConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearPlanConfirmation = false },
            title = { Text(stringResource(R.string.clear_current_plan_title)) },
            text = { Text(stringResource(R.string.clear_current_plan_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        planViewModel.clearCurrentPlan()
                        showClearPlanConfirmation = false
                    },
                ) { Text(stringResource(R.string.clear_current_plan), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearPlanConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showClearLocalDataConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearLocalDataConfirmation = false },
            title = { Text(stringResource(R.string.clear_local_data_title)) },
            text = { Text(stringResource(R.string.clear_local_data_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        // The old plan IDs are still available here, so their local alarms
                        // are cancelled before the explicit database reset removes them.
                        reminderScheduler.sync(
                            isEnabled = false,
                            currentPlan = null,
                            planHistory = planUiState.planHistory,
                            tasks = uiState.tasks,
                        )
                        dataManagementViewModel.clearAllLocalData()
                        showClearLocalDataConfirmation = false
                    },
                ) {
                    Text(stringResource(R.string.clear_local_data_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLocalDataConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showAiConsentDialog || planningAgentUiState.workflow is PlanningAgentState.AwaitingConsent) {
        AiConsentDialog(
            onDismiss = {
                showAiConsentDialog = false
                if (planningAgentUiState.workflow is PlanningAgentState.AwaitingConsent) {
                    planningAgentViewModel.dismiss()
                }
            },
            onConfirm = {
                planningAgentViewModel.grantConsentAndEnable()
                showAiConsentDialog = false
            },
        )
    }

    (planningAgentUiState.workflow as? PlanningAgentState.PreviewingRequest)?.let { preview ->
        AiRequestPreviewDialog(
            request = preview.request,
            onDismiss = planningAgentViewModel::dismiss,
            onConfirm = planningAgentViewModel::confirmPreview,
        )
    }
}

@Composable
private fun TabIcon(tab: AppTab) {
    val icon = when (tab) {
        AppTab.TODAY -> Icons.Outlined.DateRange
        AppTab.TASKS -> Icons.AutoMirrored.Outlined.List
        AppTab.AGENT -> Icons.Outlined.Add
        AppTab.TIME -> Icons.Outlined.DateRange
        AppTab.MINE -> Icons.Outlined.Person
    }
    Icon(icon, contentDescription = null)
}

@Composable
private fun TodayScreen(
    executionSession: com.swan1127.repland.domain.model.ExecutionSession?,
    executionError: String?,
    onResumeExecution: () -> Unit,
    executionFinished: Boolean,
    onReplanRemaining: () -> Unit,
    onDismissExecutionResult: () -> Unit,
    planNeedsUpdate: Boolean,
    onReviewPlanChanges: () -> Unit,
    date: LocalDate,
    tasks: List<Task>,
    allTasks: List<Task>,
    weeklyBlocks: List<WeeklyTimeBlock>,
    dateOverrides: List<DateOverride>,
    semesterFirstWeekMonday: LocalDate?,
    engagementMode: EngagementMode,
    tracks: List<RhythmTrack>,
    onTracksChanged: (List<RhythmTrack>) -> Unit,
    onOpenTimelineEntry: (String) -> Unit,
    onEditTimelineEntry: (TimelineEntry) -> Unit,
    planSegments: List<PlannedSegment>,
    allPlanSegments: List<PlannedSegment>,
    hasConfirmedPlan: Boolean,
    pendingConfirmationSegments: List<PlannedSegment>,
    isLoading: Boolean,
    onOpen: (Task) -> Unit,
    onStart: (String) -> Unit,
    onOpenDailyReview: () -> Unit,
    onAdd: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenTaskLibrary: () -> Unit,
    onPlaceEvent: (String, LocalDate, Int, Int, String) -> Unit,
    onFocusStarted: (String) -> Unit,
    onCreateCourse: (com.swan1127.repland.ui.schedule.CourseInsertionRequest, LocalDate) -> Unit,
    onRemovePlacement: (String) -> Unit,
    onMoveEntry: (TimelineEntry, Int) -> Unit,
    onUpdateEntryTime: (TimelineEntry, Int, Int) -> Unit,
) {
    if (isLoading) return
    var scheduleRange by rememberSaveable { mutableStateOf(ScheduleRange.DAY) }
    var scheduleDate by remember { mutableStateOf(date) }
    var showPending by rememberSaveable { mutableStateOf(false) }
    var focusNow by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) { focusNow = LocalDateTime.now(); delay(30_000) }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(date) { scheduleDate = date }
    val taskById = allTasks.associateBy(Task::id)
    val timelineEntries = ScheduleTimeline.entries(scheduleDate, weeklyBlocks, dateOverrides, allPlanSegments, allTasks, semesterFirstWeekMonday)
    val nowMinute = focusNow.hour * 60 + focusNow.minute
    val pendingEntries = TodayFocus.pending(timelineEntries, focusNow, executionSession?.taskId)
    if (showPending) {
        AlertDialog(
            onDismissRequest = { showPending = false },
            title = { Text("确认任务结果") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("这些任务的安排时段已结束，但还没有确认整项结果。时间经过不会自动完成任务。")
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(pendingEntries, key = { it.taskId!! }) { entry ->
                            TextButton(onClick = { taskById[entry.taskId]?.let(onOpen); showPending = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(entry.title)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPending = false }) { Text("稍后确认") } },
        )
    }
    val currentOrNext = planSegments
        .filter { it.endMinute > nowMinute }
        .minByOrNull { it.startMinute }
    val currentTask = currentOrNext?.let { taskById[it.taskId] }
    val plannedTaskIds = planSegments.map(PlannedSegment::taskId).toSet()
    val placedTaskIdsForScheduleDate = allPlanSegments.filter { it.date == scheduleDate }.map(PlannedSegment::taskId).toSet()
    val scheduledMinutes = planSegments.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (executionSession != null) {
            item {
                OutlinedButton(onClick = onResumeExecution, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("resume-execution")) {
                    Text("${if (executionSession.isPaused) "已暂停" else "专注中"} · ${executionSession.taskTitle} · 返回本轮")
                }
            }
        }
        if (executionError != null) {
            item { Text(executionError, color = MaterialTheme.colorScheme.error) }
        }
        if (executionFinished) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("本轮记录已保存。可依据你确认的进度重新安排剩余任务；确认预览前，原计划不变。")
                    OutlinedButton(onClick = onReplanRemaining, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("execution-replan")) {
                        Text("预览剩余任务的新安排")
                    }
                    TextButton(onClick = onDismissExecutionResult, modifier = Modifier.heightIn(min = 48.dp)) { Text("保留原计划") }
                }
            }
        }
        if (planNeedsUpdate && executionSession == null && !executionFinished) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("任务或时间信息已变化，当前计划仍保持不变。需要时可重新预览安排。")
                    OutlinedButton(onClick = onReviewPlanChanges, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("review-changed-plan")) {
                        Text("预览更新后的安排")
                    }
                }
            }
        }
        if (scheduleRange == ScheduleRange.DAY) {
            item {
                TodayFocalOverview(
                    nextEntries = timelineEntries.filter { !it.taskClosed && it.end.isAfter(focusNow) }.sortedBy(TimelineEntry::startMinute),
                    now = focusNow,
                    hasExecution = executionSession != null,
                    pendingCount = pendingEntries.size,
                    onOpenPending = { showPending = true },
                    onStart = onFocusStarted,
                    unplacedCount = allTasks.count { it.status.isActive && com.swan1127.repland.domain.model.TaskPlanMembership.isInbox(it, allPlanSegments, focusNow.toLocalDate()) },
                    onOpenSchedule = {
                        val prefix = (if (executionSession != null) 1 else 0) +
                            (if (executionError != null) 1 else 0) + (if (executionFinished) 1 else 0) +
                            (if (planNeedsUpdate && executionSession == null && !executionFinished) 1 else 0)
                        scope.launch { listState.scrollToItem(prefix + 2) }
                    },
                    onOpenTasks = onOpenTaskLibrary,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = scheduleRange == ScheduleRange.DAY, onClick = { scheduleRange = ScheduleRange.DAY }, label = { Text("日") }, modifier = Modifier.testTag("today-timeline"))
                FilterChip(selected = scheduleRange == ScheduleRange.WEEK, onClick = { scheduleRange = ScheduleRange.WEEK }, label = { Text("周") }, modifier = Modifier.testTag("week-schedule"))
                FilterChip(selected = scheduleRange == ScheduleRange.MONTH, onClick = { scheduleRange = ScheduleRange.MONTH }, label = { Text("月") }, modifier = Modifier.testTag("month-schedule"))
            }
        }
        if (scheduleRange == ScheduleRange.DAY) {
            item {
                TimelineDashboard(
                    entries = timelineEntries,
                    persistedTracks = tracks,
                    onTracksChanged = onTracksChanged,
                    mode = engagementMode,
                    onOpenEntry = onOpenTimelineEntry,
                    onOpenTask = { id -> taskById[id]?.let(onOpen) },
                    onEditEntry = onEditTimelineEntry,
                    eventObjects = allTasks
                        .filter { it.id !in placedTaskIdsForScheduleDate && it.status.isActive }
                        .map { TimelineEventObject(it.id, it.displayName, it.totalDurationMinutes, it.category) },
                    onPlaceEvent = { event, trackId, startMinute ->
                        val duration = event.durationMinutes?.coerceIn(15, 240) ?: 30
                        onPlaceEvent(event.id, scheduleDate, startMinute, (startMinute + duration).coerceAtMost(1440), trackId)
                    },
                    scheduleDate = scheduleDate,
                    onCreateCourse = { request -> onCreateCourse(request, scheduleDate) },
                    onFocusStarted = onFocusStarted,
                    onRemovePlacement = onRemovePlacement,
                    onMoveEntry = onMoveEntry,
                    onUpdateEntryTime = onUpdateEntryTime,
                )
            }
        } else if (scheduleRange == ScheduleRange.WEEK) {
            item {
                WeekScheduleView(
                    date = scheduleDate,
                    weeklyBlocks = weeklyBlocks,
                    dateOverrides = dateOverrides,
                    planSegments = allPlanSegments,
                    tasks = allTasks,
                    semesterFirstWeekMonday = semesterFirstWeekMonday,
                    mode = engagementMode,
                    tracks = tracks,
                    onOpenEntry = onOpenTimelineEntry,
                )
            }
        } else if (scheduleRange == ScheduleRange.MONTH) {
            item {
                MonthScheduleView(
                    date = scheduleDate,
                    weeklyBlocks = weeklyBlocks,
                    dateOverrides = dateOverrides,
                    planSegments = allPlanSegments,
                    tasks = allTasks,
                    tracks = tracks,
                    semesterFirstWeekMonday = semesterFirstWeekMonday,
                    onSelectDate = { selectedDate -> scheduleDate = selectedDate; scheduleRange = ScheduleRange.DAY },
                    onOpenEntry = onOpenTimelineEntry,
                )
            }
        } else {
        item {
            DailyDesk(
                date = scheduleDate,
                entries = ScheduleTimeline.entries(scheduleDate, weeklyBlocks, dateOverrides, allPlanSegments, allTasks, semesterFirstWeekMonday),
                unplacedCount = allTasks.count { it.id !in placedTaskIdsForScheduleDate && it.status.isActive },
                unplacedTasks = allTasks.filter { it.id !in placedTaskIdsForScheduleDate && it.status.isActive },
                plannedMinutes = planSegments.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) },
                onOpenEntry = onOpenTimelineEntry,
                onOpenTask = onOpen,
                onOpenPlan = { scheduleRange = ScheduleRange.DAY },
                onAdd = onAdd,
            )
        }
        }
        if (scheduleRange != ScheduleRange.OVERVIEW) {
            item { TextButton(onClick = onOpenDailyReview) { Text(stringResource(R.string.open_daily_review)) } }
        }
    }
}

@Composable
internal fun TodayFocalOverview(
    nextEntries: List<TimelineEntry>,
    unplacedCount: Int,
    onOpenSchedule: () -> Unit,
    onOpenTasks: () -> Unit,
    now: LocalDateTime,
    hasExecution: Boolean,
    pendingCount: Int,
    onOpenPending: () -> Unit,
    onStart: (String) -> Unit,
) {
    val first = TodayFocus.next(nextEntries, now)
    val sameMoment = first?.let { lead -> nextEntries.count { it.startMinute == lead.startMinute } } ?: 0
    val phase = first?.let { ScheduleTimeline.clock(it, now).phase }
    val segmentId = TodayFocus.startSegment(first)
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("today-focus"),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (phase) {
                        TimelinePhase.ACTIVE -> "当前时段"
                        TimelinePhase.OVERRUN -> "已超时"
                        TimelinePhase.UPCOMING -> "下一项"
                        else -> "今天"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(first?.title ?: "接下来没有安排", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                first?.let { "${TimeBlockValidator.formatTime(it.startMinute)}–${TimeBlockValidator.formatTime(it.endMinute)}" } ?: "从任务库选择一件事，再放进日轨道。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (sameMoment > 1) {
                Text("同一时段还有 ${sameMoment - 1} 件并行事项", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (segmentId != null) {
                Button(onClick = { onStart(segmentId) }, enabled = !hasExecution, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("today-focus-start")) {
                    Text(if (phase == TimelinePhase.UPCOMING) "提前开始专注" else "开始专注")
                }
                if (hasExecution) Text("请先返回并结束当前专注，再开始另一项。", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenTasks, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("待安排 $unplacedCount") }
                TextButton(onClick = onOpenPending, enabled = pendingCount > 0, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("today-focus-pending")) { Text("待确认 $pendingCount") }
            }
            TextButton(onClick = onOpenSchedule, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("today-focus-schedule")) { Text("查看完整日程") }
        }
    }
}

@Composable
private fun TodayEmptyState(onAdd: () -> Unit, onVoiceCapture: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerLow)))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
                Icon(Icons.Outlined.DateRange, contentDescription = null, modifier = Modifier.padding(14.dp).size(26.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text("把今天，留给\n重要的事。", style = MaterialTheme.typography.headlineMedium)
            Text("还没有安排。先记下一件事，\n让一天从容开始。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd) { Text("添加第一件事") }
                TextButton(onClick = onVoiceCapture) { Text("说一句话") }
            }
        }
    }
}

@Composable
private fun CompactSectionHeader(title: String, count: Int, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text("  $count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun PendingConfirmationTaskCard(
    task: Task,
    segment: PlannedSegment,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("task-card-${task.displayName}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                "${segment.date}\\n${TimeBlockValidator.formatTime(segment.startMinute)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.pending_confirmation_open_task),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DailyReviewDialog(
    date: LocalDate,
    summary: com.swan1127.repland.domain.model.DailyReviewSummary,
    tasks: List<Task>,
    isAiEnabled: Boolean,
    planningAgentState: PlanningAgentState,
    onRequestAgentSummary: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tasksById = tasks.associateBy(Task::id)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.daily_review_title, date.toString())) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.daily_review_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DetailLine(
                    stringResource(R.string.daily_review_planned_segments),
                    summary.plannedSegmentCount.toString(),
                )
                DetailLine(
                    stringResource(R.string.daily_review_pending_segments),
                    summary.pendingSegmentCount.toString(),
                )
                DetailLine(
                    stringResource(R.string.daily_review_feedback_count),
                    summary.feedbackCount.toString(),
                )
                DetailLine(
                    stringResource(R.string.actual_duration_optional),
                    stringResource(R.string.duration_format, summary.actualDurationMinutes),
                )
                if (summary.progressByTask.isEmpty()) {
                    Text(
                        stringResource(R.string.daily_review_no_feedback),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(stringResource(R.string.daily_review_recorded_feedback), fontWeight = FontWeight.Medium)
                    summary.progressByTask.forEach { progress ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(progress.taskName, fontWeight = FontWeight.Medium)
                                progress.progressPercent?.let { percent ->
                                    Text(stringResource(R.string.progress_format, percent))
                                }
                                progress.completionResult?.let { result -> Text(result) }
                            }
                        }
                    }
                }
                summary.nextSuggestedTaskId?.let { taskId ->
                    tasksById[taskId]?.let { task ->
                        Text(
                            stringResource(R.string.daily_review_next_suggestion, task.displayName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (isAiEnabled) {
                    OutlinedButton(
                        onClick = onRequestAgentSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request-agent-daily-summary"),
                    ) { Text(stringResource(R.string.request_agent_daily_summary)) }
                }
                if (planningAgentState !is PlanningAgentState.Idle) {
                    PlanningAgentOutcomeCard(
                        state = planningAgentState,
                        onReviewDraft = {},
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.skip_daily_review)) }
        },
    )
}

@Composable
private fun PlannedTaskCard(
    task: Task,
    segment: PlannedSegment,
    onOpen: () -> Unit,
    onStart: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                TimeBlockValidator.formatTime(segment.startMinute),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                task.description.takeIf { it.isNotBlank() && it != task.displayName }?.let { intro ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        intro,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TaskStatusButton(task = task, onStart = onStart)
        }
    }
}

@Composable
internal fun TasksScreen(
    tasks: List<Task>,
    isLoading: Boolean,
    onOpen: (Task) -> Unit,
    onStart: (String) -> Unit,
    onAdd: () -> Unit,
    confirmedSegments: List<PlannedSegment> = emptyList(),
) {
    var view by rememberSaveable { mutableStateOf(TaskCenterView.OVERVIEW) }
    val activeTasks = tasks.filter { it.status.isActive }
    val today = LocalDate.now()
    val activeIds = activeTasks.map { it.id }.toSet()
    val placementLabels = confirmedSegments.filter { it.date >= today && it.taskId in activeIds }.sortedWith(compareBy({ it.date }, { it.startMinute }))
        .groupBy { it.taskId }.mapValues { (_, placements) ->
            val slot = placements.first()
            fun time(minute: Int) = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
            "已安排 · ${slot.date.monthValue}月${slot.date.dayOfMonth}日 ${time(slot.startMinute)}–${time(slot.endMinute)}"
        }
    val visibleTasks = when (view) {
        TaskCenterView.OVERVIEW -> activeTasks
        TaskCenterView.INBOX -> activeTasks.filter { com.swan1127.repland.domain.model.TaskPlanMembership.isInbox(it, confirmedSegments, today) }
        TaskCenterView.TODAY -> activeTasks.filter { com.swan1127.repland.domain.model.TaskPlanMembership.isToday(it, confirmedSegments, today) }
        TaskCenterView.OVERDUE -> activeTasks.filter { it.dueDate?.isBefore(today) == true }
        TaskCenterView.COMPLETED -> tasks.filter { !it.status.isActive }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TaskCenterView.entries.forEach { option ->
                FilterChip(selected = view == option, onClick = { view = option }, label = { Text(option.label) })
            }
        }
        if (!isLoading && visibleTasks.isEmpty()) {
            EmptyState(
                title = when (view) {
                    TaskCenterView.INBOX -> "收件箱是空的"
                    TaskCenterView.TODAY -> "今天还没有任务"
                    TaskCenterView.OVERDUE -> "没有逾期任务"
                    TaskCenterView.COMPLETED -> "还没有完成记录"
                    TaskCenterView.OVERVIEW -> "还没有需要处理的任务"
                },
                actionLabel = if (view == TaskCenterView.COMPLETED || view == TaskCenterView.OVERDUE) null else "添加任务",
                onAction = onAdd,
            )
        } else if (!isLoading) {
            if (view == TaskCenterView.OVERVIEW) {
                TaskControlCenter(tasks = activeTasks, onOpen = onOpen, onStart = onStart, confirmedSegments = confirmedSegments, placementLabels = placementLabels)
            } else {
                TaskList(tasks = visibleTasks, onOpen = onOpen, onStart = onStart, placementLabels = placementLabels)
            }
        }
    }
}

private enum class TaskCenterView(val label: String) {
    OVERVIEW("总览"), INBOX("待安排"), TODAY("今天"), OVERDUE("逾期"), COMPLETED("已完成"),
}

@Composable
private fun TaskControlCenter(
    tasks: List<Task>,
    onOpen: (Task) -> Unit,
    onStart: (String) -> Unit,
    confirmedSegments: List<PlannedSegment> = emptyList(),
    placementLabels: Map<String, String> = emptyMap(),
) {
    val today = LocalDate.now()
    val attention = tasks.filter {
        it.dueDate?.isBefore(today) == true ||
            it.scheduledForDate?.isBefore(today) == true ||
            it.status == TaskStatus.POSTPONED
    }
    val todayTasks = tasks.filter { com.swan1127.repland.domain.model.TaskPlanMembership.isToday(it, confirmedSegments, today) && it !in attention }
    val inbox = tasks.filter { com.swan1127.repland.domain.model.TaskPlanMembership.isInbox(it, confirmedSegments, today) && it !in attention && it !in todayTasks }
    val later = tasks.filter { it !in attention && it !in todayTasks && it !in inbox }
    var expandedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("每件事，都有位置", style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("进行中" to tasks.count { it.status == TaskStatus.IN_PROGRESS }, "待安排" to inbox.size, "需处理" to attention.size).forEach { (label, count) ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(count.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        listOf("需要处理" to attention, "今天" to todayTasks, "待安排" to inbox, "更晚" to later).forEach { (title, group) ->
            if (group.isNotEmpty()) {
                item(key = "heading-$title") {
                    CompactSectionHeader(title, group.size,
                        action = if (group.size > 3) { if (expandedGroup == title) "收起" else "查看全部" } else null,
                        onAction = { expandedGroup = if (expandedGroup == title) null else title })
                }
                items(if (expandedGroup == title) group else group.take(3), key = Task::id) { task ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        TaskRow(task, { onOpen(task) }, { onStart(task.id) }, emphasis = if (title == "需要处理") TaskRowEmphasis.ATTENTION else TaskRowEmphasis.NORMAL,
                            planningSummary = placementLabels[task.id])
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun TaskList(
    tasks: List<Task>,
    onOpen: (Task) -> Unit,
    onStart: (String) -> Unit,
    placementLabels: Map<String, String> = emptyMap(),
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(tasks, key = Task::id) { task ->
            TaskRow(task = task, onOpen = { onOpen(task) }, onStart = { onStart(task.id) }, planningSummary = placementLabels[task.id])
        }
    }
}

@Composable
private fun TaskCard(
    task: Task,
    onOpen: () -> Unit,
    onStart: () -> Unit,
) {
    TaskRow(task = task, onOpen = onOpen, onStart = onStart)
}

@Composable
private fun TaskStatusButton(task: Task, onStart: () -> Unit) {
    val canStart = task.status == TaskStatus.NOT_STARTED || task.status == TaskStatus.POSTPONED
    OutlinedButton(
        onClick = onStart,
        enabled = canStart,
        modifier = Modifier.testTag("start-task"),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
        Text(if (canStart) "开始" else stringResource(task.status.labelRes()))
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TaskDetailScreen(
    task: Task,
    executionLogs: List<TaskExecutionLog>,
    onBack: () -> Unit,
    onStart: (String) -> Unit,
    onEdit: () -> Unit,
    onPostpone: () -> Unit,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    onPartialCompletion: () -> Unit,
    onFeedback: () -> Unit,
    onOpenAssistant: () -> Unit,
    onReplace: () -> Unit,
    onRestore: () -> Unit,
    onCorrectLog: (TaskExecutionLog) -> Unit,
    onReviewAdjustmentDraft: () -> Unit,
    isAiEnabled: Boolean,
    planningAgentState: PlanningAgentState,
    onRequestAiAdvice: (PlanningAgentRequestType) -> Unit,
    onReviewAgentDraft: (PlanDraft) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = task.displayName,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("task-detail-back"),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("task-detail-scroll"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = stringResource(task.status.labelRes()),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.width(12.dp))
                            TaskStatusButton(task = task, onStart = { onStart(task.id) })
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = task.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        task.description.takeIf { it.isNotBlank() && it != task.displayName }?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailMetaPill(stringResource(task.category.labelRes()))
                            DetailMetaPill(stringResource(task.userPriority.labelRes()))
                            task.totalDurationMinutes?.let {
                                DetailMetaPill(stringResource(R.string.duration_format, it))
                            }
                        }
                    }
                }
            }
            item {
                DetailSection(title = stringResource(R.string.task_information), eyebrow = "任务上下文") {
                    DetailLine(stringResource(R.string.category), stringResource(task.category.labelRes()))
                    DetailLine(stringResource(R.string.priority), stringResource(task.userPriority.labelRes()))
                    DetailLine(
                        stringResource(R.string.estimated_days),
                        stringResource(R.string.estimated_days_format, task.estimatedDays),
                    )
                    DetailLine(
                        stringResource(R.string.duration_minutes_optional),
                        task.totalDurationMinutes?.let { stringResource(R.string.duration_format, it) }
                            ?: stringResource(R.string.unscheduled_duration),
                    )
                    task.dueDate?.let {
                        DetailLine(
                            stringResource(R.string.due_date_optional),
                            taskDateLabel(it),
                        )
                    }
                    task.scheduledForDate?.let {
                        DetailLine(
                            "计划日期",
                            taskDateLabel(it),
                        )
                    }
                }
            }
            (task.completionSummary != null || task.completionResult != null ||
                task.actualDurationMinutes != null || task.progressPercent != null).takeIf { it }?.let {
                item {
                    DetailSection(title = stringResource(R.string.latest_feedback), eyebrow = "最近一次记录") {
                        task.completionSummary?.let { content ->
                            DetailLine(stringResource(R.string.completed_content), content)
                        }
                        task.completionResult?.let { result ->
                            DetailLine(stringResource(R.string.completion_result), result)
                        }
                        task.actualDurationMinutes?.let { minutes ->
                            DetailLine(
                                stringResource(R.string.actual_duration_optional),
                                stringResource(R.string.duration_format, minutes),
                            )
                        }
                        task.progressPercent?.let { progress ->
                            DetailLine(
                                stringResource(R.string.progress_optional),
                                stringResource(R.string.progress_format, progress),
                            )
                        }
                    }
                }
            }
            if (task.status.isActive && task.postponeCount >= POSTPONEMENT_ADVICE_THRESHOLD) {
                item {
                    PostponementGuidance(
                        postponeCount = task.postponeCount,
                        onReviewAdjustmentDraft = onReviewAdjustmentDraft,
                        onEdit = onEdit,
                    )
                }
            }
            item {
                DetailSection(title = stringResource(R.string.execution_logs), eyebrow = "可回溯") {
                    if (executionLogs.isEmpty()) {
                        Text(
                            stringResource(R.string.no_execution_logs),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        executionLogs.forEach { log ->
                            ExecutionLogCard(log = log, onCorrect = { onCorrectLog(log) })
                        }
                    }
                }
            }
            item {
                DetailActions(
                    task = task,
                    onEdit = onEdit,
                    onPostpone = onPostpone,
                    onCancel = onCancel,
                    onComplete = onComplete,
                    onPartialCompletion = onPartialCompletion,
                    onFeedback = onFeedback,
                    onOpenAssistant = onOpenAssistant,
                    onReplace = onReplace,
                    onRestore = onRestore,
                    isAiEnabled = isAiEnabled,
                    onRequestAiAdvice = onRequestAiAdvice,
                )
            }
            if (planningAgentState !is PlanningAgentState.Idle) {
                item {
                    PlanningAgentOutcomeCard(
                        state = planningAgentState,
                        onReviewDraft = onReviewAgentDraft,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, eyebrow: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        eyebrow?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun DetailMetaPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun PostponementGuidance(
    postponeCount: Int,
    onReviewAdjustmentDraft: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.postponement_guidance_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                stringResource(R.string.postponement_guidance_message, postponeCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Button(onClick = onReviewAdjustmentDraft, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.review_adjustment_draft))
            }
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.adjust_task_scope_or_date))
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(16.dp))
        Text(value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExecutionLogCard(log: TaskExecutionLog, onCorrect: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(log.eventType.labelRes()),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    formatExecutionLogTime(log.createdAtEpochMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.confirmed_status_format, stringResource(log.confirmedStatus.labelRes())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            log.feedback.completedContent?.let {
                DetailLine(stringResource(R.string.completed_content), it)
            }
            log.feedback.completionResult?.let {
                DetailLine(stringResource(R.string.completion_result), it)
            }
            log.feedback.actualDurationMinutes?.let {
                DetailLine(stringResource(R.string.actual_duration_optional), stringResource(R.string.duration_format, it))
            }
            log.feedback.progressPercent?.let {
                DetailLine(stringResource(R.string.progress_optional), stringResource(R.string.progress_format, it))
            }
            log.feedback.postponeReason?.let {
                DetailLine(stringResource(R.string.postpone_reason), it)
            }
            log.correctedLogId?.let {
                Text(
                    stringResource(R.string.corrects_log_format, it.take(8)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            log.replacementTaskId?.let {
                Text(
                    stringResource(R.string.replacement_task_format, it.take(8)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(onClick = onCorrect) { Text(stringResource(R.string.correct_log)) }
        }
    }
}

@Composable
private fun DetailActions(
    task: Task,
    onEdit: () -> Unit,
    onPostpone: () -> Unit,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    onPartialCompletion: () -> Unit,
    onFeedback: () -> Unit,
    onOpenAssistant: () -> Unit,
    onReplace: () -> Unit,
    onRestore: () -> Unit,
    isAiEnabled: Boolean,
    onRequestAiAdvice: (PlanningAgentRequestType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (task.status.isActive) {
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().testTag("complete-task"),
            ) {
                Text(stringResource(R.string.complete))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPartialCompletion,
                    modifier = Modifier.weight(1f).testTag("partial-completion"),
                ) { Text(stringResource(R.string.partial_completion)) }
                OutlinedButton(onClick = onPostpone, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.postpone))
                }
            }
            TextButton(onClick = onReplace, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.replace_task))
            }
        }
        if (task.status != TaskStatus.REPLACED) {
            OutlinedButton(
                onClick = onFeedback,
                modifier = Modifier.fillMaxWidth().testTag("record-feedback"),
            ) {
                Text(stringResource(R.string.record_feedback))
            }
        }
        if (task.status != TaskStatus.REPLACED) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.edit_task))
            }
        }
        if (isAiEnabled && task.status != TaskStatus.REPLACED) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("需要一点协助？", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "一起拆解任务，找到合适的下一步。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    OutlinedButton(onClick = onOpenAssistant, modifier = Modifier.fillMaxWidth().testTag("request-ai-advice")) {
                        Text("打开对话")
                    }
                }
            }
        }
        if (task.status.isActive) {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.task_cancel),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.CANCELLED) {
            TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.restore_task))
            }
        }
    }
}

@Composable
private fun PlanningAgentOutcomeCard(
    state: PlanningAgentState,
    onReviewDraft: (PlanDraft) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("planning-agent-outcome"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.ai_advice_result_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            when (state) {
                PlanningAgentState.Idle -> Unit
                is PlanningAgentState.PreparingContext,
                is PlanningAgentState.RequestingAdvice,
                is PlanningAgentState.ValidatingAdvice -> Text(stringResource(R.string.ai_requesting))

                is PlanningAgentState.AwaitingConsent,
                is PlanningAgentState.PreviewingRequest -> Text(stringResource(R.string.ai_request_preview_notice))

                is PlanningAgentState.ShowingAdvice -> {
                    EditableAiAdvice(response = state.response)
                }

                is PlanningAgentState.ShowingDraft -> AgentDraftCardContent(
                    explanation = state.explanation,
                    draft = state.draft,
                    onReviewDraft = onReviewDraft,
                )

                is PlanningAgentState.FailedFallback -> {
                    Text(
                        stringResource(state.reason.labelRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    when (val fallback = state.fallback) {
                        is LocalPlanningFallback.Advice -> Text(
                            fallback.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )

                        is LocalPlanningFallback.Draft -> AgentDraftCardContent(
                            explanation = fallback.explanation,
                            draft = fallback.draft,
                            onReviewDraft = onReviewDraft,
                        )
                    }
                }

                is PlanningAgentState.UserAccepted -> Text(stringResource(R.string.agent_draft_accepted))
                is PlanningAgentState.UserDismissed -> Text(stringResource(R.string.agent_draft_dismissed))
            }
        }
    }
}

@Composable
private fun EditableAiAdvice(response: AiAdvisorResponse) {
    val initialAdvice = when (response) {
        is AiTaskUnderstandingAdvice -> response.summary
        is AiDifficultyAndDurationAdvice -> response.explanation
        is AiTaskBreakdownAdvice -> response.steps.joinToString(separator = "\n") + "\n" + response.explanation
        is AiSortingExplanationAdvice -> response.explanation
        is AiDailySummaryAdvice -> listOfNotNull(response.summary, response.suggestedNextStep)
            .joinToString(separator = "\n")
        else -> ""
    }
    var editedAdvice by remember(response) { mutableStateOf(initialAdvice) }
    OutlinedTextField(
        value = editedAdvice,
        onValueChange = { editedAdvice = it.take(2_000) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agent-advice-editor"),
        label = { Text(stringResource(R.string.editable_agent_advice)) },
        minLines = 3,
    )
    Text(
        stringResource(R.string.ai_advice_needs_review),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}

@Composable
private fun AgentDraftCardContent(
    explanation: String,
    draft: PlanDraft,
    onReviewDraft: (PlanDraft) -> Unit,
) {
    Text(explanation, style = MaterialTheme.typography.bodyMedium)
    Text(
        stringResource(R.string.agent_draft_review_notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
    )
    OutlinedButton(
        onClick = { onReviewDraft(draft) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("review-agent-draft"),
    ) {
        Text(stringResource(R.string.review_agent_draft))
    }
}

@Composable
private fun AiConsentDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_consent_title)) },
        text = {
            Text(stringResource(R.string.ai_consent_message), modifier = Modifier.verticalScroll(rememberScrollState()))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("ai-consent-confirm"),
            ) { Text(stringResource(R.string.ai_consent_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun AiRequestPreviewDialog(
    request: AiAdvisorRequest,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val tasks = when (request) {
        is AiTaskUnderstandingRequest -> listOf(request.task)
        is AiDifficultyAndDurationRequest -> listOf(request.task)
        is AiTaskBreakdownRequest -> listOf(request.task)
        is AiSortingExplanationRequest -> listOf(request.task)
        is AiReplanRequest -> request.affectedTasks
        is AiDailySummaryRequest -> emptyList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_request_preview_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.ai_request_preview_notice),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (tasks.isEmpty()) {
                    val feedbackCount = (request as AiDailySummaryRequest).confirmedFeedback.size
                    Text(
                        stringResource(R.string.ai_request_preview_daily_feedback, feedbackCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    if (tasks.size > 1) {
                        Text(
                            stringResource(R.string.ai_request_preview_task_count, tasks.size),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    tasks.forEach { task ->
                        DetailLine(stringResource(R.string.task_name), task.title)
                        DetailLine(
                            stringResource(R.string.task_description),
                            task.description.ifBlank { stringResource(R.string.no_introduction) },
                        )
                        DetailLine(stringResource(R.string.category), stringResource(task.category.labelRes()))
                        DetailLine(stringResource(R.string.priority), stringResource(task.initialPriority.labelRes()))
                        DetailLine(stringResource(R.string.current_status), stringResource(task.currentStatus.labelRes()))
                        Text(
                            stringResource(R.string.ai_request_preview_feedback_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        if (task.recentFeedback.isEmpty()) {
                            Text(stringResource(R.string.ai_request_preview_no_feedback), style = MaterialTheme.typography.bodySmall)
                        } else {
                            task.recentFeedback.forEachIndexed { index, feedback ->
                                Text(
                                    text = stringResource(
                                        R.string.ai_request_preview_feedback_format,
                                        index + 1,
                                        feedback.actualDurationMinutes?.let { "$it 分钟" } ?: "—",
                                        feedback.progressPercent?.let { "$it%" } ?: "—",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.ai_request_preview_segments_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        if (task.confirmedSegments.isEmpty()) {
                            Text(stringResource(R.string.ai_request_preview_no_segments), style = MaterialTheme.typography.bodySmall)
                        } else {
                            task.confirmedSegments.forEach { segment ->
                                Text(
                                    "${segment.date} · ${TimeBlockValidator.formatTime(segment.startMinute)}–" +
                                        TimeBlockValidator.formatTime(segment.endMinute),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.ai_request_preview_excluded_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("ai-request-confirm"),
            ) { Text(stringResource(R.string.ai_request_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun TimeScreen(
    timetableImport: TimetableImportState,
    onImportPdf: (Uri) -> Unit,
    onClearTimetableImport: () -> Unit,
) {
    val timetablePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(onImportPdf) },
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("导入固定课程", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "选择课表 PDF 后，识别到的课程会直接进入日与周视图。课程在日轨道上可长按拖动调整时间。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("课表 PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.import_timetable_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Button(
                    onClick = { timetablePicker.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.fillMaxWidth().testTag("import-timetable-pdf"),
                ) { Text(stringResource(R.string.import_timetable_pdf)) }
            }
        }
        when (timetableImport) {
            TimetableImportState.Idle,
            is TimetableImportState.Review -> Unit

            TimetableImportState.Reading -> {
                Text(
                    "正在导入课表…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            is TimetableImportState.Failed -> {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            timetableImport.message,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = onClearTimetableImport) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SemesterWeekCard(
    semesterFirstWeekMonday: LocalDate?,
    onEdit: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.semester_week_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = if (semesterFirstWeekMonday == null) {
                        stringResource(R.string.semester_week_unset)
                    } else {
                        stringResource(
                            R.string.semester_week_set,
                            semesterFirstWeekMonday.toString(),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onEdit) {
                Text(
                    stringResource(
                        if (semesterFirstWeekMonday == null) R.string.set_semester_week
                        else R.string.edit_semester_week,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SemesterStartEditorDialog(
    semesterFirstWeekMonday: LocalDate?,
    onDismiss: () -> Unit,
    onSave: (LocalDate) -> Unit,
) {
    var dateText by remember(semesterFirstWeekMonday) {
        mutableStateOf(semesterFirstWeekMonday?.toString().orEmpty())
    }
    var showValidationError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.semester_week_editor_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.semester_week_date)) },
                    placeholder = { Text(stringResource(R.string.semester_week_editor_hint)) },
                    singleLine = true,
                )
                if (showValidationError) {
                    Text(
                        stringResource(R.string.semester_week_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
                    if (date?.dayOfWeek == DayOfWeek.MONDAY) onSave(date)
                    else showValidationError = true
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun TimeListHeading(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun TimeEmptyState(
    @StringRes titleRes: Int,
    onAdd: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAdd) { Text(stringResource(R.string.add)) }
        }
    }
}

@Composable
private fun WeeklyBlockCard(
    block: WeeklyTimeBlock,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(block.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(5.dp))
                Text(
                    text = stringResource(block.dayOfWeek.labelRes()) + " · " +
                        TimeBlockValidator.formatTime(block.startMinute) + "–" +
                        TimeBlockValidator.formatTime(block.endMinute),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(block.kind.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                block.weekPattern?.let { pattern ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        pattern,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun TimetableImportReviewDialog(
    courses: List<ImportedCourse>,
    onDismiss: () -> Unit,
    onConfirm: (List<ImportedCourse>) -> Unit,
) {
    var reviewedCourses by remember(courses) { mutableStateOf(courses) }
    var selectedIds by remember(courses) { mutableStateOf(courses.map(ImportedCourse::id).toSet()) }
    var editingCourse by remember { mutableStateOf<ImportedCourse?>(null) }
    val selectedCourses = reviewedCourses.filter { it.id in selectedIds }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timetable_import_review)) },
        text = {
            Column(
                modifier = Modifier
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.import_default_period_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                reviewedCourses.forEach { course ->
                    val draft = ClassPeriodClock.toWeeklyTimeBlockDraft(course)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedIds = if (course.id in selectedIds) {
                                    selectedIds - course.id
                                } else {
                                    selectedIds + course.id
                                }
                            },
                        verticalAlignment = Alignment.Top,
                    ) {
                        Checkbox(
                            checked = course.id in selectedIds,
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + course.id else selectedIds - course.id
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(course.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(
                                    R.string.course_period_format,
                                    stringResource(course.dayOfWeek.labelRes()),
                                    course.startPeriod,
                                    course.endPeriod,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            draft?.let {
                                Text(
                                    TimeBlockValidator.formatTime(it.startMinute) + "–" +
                                        TimeBlockValidator.formatTime(it.endMinute),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            course.weekPattern?.let { pattern ->
                                Text(
                                    pattern,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { editingCourse = course }) {
                                Text(stringResource(R.string.edit_imported_course))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedCourses) },
                enabled = selectedCourses.isNotEmpty(),
            ) {
                Text(stringResource(R.string.import_selected_format, selectedCourses.size))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
    editingCourse?.let { course ->
        ImportedCourseEditorDialog(
            course = course,
            onDismiss = { editingCourse = null },
            onSave = { updated ->
                reviewedCourses = reviewedCourses.map { current ->
                    if (current.id == updated.id) updated else current
                }
                editingCourse = null
            },
        )
    }
}

@Composable
private fun ImportedCourseEditorDialog(
    course: ImportedCourse,
    onDismiss: () -> Unit,
    onSave: (ImportedCourse) -> Unit,
) {
    var title by remember(course) { mutableStateOf(course.title) }
    var dayOfWeek by remember(course) { mutableStateOf(course.dayOfWeek) }
    var startPeriodText by remember(course) { mutableStateOf(course.startPeriod.toString()) }
    var endPeriodText by remember(course) { mutableStateOf(course.endPeriod.toString()) }
    var weekPattern by remember(course) { mutableStateOf(course.weekPattern.orEmpty()) }
    var showValidationError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_imported_course)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.time_block_name)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.day_of_week), style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = DayOfWeek.entries.toList(),
                    selected = dayOfWeek,
                    label = { stringResource(it.labelRes()) },
                    onSelected = { dayOfWeek = it },
                )
                OutlinedTextField(
                    value = startPeriodText,
                    onValueChange = { startPeriodText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.start_period)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = endPeriodText,
                    onValueChange = { endPeriodText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.end_period)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = weekPattern,
                    onValueChange = { weekPattern = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.week_pattern_optional)) },
                    singleLine = true,
                )
                if (showValidationError) {
                    Text(stringResource(R.string.imported_course_error), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val startPeriod = startPeriodText.toIntOrNull() ?: -1
                    val endPeriod = endPeriodText.toIntOrNull() ?: -1
                    if (title.isNotBlank() && startPeriod in 1..12 && endPeriod in startPeriod..12) {
                        onSave(
                            course.copy(
                                title = title.trim(),
                                dayOfWeek = dayOfWeek,
                                startPeriod = startPeriod,
                                endPeriod = endPeriod,
                                weekPattern = weekPattern.trim().takeIf(String::isNotBlank),
                            ),
                        )
                    } else {
                        showValidationError = true
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun PlanDraftDialog(
    draft: PlanDraft,
    tasks: List<Task>,
    weeklyBlocks: List<WeeklyTimeBlock>,
    dateOverrides: List<DateOverride>,
    semesterFirstWeekMonday: LocalDate?,
    onDismiss: () -> Unit,
    onUpdateDraft: (PlanDraft) -> Unit,
    onAccept: () -> Unit,
    tracks: List<RhythmTrack> = emptyList(),
    onCompleteTaskDetails: (Task) -> Unit = {},
    onConfigureAvailability: () -> Unit = {},
    currentPlan: ConfirmedPlan? = null,
    currentTaskOrder: List<String> = emptyList(),
    isSaving: Boolean = false,
) {
    val tasksById = tasks.associateBy(Task::id)
    val unknownTaskLabel = stringResource(R.string.unknown_task)
    val orderedTaskIds = draft.orderedTaskIds
        .distinct()
        .filter(tasksById::containsKey)
        .ifEmpty { (draft.segments.map(PlannedSegment::taskId) + draft.pendingTaskIds).distinct() }
    val priorityAssessmentsByTask = draft.priorityAssessments.associateBy(LocalPriorityAssessment::taskId)
    var editingSegmentId by remember { mutableStateOf<String?>(null) }
    var showTaskOrder by rememberSaveable { mutableStateOf(draft.orderOnly) }
    var showChanges by rememberSaveable { mutableStateOf(false) }
    val changes = PlanDraftReview.changes(draft, currentPlan, currentTaskOrder)
    val now = java.time.LocalDateTime.now()
    val protectedCurrent = currentPlan?.segments.orEmpty().filter {
        it.isLocked || it.date < now.toLocalDate() || (it.date == now.toLocalDate() && it.startMinute <= now.hour * 60 + now.minute)
    }
    val protectedIds = (protectedCurrent.map { it.id } + draft.segments.filter { candidate -> protectedCurrent.any { it.taskId == candidate.taskId } }.map { it.id }).toSet()
    EditorSheet(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.orderOnly) "排序预览" else stringResource(R.string.plan_draft_title)) },
        text = {
            Column(
                modifier = Modifier
                    .height(500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (draft.orderOnly) "只调整任务列表顺序，不改变日程时段。" else stringResource(R.string.plan_draft_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("确认后：${changes.schedules.size} 项日程变化，${changes.order.size} 项顺序变化。取消不会改动当前计划。",
                    modifier = Modifier.testTag("draft-change-summary"), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { showChanges = !showChanges }, modifier = Modifier.testTag("draft-show-changes")) {
                    Text(if (showChanges) "收起变化" else "查看具体变化")
                }
                if (showChanges) {
                    changes.schedules.forEach { change ->
                        fun placementLabel(segments: List<PlannedSegment>) = if (segments.isEmpty()) "未安排" else segments.joinToString("；") {
                            "${it.date} ${TimeBlockValidator.formatTime(it.startMinute)}–${TimeBlockValidator.formatTime(it.endMinute)} · ${draftTrackLabel(it.trackId, tracks)}${if (it.isLocked) "（锁定）" else ""}"
                        }
                        Text(tasksById[change.taskId]?.displayName ?: unknownTaskLabel, style = MaterialTheme.typography.titleSmall)
                        Text("原：${placementLabel(change.before)}\n新：${placementLabel(change.after)}", style = MaterialTheme.typography.bodySmall)
                    }
                    changes.order.forEach { change ->
                        Text("${tasksById[change.taskId]?.displayName ?: unknownTaskLabel}：${change.before?.let { "第 $it 位" } ?: "未入排序"} → 第 ${change.after} 位", style = MaterialTheme.typography.bodySmall)
                    }
                    if (changes.schedules.isEmpty() && changes.order.isEmpty()) Text("与当前计划和顺序一致。")
                }
                if (!draft.orderOnly && weeklyBlocks.none { it.kind == TimeBlockKind.AVAILABLE } && dateOverrides.none { it.type == DateOverrideType.AVAILABLE }) {
                    Text("还没有可用时间，系统不会把所有空白都当作可以工作。", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onConfigureAvailability, modifier = Modifier.testTag("draft-configure-availability")) { Text("补充时间后重新生成") }
                }
                if (!draft.orderOnly) PlanDraftTimelinePreview(
                    draft = draft,
                    tasks = tasks,
                    weeklyBlocks = weeklyBlocks,
                    dateOverrides = dateOverrides,
                    semesterFirstWeekMonday = semesterFirstWeekMonday,
                    onEditSegment = { editingSegmentId = it },
                    tracks = tracks,
                )
                draft.pendingTaskIds.takeIf { !draft.orderOnly && it.isNotEmpty() }?.let { pendingIds ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${stringResource(R.string.plan_pending_tasks)} · ${pendingIds.size}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                pendingIds.joinToString("、") { taskId ->
                                    tasksById[taskId]?.displayName ?: unknownTaskLabel
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            pendingIds.mapNotNull(tasksById::get).filter { it.totalDurationMinutes == null }.forEach { task ->
                                TextButton(onClick = { onCompleteTaskDetails(task) }, modifier = Modifier.testTag("draft-complete-details-${task.id}")) { Text("补充 ${task.displayName} 的时长后重新生成") }
                            }
                        }
                    }
                }
                draft.unscheduledTasks.takeIf(List<*>::isNotEmpty)?.let { unscheduledTasks ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "未排入 / 暂不安排",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            unscheduledTasks.forEach { unscheduled ->
                                // This is unassigned workload, not a promise of one continuous free slot.
                                Text(
                                    if (unscheduled.reason == UnscheduledReason.USER_DEFERRED && unscheduled.remainingMinutes == 0) "${tasksById[unscheduled.taskId]?.displayName ?: unknownTaskLabel}：本次暂不安排，时长仍待补充。" else stringResource(
                                        R.string.plan_unassigned_task,
                                        tasksById[unscheduled.taskId]?.displayName
                                            ?: stringResource(R.string.unknown_task),
                                        unscheduled.remainingMinutes,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(when (unscheduled.reason) {
                                    UnscheduledReason.USER_DEFERRED -> "你选择本次暂不安排；任务不会被删除或标记完成。"
                                    UnscheduledReason.CAPACITY_BEFORE_DUE_DATE -> "截止日期前的可用时间不足。"
                                    else -> "本次规划窗口内的可用时间不足。"
                                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                if (unscheduled.reason != UnscheduledReason.USER_DEFERRED) {
                                    tasksById[unscheduled.taskId]?.let { task ->
                                        TextButton(onClick = { onCompleteTaskDetails(task) }) { Text("调整 ${task.displayName} 的时长或日期后重新生成") }
                                    }
                                }
                            }
                            val capacityMissing = unscheduledTasks.filter { it.reason != UnscheduledReason.USER_DEFERRED }.sumOf { it.remainingMinutes }
                            if (capacityMissing > 0) Text("本次未排入工作量合计 $capacityMissing 分钟；补充时间后需重新检查约束。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = onConfigureAvailability, modifier = Modifier.testTag("draft-capacity-add-time")) { Text("补充可用时间后重新生成") }
                            Text("也可直接确认已排入部分；未排入任务仍保留在待安排列表。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                if (orderedTaskIds.isNotEmpty()) {
                    Surface(
                        onClick = { showTaskOrder = !showTaskOrder },
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("候选事项顺序", style = MaterialTheme.typography.titleSmall)
                                Text(if (draft.orderOnly) "确认后同步任务列表；不移动已安排的时段。" else "只影响下次自动安排，不会改变上面的时段。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(if (showTaskOrder) "收起" else "调整", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (showTaskOrder) {
                        orderedTaskIds.forEachIndexed { index, taskId ->
                            tasksById[taskId]?.let { task ->
                                DraftTaskOrderRow(
                                    task = task,
                                    assessment = priorityAssessmentsByTask[taskId],
                                    index = index,
                                    total = orderedTaskIds.size,
                                    onMove = { offset ->
                                        onUpdateDraft(PlanDraftEditor.moveTask(draft, taskId, offset))
                                    },
                                )
                                if (!draft.orderOnly) {
                                    val deferred = draft.unscheduledTasks.any { it.taskId == taskId && it.reason == UnscheduledReason.USER_DEFERRED }
                                    val protected = draft.segments.any { it.taskId == taskId && (it.isLocked || it.id in protectedIds) }
                                    TextButton(
                                        enabled = !isSaving && !deferred && !protected,
                                        onClick = { onUpdateDraft(PlanDraftReview.defer(draft, taskId, protectedIds)) },
                                        modifier = Modifier.testTag("draft-defer-$taskId"),
                                    ) { Text(if (deferred) "本次已暂不安排" else if (protected) "包含锁定或已开始安排" else "本次暂不安排 ${task.displayName}") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                modifier = Modifier.testTag("accept-plan-draft"),
                enabled = !isSaving && (draft.segments.isNotEmpty() || draft.pendingTaskIds.isNotEmpty() || (draft.orderOnly && orderedTaskIds.isNotEmpty())),
            ) {
                Text(if (draft.orderOnly) "应用排序" else stringResource(R.string.accept_plan_draft))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.discard_plan_draft)) }
        },
    )
    editingSegmentId?.let { segmentId ->
        draft.segments.firstOrNull { it.id == segmentId }?.let { segment ->
            PlanSegmentEditorDialog(
                segment = segment,
                availableTrackIds = (tracks.map { it.id } + listOf("focus", "parallel-2", "parallel-3") + draft.segments.map(PlannedSegment::trackId)).distinct(),
                tracks = tracks,
                hasConflict = { candidate -> PlanDraftEditor.overlapsAnotherSegment(draft, candidate) },
                onDismiss = { editingSegmentId = null },
                onSave = { updated ->
                    onUpdateDraft(PlanDraftEditor.moveSegment(draft, updated))
                    editingSegmentId = null
                },
            )
        }
    }
}

@Composable
private fun PlanDraftTimelinePreview(
    draft: PlanDraft,
    tasks: List<Task>,
    weeklyBlocks: List<WeeklyTimeBlock>,
    dateOverrides: List<DateOverride>,
    semesterFirstWeekMonday: LocalDate?,
    onEditSegment: (String) -> Unit,
    tracks: List<RhythmTrack> = emptyList(),
) {
    val dates = draft.segments.map(PlannedSegment::date).distinct().sorted()
    var selectedDate by remember(dates) { mutableStateOf(dates.firstOrNull()) }
    if (selectedDate !in dates) selectedDate = dates.firstOrNull()
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("plan-draft-preview"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("安排预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("真实时间与轨道；点选蓝色事项可调整。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("尚未写入", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (dates.isEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        "还没有可落位的时段。先保留为待安排，或回到安排助手补充时间。",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    dates.forEach { date ->
                        FilterChip(
                            selected = selectedDate == date,
                            onClick = { selectedDate = date },
                            label = { Text(date.format(DateTimeFormatter.ofPattern("M/d E", Locale.SIMPLIFIED_CHINESE))) },
                            modifier = Modifier.testTag("plan-draft-date-$date"),
                        )
                    }
                }
                val date = requireNotNull(selectedDate)
                val entries = ScheduleTimeline.entries(
                    date = date,
                    weeklyBlocks = weeklyBlocks,
                    dateOverrides = dateOverrides,
                    segments = draft.segments,
                    tasks = tasks,
                    semesterFirstWeekMonday = semesterFirstWeekMonday,
                )
                DraftDayTrackPreview(entries = entries, onEditSegment = onEditSegment, tracks = tracks)
            }
        }
    }
}

@Composable
private fun DraftDayTrackPreview(
    entries: List<TimelineEntry>,
    onEditSegment: (String) -> Unit,
    tracks: List<RhythmTrack> = emptyList(),
) {
    val laneCount = (entries.maxOfOrNull(TimelineEntry::lane) ?: 0) + 1
    val laneTracks = (0 until laneCount).associateWith { lane ->
        entries.firstOrNull { it.lane == lane }?.trackId ?: "focus"
    }
    val rulerWidth = 38.dp
    val laneWidth = 112.dp
    val headerHeight = 28.dp
    val dayStart = 7 * 60
    val dayEnd = 23 * 60
    val hourHeight = 14.dp
    val contentHeight = headerHeight + hourHeight * ((dayEnd - dayStart) / 60)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeight + 18.dp)
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
        ) {
            Box(Modifier.width(rulerWidth + laneWidth * laneCount).height(contentHeight)) {
                laneTracks.forEach { (lane, trackId) ->
                    Surface(
                        modifier = Modifier
                            .offset(x = rulerWidth + laneWidth * lane)
                            .width(laneWidth - 6.dp)
                            .height(22.dp),
                        color = if (lane == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            draftTrackLabel(trackId, tracks),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                (dayStart / 60..dayEnd / 60).forEach { hour ->
                    val y = headerHeight + hourHeight * (hour - dayStart / 60)
                    Text(
                        String.format(Locale.ROOT, "%02d", hour),
                        modifier = Modifier.offset(y = y - 6.dp).width(rulerWidth),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = rulerWidth, y = y)
                            .width(laneWidth * laneCount)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)),
                    )
                }
                entries.forEach { entry ->
                    val start = entry.startMinute.coerceIn(dayStart, dayEnd)
                    val end = entry.endMinute.coerceIn(dayStart, dayEnd)
                    if (end > start) {
                        val isProposed = entry.id.startsWith("segment:")
                        val y = headerHeight + hourHeight * ((start - dayStart) / 60f)
                        val blockHeight = (hourHeight * ((end - start) / 60f)).coerceAtLeast(24.dp)
                        val modifier = Modifier
                            .offset(x = rulerWidth + laneWidth * entry.lane, y = y)
                            .width(laneWidth - 6.dp)
                            .height(blockHeight)
                            .then(
                                if (isProposed) Modifier.testTag("draft-segment-${entry.id.removePrefix("segment:")}") else Modifier,
                            )
                        val content: @Composable () -> Unit = {
                            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(entry.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(
                                    "${TimeBlockValidator.formatTime(entry.startMinute)}–${TimeBlockValidator.formatTime(entry.endMinute)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    color = if (isProposed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (isProposed) {
                            Surface(
                                onClick = { onEditSegment(entry.id.removePrefix("segment:")) },
                                modifier = modifier,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(9.dp),
                            ) { content() }
                        } else {
                            Surface(
                                modifier = modifier,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(9.dp),
                            ) { content() }
                        }
                    }
                }
            }
        }
    }
}

private fun draftTrackLabel(trackId: String, tracks: List<RhythmTrack> = emptyList()): String = tracks.firstOrNull { it.id == trackId }?.name ?: when (trackId) {
    "focus" -> "主线"
    "course" -> "课程"
    "fixed" -> "固定"
    "rest" -> "休息"
    else -> trackId.removePrefix("parallel-").toIntOrNull()?.let { "并行 $it" } ?: trackId
}

@Composable
private fun DraftTaskOrderRow(
    task: Task,
    assessment: LocalPriorityAssessment?,
    index: Int,
    total: Int,
    onMove: (Int) -> Unit,
) {
    var accumulatedDragY by remember(task.id) { mutableFloatStateOf(0f) }
    var showReason by rememberSaveable(task.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(task.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { accumulatedDragY = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        accumulatedDragY += amount.y
                        if (abs(accumulatedDragY) >= 44f) {
                            onMove(if (accumulatedDragY > 0f) 1 else -1)
                            accumulatedDragY = 0f
                        }
                    },
                )
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(task.displayName, style = MaterialTheme.typography.bodyMedium)
                assessment?.let { value ->
                    TextButton(onClick = { showReason = !showReason }) {
                        Text(if (showReason) "收起排序依据" else "排序依据", style = MaterialTheme.typography.labelSmall)
                    }
                    if (showReason) {
                    Text(stringResource(R.string.dynamic_priority_score, value.score), style = MaterialTheme.typography.bodySmall)
                    value.reasons
                        .filter { it.kind != PriorityReasonKind.LOCAL_AI_NEUTRAL }
                        .take(3)
                        .forEach { reason ->
                            Text(
                                text = priorityReasonText(reason),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            TextButton(onClick = { onMove(-1) }, enabled = index > 0) {
                Text(stringResource(R.string.move_up))
            }
            TextButton(onClick = { onMove(1) }, enabled = index < total - 1) {
                Text(stringResource(R.string.move_down))
            }
        }
    }
}

@Composable
private fun PlanSegmentEditorDialog(
    segment: PlannedSegment,
    availableTrackIds: List<String>,
    hasConflict: (PlannedSegment) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (PlannedSegment) -> Unit,
    tracks: List<RhythmTrack> = emptyList(),
) {
    var dateText by remember(segment) { mutableStateOf(segment.date.toString()) }
    var startTime by remember(segment) { mutableStateOf(TimeBlockValidator.formatTime(segment.startMinute)) }
    var endTime by remember(segment) { mutableStateOf(TimeBlockValidator.formatTime(segment.endMinute)) }
    var selectedTrackId by remember(segment) { mutableStateOf(segment.trackId) }
    var showValidationError by remember { mutableStateOf(false) }
    var confirmConflict by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_plan_segment)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.plan_segment_edit_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = {
                        dateText = it
                        confirmConflict = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.override_date)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = startTime,
                    onValueChange = {
                        startTime = it
                        confirmConflict = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.start_time)) },
                    placeholder = { Text(stringResource(R.string.time_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = {
                        endTime = it
                        confirmConflict = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.end_time)) },
                    placeholder = { Text(stringResource(R.string.time_hint)) },
                    singleLine = true,
                )
                Text("轨道", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableTrackIds.forEach { trackId ->
                        FilterChip(
                            selected = selectedTrackId == trackId,
                            onClick = {
                                selectedTrackId = trackId
                                confirmConflict = false
                            },
                            label = { Text(draftTrackLabel(trackId, tracks)) },
                        )
                    }
                }
                if (confirmConflict) {
                    Text(
                        stringResource(R.string.plan_segment_overlap_warning),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (showValidationError) {
                    Text(
                        stringResource(R.string.plan_segment_edit_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedDate = dateText.takeIf(String::isNotBlank)
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    val updated = segment.copy(
                        date = parsedDate ?: segment.date,
                        startMinute = TimeBlockValidator.parseTime(startTime) ?: -1,
                        endMinute = TimeBlockValidator.parseTime(endTime) ?: -1,
                        trackId = selectedTrackId,
                    )
                    if (parsedDate == null || !PlanDraftEditor.isValidSegment(updated)) {
                        showValidationError = true
                    } else if (hasConflict(updated) && !confirmConflict) {
                        confirmConflict = true
                    } else {
                        onSave(updated)
                    }
                },
            ) {
                Text(stringResource(if (confirmConflict) R.string.confirm_anyway else R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PlanOverviewDialog(
    plans: List<ConfirmedPlan>,
    tasks: List<Task>,
    onDismiss: () -> Unit,
    onRestore: (String) -> Unit,
    onClearCurrent: () -> Unit,
    onToggleSegmentLock: (segmentId: String, isLocked: Boolean) -> Unit,
) {
    val tasksById = tasks.associateBy(Task::id)
    EditorSheet(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_overview_title)) },
        text = {
            Column(
                modifier = Modifier
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (plans.isEmpty()) {
                    Text(stringResource(R.string.no_plan_history))
                }
                plans.forEachIndexed { index, plan ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (plan.isCurrent) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (plan.isCurrent) R.string.current_plan_label
                                    else R.string.plan_history_version,
                                    index + 1,
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                stringResource(R.string.plan_segment_count, plan.segments.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            plan.segments.forEach { segment ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = (tasksById[segment.taskId]?.displayName
                                            ?: stringResource(R.string.unknown_task)) + " · " +
                                            segment.date + " " +
                                            TimeBlockValidator.formatTime(segment.startMinute) + "–" +
                                            TimeBlockValidator.formatTime(segment.endMinute),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    if (plan.isCurrent) {
                                        TextButton(
                                            onClick = {
                                                onToggleSegmentLock(segment.id, !segment.isLocked)
                                            },
                                        ) {
                                            Text(
                                                stringResource(
                                                    if (segment.isLocked) R.string.unlock_segment
                                                    else R.string.lock_segment,
                                                ),
                                            )
                                        }
                                    } else if (segment.isLocked) {
                                        Text(stringResource(R.string.segment_locked))
                                    }
                                }
                            }
                            if (!plan.isCurrent) {
                                TextButton(onClick = { onRestore(plan.id) }) {
                                    Text(stringResource(R.string.restore_plan))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (plans.any { it.isCurrent }) {
                TextButton(onClick = onClearCurrent) {
                    Text(
                        stringResource(R.string.clear_current_plan),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun DateOverrideCard(
    dateOverride: DateOverride,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(dateOverride.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(5.dp))
                Text(
                    text = dateOverride.date.toString() + " · " +
                        TimeBlockValidator.formatTime(dateOverride.startMinute) + "–" +
                        TimeBlockValidator.formatTime(dateOverride.endMinute),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(dateOverride.type.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun MineScreen(
    weights: Map<TaskCategory, Int>,
    profileEvidence: List<ProfileEvidence>,
    isLoading: Boolean,
    onSave: (Map<TaskCategory, Int>) -> Unit,
    remindersEnabled: Boolean,
    notificationsAllowed: Boolean,
    onRemindersEnabledChange: (Boolean) -> Unit,
    onGenerateProfileEvidence: () -> Unit,
    onUpdateProfileEvidence: (String, String) -> Unit,
    onDeleteProfileEvidence: (String) -> Unit,
    onExportLocalData: () -> Unit,
    onClearLocalData: () -> Unit,
    isDataWorking: Boolean,
    dataResult: DataManagementResult?,
    hasProfileActionError: Boolean,
    aiEnabled: Boolean,
    aiConsented: Boolean,
    onAiEnabledChange: (Boolean) -> Unit,
    aiProviderConfig: AiProviderConfig,
    remoteAiSupported: Boolean,
    aiProviderConfigError: String?,
    aiProviderConnectionTest: AiProviderConnectionTest,
    onSaveAiProviderConfig: (String, String, String) -> Unit,
    onClearAiProviderKey: () -> Unit,
    onTestAiProviderConnection: () -> Unit,
    engagementMode: EngagementMode,
    onEngagementModeChange: (EngagementMode) -> Unit,
) {
    if (isLoading) return
    var showPreferences by rememberSaveable { mutableStateOf(false) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var showData by rememberSaveable { mutableStateOf(false) }
    var editableWeights by remember(weights) {
        mutableStateOf(TaskCategory.entries.associateWith { weights.getValue(it).toString() })
    }
    var showValidationError by remember { mutableStateOf(false) }
    val parsedWeights = TaskCategory.entries.associateWith { category ->
        editableWeights.getValue(category).toIntOrNull() ?: -1
    }
    val valid = parsedWeights.values.all { it in 0..100 } && parsedWeights.values.sum() > 0
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("版本 ${com.swan1127.repland.BuildConfig.VERSION_NAME} (${com.swan1127.repland.BuildConfig.VERSION_CODE}) · ${com.swan1127.repland.BuildConfig.BUILD_SOURCE}",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("app-version"))
        Text("日常使用", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("统一工作方式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("所有安排始终可见，详细信息按需展开。旧版参与方式记录保留在本地历史中，不再改变页面功能。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.local_reminders_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            stringResource(R.string.local_reminders_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = remindersEnabled,
                        onCheckedChange = onRemindersEnabledChange,
                        modifier = Modifier.testTag("local-reminders-switch"),
                    )
                }
                if (!notificationsAllowed) {
                    Text(
                        stringResource(R.string.notification_permission_needed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Text("智能与个性化", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        AiSettingsSection(
            aiEnabled = aiEnabled,
            aiConsented = aiConsented,
            onAiEnabledChange = onAiEnabledChange,
            providerConfig = aiProviderConfig,
            providerConfigError = aiProviderConfigError,
            providerConnectionTest = aiProviderConnectionTest,
            onSaveProviderConfig = onSaveAiProviderConfig,
            onClearProviderKey = onClearAiProviderKey,
            onTestConnection = onTestAiProviderConnection,
            remoteSupported = remoteAiSupported,
        )
        SettingsDisclosure("类别偏好", "调整不同事情在计划中的权重", showPreferences, { showPreferences = !showPreferences })
        if (showPreferences) {
        Text(stringResource(R.string.category_preferences_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.category_preferences_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TaskCategory.entries.forEach { category ->
            OutlinedTextField(
                value = editableWeights.getValue(category),
                onValueChange = { value ->
                    editableWeights = editableWeights + (category to value.filter(Char::isDigit))
                    showValidationError = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(category.labelRes())) },
                suffix = { Text(stringResource(R.string.percent_suffix)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        }
        if (showValidationError) {
            Text(
                stringResource(R.string.category_preferences_error),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = {
                if (valid) onSave(parsedWeights) else showValidationError = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.save_category_preferences)) }
        }
        SettingsDisclosure("关于你的记录", "${profileEvidence.size} 条画像记录 · 查看、修正与删除", showProfile, { showProfile = !showProfile })
        if (showProfile) {
        ProfileEvidenceSection(
            evidence = profileEvidence,
            onGenerate = onGenerateProfileEvidence,
            onUpdate = onUpdateProfileEvidence,
            onDelete = onDeleteProfileEvidence,
            hasActionError = hasProfileActionError,
        )
        }
        Text("数据与隐私", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        SettingsDisclosure("本地数据", "导出备份与数据管理", showData, { showData = !showData })
        if (showData) {
        LocalDataManagementSection(
            isWorking = isDataWorking,
            result = dataResult,
            onExport = onExportLocalData,
            onClear = onClearLocalData,
        )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsDisclosure(title: String, description: String, expanded: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Text(if (expanded) "收起" else "展开", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AiSettingsSection(
    aiEnabled: Boolean,
    aiConsented: Boolean,
    onAiEnabledChange: (Boolean) -> Unit,
    providerConfig: AiProviderConfig,
    providerConfigError: String?,
    providerConnectionTest: AiProviderConnectionTest,
    onSaveProviderConfig: (String, String, String) -> Unit,
    onClearProviderKey: () -> Unit,
    onTestConnection: () -> Unit,
    remoteSupported: Boolean,
) {
    var showProviderSettings by rememberSaveable { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!remoteSupported) Text("当前构建使用本地规划，尚不支持远程 AI；保存密钥不会启用联网能力。",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ai-build-capability"))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.ai_advisor_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.ai_advisor_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Switch(
                    checked = aiEnabled,
                    onCheckedChange = onAiEnabledChange,
                    modifier = Modifier.testTag("ai-advisor-switch"),
                )
            }
            Text(
                stringResource(
                    if (aiConsented) R.string.ai_advisor_consent_recorded
                    else R.string.ai_advisor_consent_needed,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Surface(
                onClick = { showProviderSettings = true },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("ai-provider-settings"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = if (providerConfig.hasApiKey) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            PlannerIcons.Send,
                            contentDescription = null,
                            modifier = Modifier.padding(9.dp),
                            tint = if (providerConfig.hasApiKey) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("模型服务", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (providerConfig.hasApiKey) "密钥已加密保存在本设备 · ${providerConfig.model}"
                            else "尚未添加密钥 · 本地结构化解析仍可使用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (providerConfigError != null) {
                Text(providerConfigError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(
                "保存密钥本身不会联网。只有你在助手或任务建议中确认请求后，才会发送本次所需的最小上下文。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
    if (showProviderSettings) {
        AiProviderSettingsDialog(
            config = providerConfig,
            errorMessage = providerConfigError,
            connectionTest = providerConnectionTest,
            onDismiss = { showProviderSettings = false },
            onSave = onSaveProviderConfig,
            onClearKey = onClearProviderKey,
            onTestConnection = onTestConnection,
        )
    }
}

@Composable
internal fun AiProviderSettingsDialog(
    config: AiProviderConfig,
    errorMessage: String?,
    connectionTest: AiProviderConnectionTest,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onClearKey: () -> Unit,
    onTestConnection: () -> Unit,
) {
    var baseUrl by remember(config.baseUrl) { mutableStateOf(config.baseUrl) }
    var model by remember(config.model) { mutableStateOf(config.model) }
    var apiKey by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模型服务配置") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "用于需要更深层语义理解的可选建议。安排写入仍然必须经过轨道草案与手动确认。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("地址预设（切换服务需重新输入对应密钥）", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = baseUrl.contains("apihub.agnes-ai.com"),
                        onClick = { baseUrl = "https://apihub.agnes-ai.com/v1" },
                        label = { Text("国际服务") },
                    )
                    FilterChip(
                        selected = baseUrl.contains("api.agnes-ai.cn"),
                        onClick = { baseUrl = "https://api.agnes-ai.cn/v1" },
                        label = { Text("中国服务") },
                    )
                }
                FilterChip(
                    selected = baseUrl.trimEnd('/') == "https://api.deepseek.com",
                    onClick = { baseUrl = "https://api.deepseek.com"; model = "deepseek-flash"; apiKey = "" },
                    label = { Text("DeepSeek 官方") },
                    modifier = Modifier.testTag("ai-provider-deepseek"),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth().testTag("ai-provider-endpoint"),
                    label = { Text("服务地址") },
                    supportingText = { Text("填写 HTTPS API 基础地址，不是聊天网站地址；请以服务商文档为准。") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth().testTag("ai-provider-model"),
                    label = { Text("模型") },
                    supportingText = { Text("填写服务商 API 模型 ID，不一定等于产品展示名称。") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth().testTag("ai-provider-key"),
                    label = { Text(if (config.hasApiKey) "替换 API 密钥（留空则保留）" else "API 密钥") },
                    placeholder = { Text(if (config.hasApiKey) "已安全保存" else "粘贴你的密钥") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                if (errorMessage != null) Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                when (connectionTest) {
                    AiProviderConnectionTest.Idle -> Text("保存密钥后可发送一条不含个人数据的最小请求，验证地址、模型与鉴权。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AiProviderConnectionTest.Testing -> Text("正在验证连接…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    is AiProviderConnectionTest.Connected -> Text("连接成功：${connectionTest.model} 已可用于安排助手。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    is AiProviderConnectionTest.Failed -> Text(connectionTest.reason.connectionTestMessage(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (config.hasApiKey) {
                    TextButton(onClick = { onClearKey(); apiKey = "" }, modifier = Modifier.testTag("ai-provider-clear")) {
                        Text("清除本机密钥", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Column {
                TextButton(onClick = { onSave(baseUrl, model, apiKey) }, modifier = Modifier.testTag("ai-provider-save")) { Text("加密保存") }
                TextButton(
                    onClick = onTestConnection,
                    enabled = config.hasApiKey && connectionTest !is AiProviderConnectionTest.Testing,
                    modifier = Modifier.testTag("ai-provider-test"),
                ) { Text("测试连接") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

private fun AiAdvisorFailureReason.connectionTestMessage(): String = when (this) {
    AiAdvisorFailureReason.SOURCE_CHANGED -> "规划数据已变化，请重试。"
    AiAdvisorFailureReason.CONTEXT_UNAVAILABLE -> "暂时无法读取规划数据，请重试。"
    AiAdvisorFailureReason.CONFIGURATION_CHANGED -> "配置已变化，请使用新配置重新测试。"
    AiAdvisorFailureReason.AUTHENTICATION_FAILURE -> "鉴权失败：请检查密钥是否属于当前服务区，以及账户是否可用。"
    AiAdvisorFailureReason.RATE_LIMITED -> "服务已连通，但当前触发请求额度或频率限制。"
    AiAdvisorFailureReason.REMOTE_FAILURE -> "已到达服务网关，但模型服务暂时异常；请稍后再试。"
    AiAdvisorFailureReason.TIMEOUT -> "连接超时：请检查网络，或稍后重试。"
    AiAdvisorFailureReason.INVALID_RESPONSE -> "服务已返回内容，但格式未通过校验；请重试。"
    AiAdvisorFailureReason.TRANSPORT_FAILURE -> "无法到达服务：请确认服务区地址和网络。"
    AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED -> "尚未保存可用密钥。"
    AiAdvisorFailureReason.DISABLED -> "连接测试不依赖规划开关；请重新保存密钥后测试。"
}

@Composable
private fun ProfileEvidenceSection(
    evidence: List<ProfileEvidence>,
    onGenerate: () -> Unit,
    onUpdate: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    hasActionError: Boolean,
) {
    var editingEvidence by remember { mutableStateOf<ProfileEvidence?>(null) }
    var deletingEvidence by remember { mutableStateOf<ProfileEvidence?>(null) }
    Text(stringResource(R.string.profile_evidence_title), style = MaterialTheme.typography.titleLarge)
    Text(
        stringResource(R.string.profile_evidence_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.generate_profile_evidence))
    }
    if (hasActionError) {
        Text(
            stringResource(R.string.profile_evidence_action_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (evidence.isEmpty()) {
        Text(
            stringResource(R.string.no_profile_evidence),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        evidence.forEach { item ->
            ProfileEvidenceCard(
                evidence = item,
                onEdit = { editingEvidence = item },
                onDelete = { deletingEvidence = item },
            )
        }
    }

    editingEvidence?.let { item ->
        ProfileEvidenceEditorDialog(
            evidence = item,
            onDismiss = { editingEvidence = null },
            onSave = { conclusion ->
                onUpdate(item.id, conclusion)
                editingEvidence = null
            },
        )
    }
    deletingEvidence?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingEvidence = null },
            title = { Text(stringResource(R.string.delete_profile_evidence_title)) },
            text = { Text(stringResource(R.string.delete_profile_evidence_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(item.id)
                        deletingEvidence = null
                    },
                ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingEvidence = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ProfileEvidenceCard(
    evidence: ProfileEvidence,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(evidence.scope.labelRes()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(evidence.conclusion, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (evidence.isUserEdited) {
                    stringResource(R.string.profile_evidence_user_edited)
                } else {
                    stringResource(R.string.profile_evidence_generated)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.profile_evidence_sources_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            if (evidence.sources.isEmpty()) {
                Text(
                    stringResource(R.string.profile_evidence_source_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                evidence.sources.forEach { source ->
                    Text(
                        text = stringResource(
                            R.string.profile_evidence_source_format,
                            source.taskDisplayName,
                            stringResource(source.eventType.labelRes()),
                            formatExecutionLogTime(source.createdAtEpochMillis),
                            source.executionLogId.take(8),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.edit_profile_evidence)) }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ProfileEvidenceEditorDialog(
    evidence: ProfileEvidence,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var conclusion by remember(evidence.id) { mutableStateOf(evidence.conclusion) }
    val valid = conclusion.trim().length in 1..500
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_profile_evidence)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.edit_profile_evidence_notice),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = conclusion,
                    onValueChange = { conclusion = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.profile_evidence_conclusion)) },
                    minLines = 3,
                )
                if (!valid) {
                    Text(
                        stringResource(R.string.profile_evidence_validation_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onSave(conclusion) }, enabled = valid) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun LocalDataManagementSection(
    isWorking: Boolean,
    result: DataManagementResult?,
    onExport: () -> Unit,
    onClear: () -> Unit,
) {
    Text(stringResource(R.string.local_data_title), style = MaterialTheme.typography.titleLarge)
    Text(
        stringResource(R.string.local_data_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = onExport,
        enabled = !isWorking,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.export_local_json)) }
    OutlinedButton(
        onClick = onClear,
        enabled = !isWorking,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.clear_local_data), color = MaterialTheme.colorScheme.error) }
    result?.let { outcome ->
        val (message, isError) = when (outcome) {
            DataManagementResult.EXPORT_SUCCEEDED -> R.string.export_local_data_success to false
            DataManagementResult.EXPORT_FAILED -> R.string.export_local_data_failed to true
            DataManagementResult.CLEAR_SUCCEEDED -> R.string.clear_local_data_success to false
            DataManagementResult.CLEAR_FAILED -> R.string.clear_local_data_failed to true
        }
        Text(
            stringResource(message),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun InfoScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmptyState(
    title: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            actionLabel?.let { label ->
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAction) { Text(label) }
            }
        }
    }
}

@Composable
private fun TaskEditorDialog(
    task: Task?,
    initialText: String = "",
    @StringRes dialogTitle: Int? = null,
    @StringRes confirmLabel: Int = R.string.save,
    allowPriorityChange: Boolean = task == null,
    onDismiss: () -> Unit,
    onSave: (TaskDraft) -> Unit,
) {
    var displayName by remember(task, initialText) {
        mutableStateOf(task?.displayName ?: TaskName.fromDescription(initialText))
    }
    var description by remember(task, initialText) {
        mutableStateOf(task?.description.takeIf { it != task?.displayName } ?: initialText)
    }
    var category by remember(task) { mutableStateOf(task?.category ?: TaskCategory.COURSE) }
    var priority by remember(task) { mutableStateOf(task?.userPriority ?: TaskPriority.MEDIUM) }
    var estimatedDaysText by remember(task) { mutableStateOf(task?.estimatedDays?.toString() ?: "1") }
    var durationText by remember(task) { mutableStateOf(task?.totalDurationMinutes?.toString().orEmpty()) }
    var scheduledForDate by remember(task) { mutableStateOf(task?.scheduledForDate) }
    var dueDate by remember(task) { mutableStateOf(task?.dueDate) }
    var datePickerTarget by remember { mutableStateOf<TaskEditorDateTarget?>(null) }
    var showValidationError by remember { mutableStateOf(false) }
    val estimatedDays = estimatedDaysText.toIntOrNull()
    val durationMinutes = durationText.takeIf(String::isNotBlank)?.toIntOrNull()
    val estimatedDaysInvalid = estimatedDays !in 1..30
    val durationInvalid = durationText.isNotBlank() && durationMinutes !in 1..1_440
    val scheduleAfterDeadline = scheduledForDate?.let { scheduled ->
        dueDate?.let { deadline -> scheduled.isAfter(deadline) }
    } == true

    EditorSheet(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(dialogTitle ?: if (task == null) R.string.add_task else R.string.edit_task))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth().testTag("task-editor-name"),
                    label = { Text(stringResource(R.string.task_name)) },
                    placeholder = { Text(stringResource(R.string.task_name_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth().testTag("task-editor-description"),
                    label = { Text("备注 / 说明（可选）") },
                    placeholder = { Text("地点、准备物、下一步或这次安排的上下文") },
                    minLines = 2,
                )
                Text(stringResource(R.string.category), style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = TaskCategory.entries.toList(),
                    selected = category,
                    label = { stringResource(it.labelRes()) },
                    onSelected = { category = it },
                )
                Text(stringResource(R.string.priority), style = MaterialTheme.typography.labelLarge)
                if (allowPriorityChange) {
                    ChoiceRow(
                        values = TaskPriority.entries.toList(),
                        selected = priority,
                        label = { stringResource(it.labelRes()) },
                        onSelected = { priority = it },
                    )
                } else {
                    Text(
                        text = stringResource(R.string.initial_priority_fixed, stringResource(priority.labelRes())),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = estimatedDaysText,
                    onValueChange = { estimatedDaysText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.estimated_days)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = showValidationError && estimatedDaysInvalid,
                    supportingText = {
                        if (showValidationError && estimatedDaysInvalid) Text("请输入 1–30 天")
                    },
                )
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth().testTag("task-editor-duration"),
                    label = { Text(stringResource(R.string.duration_minutes_optional)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = showValidationError && durationInvalid,
                    supportingText = {
                        if (showValidationError && durationInvalid) Text("时长应为 1–1440 分钟；留空表示暂不估算")
                    },
                )
                TaskDateField(
                    label = "安排在哪一天？",
                    description = "只决定它在“今天 / 待安排”中的位置；不会被当作截止时间。",
                    selectedDate = scheduledForDate,
                    onSelectDate = { scheduledForDate = it },
                    onChooseDate = { datePickerTarget = TaskEditorDateTarget.SCHEDULED_FOR },
                    onClear = { scheduledForDate = null },
                    tag = "task-editor-scheduled-date",
                )
                TaskDateField(
                    label = stringResource(R.string.due_date_optional),
                    description = "用于截止提醒、优先级和逾期判断；可自由选择任意日期。",
                    selectedDate = dueDate,
                    onSelectDate = { dueDate = it },
                    onChooseDate = { datePickerTarget = TaskEditorDateTarget.DEADLINE },
                    onClear = { dueDate = null },
                    tag = "task-editor-due-date",
                )
                if (scheduleAfterDeadline) {
                    Text(
                        "计划日期晚于截止日期，保存后任务会按逾期处理。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (showValidationError) {
                    Text(
                        stringResource(R.string.save_task_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val draft = TaskDraft(
                        id = task?.id,
                        displayName = displayName,
                        description = description,
                        category = category,
                        userPriority = priority,
                        estimatedDays = estimatedDays ?: 0,
                        totalDurationMinutes = durationMinutes,
                        dueDate = dueDate,
                        scheduledForDate = scheduledForDate,
                    )
                    if (TaskDraftValidator.isValid(draft)) onSave(draft) else showValidationError = true
                },
                modifier = Modifier.testTag("task-editor-save"),
            ) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
    datePickerTarget?.let { target ->
        TaskDatePickerDialog(
            initialDate = when (target) {
                TaskEditorDateTarget.SCHEDULED_FOR -> scheduledForDate
                TaskEditorDateTarget.DEADLINE -> dueDate
            },
            onDismiss = { datePickerTarget = null },
            onDateSelected = { selected ->
                when (target) {
                    TaskEditorDateTarget.SCHEDULED_FOR -> scheduledForDate = selected
                    TaskEditorDateTarget.DEADLINE -> dueDate = selected
                }
                datePickerTarget = null
            },
        )
    }
}

private enum class TaskEditorDateTarget { SCHEDULED_FOR, DEADLINE }

@Composable
private fun TaskDateField(
    label: String,
    description: String,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    onChooseDate: () -> Unit,
    onClear: () -> Unit,
    tag: String,
) {
    val today = LocalDate.now()
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                selectedDate?.let {
                    TextButton(onClick = onClear, modifier = Modifier.testTag("$tag-clear")) { Text("清除") }
                }
            }
            Text(
                selectedDate?.let(::taskDateLabel) ?: "未设置",
                style = MaterialTheme.typography.bodyLarge,
                color = if (selectedDate == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedDate == today,
                    onClick = { onSelectDate(today) },
                    label = { Text("今天") },
                )
                FilterChip(
                    selected = selectedDate == today.plusDays(1),
                    onClick = { onSelectDate(today.plusDays(1)) },
                    label = { Text("明天") },
                )
                OutlinedButton(
                    onClick = onChooseDate,
                    modifier = Modifier.testTag("$tag-picker"),
                ) {
                    Icon(Icons.Outlined.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("选择日期")
                }
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    values: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelected(value) },
                label = { Text(label(value)) },
            )
        }
    }
}

@Composable
private fun WeeklyTimeBlockEditorDialog(
    block: WeeklyTimeBlock?,
    onDismiss: () -> Unit,
    onSave: (WeeklyTimeBlockDraft) -> Unit,
    initialKind: TimeBlockKind = TimeBlockKind.COURSE,
) {
    var title by remember(block) { mutableStateOf(block?.title.orEmpty()) }
    var kind by remember(block) { mutableStateOf(block?.kind ?: initialKind) }
    var dayOfWeek by remember(block) { mutableStateOf(block?.dayOfWeek ?: if (initialKind == TimeBlockKind.AVAILABLE) LocalDate.now().dayOfWeek else DayOfWeek.MONDAY) }
    var startTime by remember(block) {
        mutableStateOf(block?.startMinute?.let(TimeBlockValidator::formatTime) ?: "09:00")
    }
    var endTime by remember(block) {
        mutableStateOf(block?.endMinute?.let(TimeBlockValidator::formatTime) ?: "10:00")
    }
    var note by remember(block) { mutableStateOf(block?.note.orEmpty()) }
    var showValidationError by remember { mutableStateOf(false) }

    EditorSheet(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (block == null) R.string.add_weekly_block else R.string.edit_weekly_block))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.time_block_name)) },
                    placeholder = { Text(stringResource(R.string.time_block_name_hint)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.block_kind), style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = TimeBlockKind.entries.toList(),
                    selected = kind,
                    label = { stringResource(it.labelRes()) },
                    onSelected = { kind = it },
                )
                Text(stringResource(R.string.day_of_week), style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = DayOfWeek.entries.toList(),
                    selected = dayOfWeek,
                    label = { stringResource(it.labelRes()) },
                    onSelected = { dayOfWeek = it },
                )
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.start_time)) },
                    placeholder = { Text(stringResource(R.string.time_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.end_time)) },
                    placeholder = { Text(stringResource(R.string.time_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth().testTag("weekly-block-note"),
                    label = { Text("备注（可选）") },
                    placeholder = { Text("地点、准备物、老师或上课提醒") },
                    minLines = 2,
                )
                if (showValidationError) {
                    Text(
                        stringResource(R.string.time_block_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val draft = WeeklyTimeBlockDraft(
                        id = block?.id,
                        title = title,
                        kind = kind,
                        dayOfWeek = dayOfWeek,
                        startMinute = TimeBlockValidator.parseTime(startTime) ?: -1,
                        endMinute = TimeBlockValidator.parseTime(endTime) ?: -1,
                        weekPattern = block?.weekPattern,
                        trackId = block?.trackId ?: "course",
                        note = note,
                    )
                    if (TimeBlockValidator.isValid(draft)) onSave(draft) else showValidationError = true
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DateOverrideEditorDialog(
    dateOverride: DateOverride?,
    onDismiss: () -> Unit,
    onSave: (DateOverrideDraft) -> Unit,
) {
    var title by remember(dateOverride) { mutableStateOf(dateOverride?.title.orEmpty()) }
    var type by remember(dateOverride) {
        mutableStateOf(dateOverride?.type ?: DateOverrideType.BLOCKED)
    }
    var dateText by remember(dateOverride) {
        mutableStateOf(dateOverride?.date?.toString() ?: LocalDate.now().toString())
    }
    var startTime by remember(dateOverride) {
        mutableStateOf(dateOverride?.startMinute?.let(TimeBlockValidator::formatTime) ?: "09:00")
    }
    var endTime by remember(dateOverride) {
        mutableStateOf(dateOverride?.endMinute?.let(TimeBlockValidator::formatTime) ?: "10:00")
    }
    var note by remember(dateOverride) { mutableStateOf(dateOverride?.note.orEmpty()) }
    var showValidationError by remember { mutableStateOf(false) }

    EditorSheet(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (dateOverride == null) R.string.add_date_override else R.string.edit_date_override))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.time_block_name)) },
                    placeholder = { Text(stringResource(R.string.time_block_name_hint)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.override_type), style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = DateOverrideType.entries.toList(),
                    selected = type,
                    label = { stringResource(it.labelRes()) },
                    onSelected = { type = it },
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.override_date)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.start_time)) },
                    placeholder = { Text(stringResource(R.string.time_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.end_time)) },
                    placeholder = { Text(stringResource(R.string.time_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth().testTag("date-override-note"),
                    label = { Text("备注（可选）") },
                    placeholder = { Text("地点、准备物或本次调整的原因") },
                    minLines = 2,
                )
                if (showValidationError) {
                    Text(
                        stringResource(R.string.time_block_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
                    val startMinute = TimeBlockValidator.parseTime(startTime)
                    val endMinute = TimeBlockValidator.parseTime(endTime)
                    val draft = date?.let {
                        DateOverrideDraft(
                            id = dateOverride?.id,
                            title = title,
                            type = type,
                            date = it,
                            startMinute = startMinute ?: -1,
                            endMinute = endMinute ?: -1,
                            note = note,
                        )
                    }
                    if (draft != null && TimeBlockValidator.isValid(draft)) onSave(draft)
                    else showValidationError = true
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DeleteTimeEntryDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_time_entry)) },
        text = { Text(stringResource(R.string.delete_time_entry_message, title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun CompleteTaskDialog(
    task: Task,
    onDismiss: () -> Unit,
    onConfirm: (
        completedContent: String,
        completionResult: String?,
        actualDurationMinutes: Int?,
        progressPercent: Int?,
    ) -> Unit,
) {
    var completedContent by remember(task) { mutableStateOf("") }
    var completionResult by remember(task) { mutableStateOf("") }
    var actualDurationText by remember(task) { mutableStateOf("") }
    var progressText by remember(task) { mutableStateOf("") }
    var showValidationError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.complete_task)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = completedContent,
                    onValueChange = { completedContent = it },
                    modifier = Modifier.fillMaxWidth().testTag("feedback-content"),
                    label = { Text(stringResource(R.string.completed_content_required)) },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = completionResult,
                    onValueChange = { completionResult = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.completion_result_optional)) },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = actualDurationText,
                    onValueChange = { actualDurationText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth().testTag("feedback-actual-duration"),
                    label = { Text(stringResource(R.string.actual_duration_optional)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = progressText,
                    onValueChange = { progressText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth().testTag("feedback-progress"),
                    label = { Text(stringResource(R.string.progress_optional)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                if (showValidationError) {
                    Text(
                        stringResource(R.string.save_task_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val actualMinutes = actualDurationText.takeIf(String::isNotBlank)?.toIntOrNull()
                    val progress = progressText.takeIf(String::isNotBlank)?.toIntOrNull()
                    val isValid = completedContent.isNotBlank() &&
                        (actualMinutes == null || actualMinutes in 1..1_440) &&
                        (progress == null || progress == 100)
                    if (isValid) onConfirm(completedContent, completionResult, actualMinutes, progress)
                    else showValidationError = true
                },
            ) { Text(stringResource(R.string.complete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun PartialCompletionDialog(
    task: Task,
    onDismiss: () -> Unit,
    onConfirm: (TaskFeedback) -> Unit,
) = FeedbackDialog(
    title = stringResource(R.string.partial_completion),
    task = task,
    requireContent = true,
    requirePartialProgress = true,
    onDismiss = onDismiss,
    onConfirm = onConfirm,
)

@Composable
private fun ExecutionFeedbackDialog(
    task: Task,
    onDismiss: () -> Unit,
    onConfirm: (TaskFeedback) -> Unit,
) = FeedbackDialog(
    title = stringResource(R.string.record_feedback),
    task = task,
    requireContent = false,
    requirePartialProgress = false,
    notice = stringResource(R.string.feedback_does_not_change_status),
    onDismiss = onDismiss,
    onConfirm = onConfirm,
)

@Composable
private fun CorrectExecutionLogDialog(
    log: TaskExecutionLog,
    onDismiss: () -> Unit,
    onConfirm: (TaskFeedback) -> Unit,
) = FeedbackDialog(
    title = stringResource(R.string.correct_log),
    task = null,
    initialFeedback = log.feedback,
    requireContent = false,
    requirePartialProgress = false,
    showPostponeReason = true,
    notice = stringResource(R.string.correction_notice),
    onDismiss = onDismiss,
    onConfirm = onConfirm,
)

@Composable
private fun FeedbackDialog(
    title: String,
    task: Task?,
    initialFeedback: TaskFeedback = TaskFeedback(),
    requireContent: Boolean,
    requirePartialProgress: Boolean,
    showPostponeReason: Boolean = false,
    notice: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (TaskFeedback) -> Unit,
) {
    var completedContent by remember(task, initialFeedback) {
        mutableStateOf(initialFeedback.completedContent.orEmpty())
    }
    var completionResult by remember(task, initialFeedback) {
        mutableStateOf(initialFeedback.completionResult.orEmpty())
    }
    var actualDurationText by remember(task, initialFeedback) {
        mutableStateOf(initialFeedback.actualDurationMinutes?.toString().orEmpty())
    }
    var progressText by remember(task, initialFeedback) {
        mutableStateOf(initialFeedback.progressPercent?.toString().orEmpty())
    }
    var postponeReason by remember(task, initialFeedback) {
        mutableStateOf(initialFeedback.postponeReason.orEmpty())
    }
    var showValidationError by remember { mutableStateOf(false) }
    EditorSheet(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                notice?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = completedContent,
                    onValueChange = { completedContent = it },
                    modifier = Modifier.fillMaxWidth().testTag("feedback-content"),
                    label = {
                        Text(stringResource(if (requireContent) R.string.completed_content_required else R.string.completed_content_optional))
                    },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = completionResult,
                    onValueChange = { completionResult = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.completion_result_optional)) },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = actualDurationText,
                    onValueChange = { actualDurationText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth().testTag("feedback-actual-duration"),
                    label = { Text(stringResource(R.string.actual_duration_optional)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = progressText,
                    onValueChange = { progressText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth().testTag("feedback-progress"),
                    label = {
                        Text(stringResource(if (requirePartialProgress) R.string.partial_progress_required else R.string.progress_optional))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                if (showPostponeReason) {
                    OutlinedTextField(
                        value = postponeReason,
                        onValueChange = { postponeReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.postpone_reason_optional)) },
                        minLines = 2,
                    )
                }
                if (showValidationError) {
                    Text(stringResource(R.string.feedback_validation_error), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val actual = actualDurationText.takeIf(String::isNotBlank)?.toIntOrNull()
                    val progress = progressText.takeIf(String::isNotBlank)?.toIntOrNull()
                    val feedback = TaskFeedback(
                        completedContent = completedContent,
                        completionResult = completionResult,
                        actualDurationMinutes = actual,
                        progressPercent = progress,
                        postponeReason = postponeReason,
                    )
                    val hasFeedback = actual != null || progress != null || completedContent.isNotBlank() ||
                        completionResult.isNotBlank() || postponeReason.isNotBlank()
                    val validProgress = if (requirePartialProgress) progress in 1..99 else progress == null || progress in 0..100
                    if (hasFeedback && (actual == null || actual in 1..1_440) && validProgress &&
                        (!requireContent || completedContent.isNotBlank())
                    ) onConfirm(feedback) else showValidationError = true
                },
                modifier = Modifier.testTag("feedback-confirm"),
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PostponeTaskDialog(onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.postpone)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.postpone_notice), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.postpone_reason_optional)) },
                    minLines = 2,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason) }) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ConfirmTaskStatusDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun Task.isTodayRelevant(): Boolean = status.isActive

private fun android.content.Context.canPostLocalNotifications(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED && NotificationManagerCompat.from(this).areNotificationsEnabled()

/** A completed time window is informational until the user records task feedback. */
private fun PlannedSegment.hasEndedBefore(now: LocalDateTime): Boolean =
    !date.atStartOfDay().plusMinutes(endMinute.toLong()).isAfter(now)

@StringRes
private fun TaskStatus.labelRes(): Int = when (this) {
    TaskStatus.NOT_STARTED -> R.string.status_not_started
    TaskStatus.IN_PROGRESS -> R.string.status_in_progress
    TaskStatus.COMPLETED -> R.string.status_completed
    TaskStatus.POSTPONED -> R.string.status_postponed
    TaskStatus.CANCELLED -> R.string.status_cancelled
    TaskStatus.REPLACED -> R.string.status_replaced
}

@StringRes
private fun ExecutionLogEventType.labelRes(): Int = when (this) {
    ExecutionLogEventType.STATUS_CHANGE -> R.string.log_event_status_change
    ExecutionLogEventType.FEEDBACK -> R.string.log_event_feedback
    ExecutionLogEventType.PARTIAL_COMPLETION -> R.string.log_event_partial_completion
    ExecutionLogEventType.CORRECTION -> R.string.log_event_correction
    ExecutionLogEventType.REPLACEMENT -> R.string.log_event_replacement
}

private fun ProfileEvidenceScope.labelRes(): Int = when (this) {
    ProfileEvidenceScope.GENERAL -> R.string.profile_evidence_scope_general
    ProfileEvidenceScope.LEARNING -> R.string.profile_evidence_scope_learning
}

@StringRes
private fun AiAdvisorFailureReason.labelRes(): Int = when (this) {
    AiAdvisorFailureReason.SOURCE_CHANGED -> R.string.ai_failure_source_changed
    AiAdvisorFailureReason.CONTEXT_UNAVAILABLE -> R.string.ai_failure_context_unavailable
    AiAdvisorFailureReason.CONFIGURATION_CHANGED -> R.string.ai_failure_configuration_changed
    AiAdvisorFailureReason.DISABLED -> R.string.ai_failure_disabled
    AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED -> R.string.ai_failure_not_configured
    AiAdvisorFailureReason.TRANSPORT_FAILURE -> R.string.ai_failure_transport
    AiAdvisorFailureReason.TIMEOUT -> R.string.ai_failure_timeout
    AiAdvisorFailureReason.AUTHENTICATION_FAILURE -> R.string.ai_failure_authentication
    AiAdvisorFailureReason.RATE_LIMITED -> R.string.ai_failure_rate_limited
    AiAdvisorFailureReason.REMOTE_FAILURE -> R.string.ai_failure_remote
    AiAdvisorFailureReason.INVALID_RESPONSE -> R.string.ai_failure_invalid_response
}

private fun formatExecutionLogTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

@Composable
private fun priorityReasonText(reason: PriorityReason): String = when (reason.kind) {
    PriorityReasonKind.INITIAL_PRIORITY -> stringResource(
        R.string.priority_reason_initial,
        reason.value ?: 0,
    )

    PriorityReasonKind.CATEGORY_PREFERENCE -> stringResource(
        R.string.priority_reason_category,
        reason.value ?: 0,
    )

    PriorityReasonKind.OVERDUE -> stringResource(R.string.priority_reason_overdue, reason.value ?: 0)
    PriorityReasonKind.DEADLINE -> when (reason.value ?: 0) {
        0 -> stringResource(R.string.priority_reason_due_today)
        else -> stringResource(R.string.priority_reason_due_in_days, reason.value ?: 0)
    }

    PriorityReasonKind.POSTPONEMENTS -> stringResource(R.string.priority_reason_postponed, reason.value ?: 0)
    PriorityReasonKind.PLANNED_DATE -> "计划日期距今天 ${reason.value ?: 0} 天"
    PriorityReasonKind.LOCAL_AI_NEUTRAL -> "本地排序：AI 修正为 0"
}

@StringRes
private fun UnscheduledReason.labelRes(): Int = when (this) {
    UnscheduledReason.USER_DEFERRED -> R.string.plan_user_deferred_reason
    UnscheduledReason.TOTAL_CAPACITY_IN_ROLLING_WINDOW -> R.string.capacity_total_window_reason
    UnscheduledReason.CAPACITY_BEFORE_DUE_DATE -> R.string.capacity_before_due_reason
    UnscheduledReason.CAPACITY_IN_ROLLING_WINDOW -> R.string.capacity_rolling_window_reason
}

@StringRes
private fun TaskPriority.labelRes(): Int = when (this) {
    TaskPriority.REQUIRED -> R.string.priority_required
    TaskPriority.HIGH -> R.string.priority_high
    TaskPriority.MEDIUM -> R.string.priority_medium
    TaskPriority.LOW -> R.string.priority_low
}

@StringRes
private fun TaskCategory.labelRes(): Int = when (this) {
    TaskCategory.COURSE -> R.string.category_course
    TaskCategory.EXTRACURRICULAR -> R.string.category_extracurricular
    TaskCategory.OFFICE -> R.string.category_office
    TaskCategory.LEISURE -> R.string.category_leisure
}

@StringRes
private fun TimeBlockKind.labelRes(): Int = when (this) {
    TimeBlockKind.AVAILABLE -> R.string.block_kind_available
    TimeBlockKind.COURSE -> R.string.block_kind_course
    TimeBlockKind.REST -> R.string.block_kind_rest
    TimeBlockKind.OTHER -> R.string.block_kind_other
}

@StringRes
private fun DateOverrideType.labelRes(): Int = when (this) {
    DateOverrideType.BLOCKED -> R.string.override_blocked
    DateOverrideType.AVAILABLE -> R.string.override_available
}

@StringRes
private fun DayOfWeek.labelRes(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.monday
    DayOfWeek.TUESDAY -> R.string.tuesday
    DayOfWeek.WEDNESDAY -> R.string.wednesday
    DayOfWeek.THURSDAY -> R.string.thursday
    DayOfWeek.FRIDAY -> R.string.friday
    DayOfWeek.SATURDAY -> R.string.saturday
    DayOfWeek.SUNDAY -> R.string.sunday
}
