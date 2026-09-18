package com.swan1127.repland.ui

import android.Manifest
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.swan1127.repland.R
import com.swan1127.repland.domain.model.ClassPeriodClock
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
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
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.TimeBlockValidator
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import com.swan1127.repland.domain.model.UnscheduledReason
import com.swan1127.repland.ui.time.TimeViewModel
import com.swan1127.repland.ui.time.TimetableImportState
import com.swan1127.repland.ui.plan.PlanViewModel
import com.swan1127.repland.ui.preferences.CategoryPreferenceViewModel
import com.swan1127.repland.ui.reminders.ReminderSettingsViewModel
import com.swan1127.repland.ui.profile.ProfileEvidenceViewModel
import com.swan1127.repland.ui.data.DataManagementResult
import com.swan1127.repland.ui.data.DataManagementViewModel
import com.swan1127.repland.ui.ai.PlanningAgentViewModel
import com.swan1127.repland.ui.tasks.TaskViewModel
import com.swan1127.repland.reminders.LocalReminderScheduler
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.flow.flowOf

private enum class AppTab(
    @param:StringRes val titleRes: Int,
    @param:StringRes val shortRes: Int,
) {
    TODAY(R.string.today_title, R.string.tab_today),
    TASKS(R.string.tasks_title, R.string.tab_tasks),
    TIME(R.string.time_title, R.string.tab_time),
    MINE(R.string.mine_title, R.string.tab_mine),
}

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
) {
    val uiState by taskViewModel.uiState.collectAsStateWithLifecycle()
    val timeUiState by timeViewModel.uiState.collectAsStateWithLifecycle()
    val planUiState by planViewModel.uiState.collectAsStateWithLifecycle()
    val categoryPreferenceUiState by categoryPreferenceViewModel.uiState.collectAsStateWithLifecycle()
    val reminderSettingsUiState by reminderSettingsViewModel.uiState.collectAsStateWithLifecycle()
    val profileEvidenceUiState by profileEvidenceViewModel.uiState.collectAsStateWithLifecycle()
    val dataManagementUiState by dataManagementViewModel.uiState.collectAsStateWithLifecycle()
    val planningAgentUiState by planningAgentViewModel.uiState.collectAsStateWithLifecycle()
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
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.TODAY) }
    var selectedTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var taskEditorTarget by remember { mutableStateOf<Task?>(null) }
    var showTaskEditor by rememberSaveable { mutableStateOf(false) }
    var completingTask by remember { mutableStateOf<Task?>(null) }
    var partiallyCompletingTask by remember { mutableStateOf<Task?>(null) }
    var feedbackTask by remember { mutableStateOf<Task?>(null) }
    var postponingTask by remember { mutableStateOf<Task?>(null) }
    var cancellingTask by remember { mutableStateOf<Task?>(null) }
    var replacingTask by remember { mutableStateOf<Task?>(null) }
    var correctingLog by remember { mutableStateOf<TaskExecutionLog?>(null) }
    var weeklyBlockEditorTarget by remember { mutableStateOf<WeeklyTimeBlock?>(null) }
    var showWeeklyBlockEditor by rememberSaveable { mutableStateOf(false) }
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
    var lastPlanningAgentTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewingAgentDraft by rememberSaveable { mutableStateOf(false) }
    val reviewDate = LocalDate.now()
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
    val generatePlanDraft = {
        planViewModel.generateDraft(
            tasks = uiState.tasks,
            weeklyBlocks = timeUiState.weeklyBlocks,
            dateOverrides = timeUiState.dateOverrides,
            semesterFirstWeekMonday = timeUiState.semesterFirstWeekMonday,
            lockedSegments = planUiState.currentPlan?.segments
                ?.filter { segment ->
                    segment.isLocked && !segment.hasEndedBefore(LocalDateTime.now())
                }
                .orEmpty(),
            categoryPreferences = categoryPreferenceUiState.weights,
            manualTaskOrder = planUiState.currentPlan
                ?.takeIf { it.hasManualTaskOrder }
                ?.orderedTaskIds
                .orEmpty(),
        )
    }
    val taskRevision = uiState.tasks.maxOfOrNull(Task::updatedAtEpochMillis) ?: 0L
    val timeConstraintRevision = maxOf(
        timeUiState.weeklyBlocks.maxOfOrNull(WeeklyTimeBlock::updatedAtEpochMillis) ?: 0L,
        timeUiState.dateOverrides.maxOfOrNull(DateOverride::updatedAtEpochMillis) ?: 0L,
        timeUiState.timeConstraintsUpdatedAtEpochMillis,
    )

    // Feedback and future-constraint changes can propose a new plan, but only the user
    // can accept it. The draft itself is deliberately not a key: dismissing a draft
    // must not cause the system to immediately recreate it without a new user change.
    LaunchedEffect(planUiState.currentPlan?.id, taskRevision, timeConstraintRevision) {
        if (
            planNeedsUpdate &&
            planUiState.draft == null &&
            !uiState.isLoading &&
            !timeUiState.isLoading
        ) {
            generatePlanDraft()
        }
    }

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
            onComplete = { completingTask = selectedTask },
            onPartialCompletion = { partiallyCompletingTask = selectedTask },
            onFeedback = { feedbackTask = selectedTask },
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
                CenterAlignedTopAppBar(title = { Text(stringResource(selectedTab.titleRes)) })
            },
            bottomBar = {
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            modifier = Modifier.testTag("navigation-${tab.name.lowercase()}"),
                            icon = { Text(stringResource(tab.shortRes).take(1)) },
                            label = { Text(stringResource(tab.shortRes)) },
                        )
                    }
                }
            },
            floatingActionButton = {
                when (selectedTab) {
                    AppTab.TODAY, AppTab.TASKS -> {
                        FloatingActionButton(
                            onClick = {
                                taskEditorTarget = null
                                showTaskEditor = true
                            },
                            modifier = Modifier.testTag("add-task"),
                        ) { Text("+") }
                    }

                    AppTab.TIME -> {
                        FloatingActionButton(
                            onClick = {
                                weeklyBlockEditorTarget = null
                                showWeeklyBlockEditor = true
                            },
                            modifier = Modifier.testTag("add-time-block"),
                        ) { Text("+") }
                    }

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
                        tasks = uiState.tasks.filter(Task::isTodayRelevant),
                        allTasks = uiState.tasks,
                        planSegments = planUiState.currentPlan?.segments
                            ?.filter { it.date == LocalDate.now() }
                            .orEmpty(),
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
                            taskEditorTarget = null
                            showTaskEditor = true
                        },
                    )

                    AppTab.TASKS -> TasksScreen(
                        tasks = uiState.tasks,
                        isLoading = uiState.isLoading,
                        onOpen = { selectedTaskId = it.id },
                        onStart = taskViewModel::startTask,
                        onAdd = {
                            taskEditorTarget = null
                            showTaskEditor = true
                        },
                    )

                    AppTab.TIME -> TimeScreen(
                        weeklyBlocks = timeUiState.weeklyBlocks,
                        dateOverrides = timeUiState.dateOverrides,
                        isLoading = timeUiState.isLoading,
                        timetableImport = timeUiState.timetableImport,
                        semesterFirstWeekMonday = timeUiState.semesterFirstWeekMonday,
                        currentPlanSegmentCount = planUiState.currentPlan?.segments?.size ?: 0,
                        hasPlanHistory = planUiState.planHistory.isNotEmpty(),
                        planNeedsUpdate = planNeedsUpdate,
                        onImportPdf = timeViewModel::readTimetable,
                        onClearTimetableImport = timeViewModel::clearTimetableImport,
                        onGenerateDraft = generatePlanDraft,
                        onEditSemesterStart = { showSemesterStartEditor = true },
                        onOpenPlanOverview = { showPlanOverview = true },
                        onAddWeeklyBlock = {
                            weeklyBlockEditorTarget = null
                            showWeeklyBlockEditor = true
                        },
                        onEditWeeklyBlock = {
                            weeklyBlockEditorTarget = it
                            showWeeklyBlockEditor = true
                        },
                        onDeleteWeeklyBlock = { deletingWeeklyBlock = it },
                        onAddDateOverride = {
                            dateOverrideEditorTarget = null
                            showDateOverrideEditor = true
                        },
                        onEditDateOverride = {
                            dateOverrideEditorTarget = it
                            showDateOverrideEditor = true
                        },
                        onDeleteDateOverride = { deletingDateOverride = it },
                    )
                    AppTab.MINE -> MineScreen(
                        weights = categoryPreferenceUiState.weights,
                        profileEvidence = profileEvidenceUiState.evidence,
                        isLoading = categoryPreferenceUiState.isLoading ||
                            reminderSettingsUiState.isLoading || profileEvidenceUiState.isLoading ||
                            planningAgentUiState.isLoading,
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
                    )
                }
            }
        }
    }

    if (showTaskEditor) {
        TaskEditorDialog(
            task = taskEditorTarget,
            onDismiss = { showTaskEditor = false },
            onSave = {
                taskViewModel.saveTask(it)
                showTaskEditor = false
            },
        )
    }

    completingTask?.let { task ->
        CompleteTaskDialog(
            task = task,
            onDismiss = { completingTask = null },
            onConfirm = { content, result, actualMinutes, progress ->
                taskViewModel.completeTask(task.id, content, result, actualMinutes, progress)
                completingTask = null
            },
        )
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
            onDismiss = { showWeeklyBlockEditor = false },
            onSave = {
                timeViewModel.saveWeeklyBlock(it)
                showWeeklyBlockEditor = false
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

    (timeUiState.timetableImport as? TimetableImportState.Review)?.let { review ->
        TimetableImportReviewDialog(
            courses = review.courses,
            onDismiss = timeViewModel::clearTimetableImport,
            onConfirm = timeViewModel::confirmTimetableImport,
        )
    }

    planUiState.draft?.let { draft ->
        PlanDraftDialog(
            draft = draft,
            tasks = uiState.tasks,
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
private fun TodayScreen(
    tasks: List<Task>,
    allTasks: List<Task>,
    planSegments: List<PlannedSegment>,
    hasConfirmedPlan: Boolean,
    pendingConfirmationSegments: List<PlannedSegment>,
    isLoading: Boolean,
    onOpen: (Task) -> Unit,
    onStart: (String) -> Unit,
    onOpenDailyReview: () -> Unit,
    onAdd: () -> Unit,
) {
    if (isLoading) return
    if (hasConfirmedPlan || planSegments.isNotEmpty() || pendingConfirmationSegments.isNotEmpty()) {
        val taskById = allTasks.associateBy(Task::id)
        val plannedTaskIds = planSegments.map(PlannedSegment::taskId).toSet()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                TextButton(onClick = onOpenDailyReview) {
                    Text(stringResource(R.string.open_daily_review))
                }
            }
            if (planSegments.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.today_schedule),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            if (pendingConfirmationSegments.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.pending_confirmation_segments),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.pending_confirmation_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(
                    items = pendingConfirmationSegments,
                    key = { segment -> "pending-${segment.id.ifBlank { "${segment.taskId}-${segment.date}-${segment.startMinute}" }}" },
                ) { segment ->
                    taskById[segment.taskId]?.let { task ->
                        PendingConfirmationTaskCard(
                            task = task,
                            segment = segment,
                            onOpen = { onOpen(task) },
                        )
                    }
                }
            }
            if (planSegments.isNotEmpty()) {
                items(
                    items = planSegments,
                    key = { segment ->
                        segment.id.ifBlank { "${segment.taskId}-${segment.date}-${segment.startMinute}" }
                    },
                ) { segment ->
                    taskById[segment.taskId]?.let { task ->
                        PlannedTaskCard(
                            task = task,
                            segment = segment,
                            onOpen = { onOpen(task) },
                            onStart = { onStart(task.id) },
                        )
                    }
                }
            }
            tasks.filter { it.id !in plannedTaskIds }.takeIf(List<Task>::isNotEmpty)?.let { otherTasks ->
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.other_active_tasks),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(otherTasks, key = Task::id) { task ->
                    TaskCard(
                        task = task,
                        onOpen = { onOpen(task) },
                        onStart = { onStart(task.id) },
                    )
                }
            }
        }
        return
    }
    if (tasks.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.today_empty),
            actionLabel = stringResource(R.string.add_first_task),
            onAction = onAdd,
        )
        return
    }
    TaskList(tasks = tasks, onOpen = onOpen, onStart = onStart)
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
private fun TasksScreen(
    tasks: List<Task>,
    isLoading: Boolean,
    onOpen: (Task) -> Unit,
    onStart: (String) -> Unit,
    onAdd: () -> Unit,
) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val visibleTasks = tasks.filter { task -> task.status.isActive != showHistory }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !showHistory,
                onClick = { showHistory = false },
                label = { Text(stringResource(R.string.active_tasks)) },
            )
            FilterChip(
                selected = showHistory,
                onClick = { showHistory = true },
                label = { Text(stringResource(R.string.history_tasks)) },
            )
        }
        if (!isLoading && visibleTasks.isEmpty()) {
            EmptyState(
                title = stringResource(
                    if (showHistory) R.string.no_history_tasks else R.string.no_active_tasks,
                ),
                actionLabel = if (showHistory) null else stringResource(R.string.add_first_task),
                onAction = onAdd,
            )
        } else if (!isLoading) {
            TaskList(tasks = visibleTasks, onOpen = onOpen, onStart = onStart)
        }
    }
}

@Composable
private fun TaskList(
    tasks: List<Task>,
    onOpen: (Task) -> Unit,
    onStart: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(tasks, key = Task::id) { task ->
            TaskCard(
                task = task,
                onOpen = { onOpen(task) },
                onStart = { onStart(task.id) },
            )
        }
    }
}

@Composable
private fun TaskCard(
    task: Task,
    onOpen: () -> Unit,
    onStart: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("task-card-${task.displayName}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                task.description.takeIf { it.isNotBlank() && it != task.displayName }?.let { intro ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = intro,
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
private fun TaskStatusButton(task: Task, onStart: () -> Unit) {
    val canStart = task.status == TaskStatus.NOT_STARTED || task.status == TaskStatus.POSTPONED
    OutlinedButton(
        onClick = onStart,
        enabled = canStart,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
        Text(stringResource(task.status.labelRes()))
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
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.task_detail)) },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("task-detail-back"),
                    ) { Text(stringResource(R.string.back)) }
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
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = task.displayName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.width(12.dp))
                            TaskStatusButton(task = task, onStart = { onStart(task.id) })
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = task.description.takeIf { it.isNotBlank() && it != task.displayName }
                                ?: stringResource(R.string.no_introduction),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                DetailSection(title = stringResource(R.string.task_information)) {
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
                            stringResource(R.string.due_date_format, it.toString()),
                        )
                    }
                }
            }
            (task.completionSummary != null || task.completionResult != null ||
                task.actualDurationMinutes != null || task.progressPercent != null).takeIf { it }?.let {
                item {
                    DetailSection(title = stringResource(R.string.latest_feedback)) {
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
                DetailSection(title = stringResource(R.string.execution_logs)) {
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
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        content()
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ExecutionLogCard(log: TaskExecutionLog, onCorrect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
    onReplace: () -> Unit,
    onRestore: () -> Unit,
    isAiEnabled: Boolean,
    onRequestAiAdvice: (PlanningAgentRequestType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (task.status.isActive) {
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().testTag("complete-task"),
            ) {
                Text(stringResource(R.string.complete))
            }
            OutlinedButton(
                onClick = onPartialCompletion,
                modifier = Modifier.fillMaxWidth().testTag("partial-completion"),
            ) {
                Text(stringResource(R.string.partial_completion))
            }
            OutlinedButton(onClick = onPostpone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.postpone))
            }
            OutlinedButton(onClick = onReplace, modifier = Modifier.fillMaxWidth()) {
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
            OutlinedButton(
                onClick = { onRequestAiAdvice(PlanningAgentRequestType.TASK_UNDERSTANDING) },
                modifier = Modifier.fillMaxWidth().testTag("request-ai-advice"),
            ) {
                Text(stringResource(R.string.request_ai_advice))
            }
            TextButton(
                onClick = { onRequestAiAdvice(PlanningAgentRequestType.DIFFICULTY_AND_DURATION) },
                modifier = Modifier.fillMaxWidth().testTag("request-agent-difficulty"),
            ) { Text(stringResource(R.string.request_agent_difficulty)) }
            TextButton(
                onClick = { onRequestAiAdvice(PlanningAgentRequestType.TASK_BREAKDOWN) },
                modifier = Modifier.fillMaxWidth().testTag("request-agent-breakdown"),
            ) { Text(stringResource(R.string.request_agent_breakdown)) }
            TextButton(
                onClick = { onRequestAiAdvice(PlanningAgentRequestType.SORTING_EXPLANATION) },
                modifier = Modifier.fillMaxWidth().testTag("request-agent-sorting"),
            ) { Text(stringResource(R.string.request_agent_sorting)) }
            if (task.status.isActive) {
                TextButton(
                    onClick = { onRequestAiAdvice(PlanningAgentRequestType.REPLAN) },
                    modifier = Modifier.fillMaxWidth().testTag("request-agent-replan"),
                ) { Text(stringResource(R.string.request_agent_replan)) }
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
            Text(stringResource(R.string.ai_consent_message))
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
    weeklyBlocks: List<WeeklyTimeBlock>,
    dateOverrides: List<DateOverride>,
    isLoading: Boolean,
    timetableImport: TimetableImportState,
    semesterFirstWeekMonday: LocalDate?,
    currentPlanSegmentCount: Int,
    hasPlanHistory: Boolean,
    planNeedsUpdate: Boolean,
    onImportPdf: (Uri) -> Unit,
    onClearTimetableImport: () -> Unit,
    onGenerateDraft: () -> Unit,
    onEditSemesterStart: () -> Unit,
    onOpenPlanOverview: () -> Unit,
    onAddWeeklyBlock: () -> Unit,
    onEditWeeklyBlock: (WeeklyTimeBlock) -> Unit,
    onDeleteWeeklyBlock: (WeeklyTimeBlock) -> Unit,
    onAddDateOverride: () -> Unit,
    onEditDateOverride: (DateOverride) -> Unit,
    onDeleteDateOverride: (DateOverride) -> Unit,
) {
    var showOverrides by rememberSaveable { mutableStateOf(false) }
    val timetablePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(onImportPdf) },
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.time_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SemesterWeekCard(
                semesterFirstWeekMonday = semesterFirstWeekMonday,
                onEdit = onEditSemesterStart,
            )
        }
        item {
            Button(
                onClick = onGenerateDraft,
                modifier = Modifier.fillMaxWidth().testTag("generate-plan-draft"),
            ) {
                Text(stringResource(R.string.generate_plan_draft))
            }
        }
        if (currentPlanSegmentCount > 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.current_plan_segment_count, currentPlanSegmentCount),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onOpenPlanOverview) {
                        Text(stringResource(R.string.view_plan))
                    }
                }
            }
        }
        if (planNeedsUpdate) {
            item {
                Text(
                    stringResource(R.string.plan_needs_update),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else if (hasPlanHistory && currentPlanSegmentCount == 0) {
            item {
                TextButton(onClick = onOpenPlanOverview) {
                    Text(stringResource(R.string.view_plan_history))
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.import_timetable_hint),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                OutlinedButton(
                    onClick = { timetablePicker.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.testTag("import-timetable-pdf"),
                ) {
                    Text(stringResource(R.string.import_timetable_pdf))
                }
            }
        }
        when (timetableImport) {
            TimetableImportState.Idle,
            is TimetableImportState.Review -> Unit

            TimetableImportState.Reading -> item {
                Text(
                    stringResource(R.string.reading_timetable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            is TimetableImportState.Failed -> item {
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
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showOverrides,
                    onClick = { showOverrides = false },
                    label = { Text(stringResource(R.string.weekly_blocks)) },
                )
                FilterChip(
                    selected = showOverrides,
                    onClick = { showOverrides = true },
                    label = { Text(stringResource(R.string.date_overrides)) },
                )
            }
        }
        if (showOverrides) {
            item {
                TimeListHeading(
                    title = stringResource(R.string.date_overrides),
                    actionLabel = stringResource(R.string.add_date_override),
                    onAction = onAddDateOverride,
                )
            }
            if (!isLoading && dateOverrides.isEmpty()) {
                item { TimeEmptyState(R.string.no_date_overrides, onAddDateOverride) }
            }
            items(dateOverrides, key = DateOverride::id) { dateOverride ->
                DateOverrideCard(
                    dateOverride = dateOverride,
                    onEdit = { onEditDateOverride(dateOverride) },
                    onDelete = { onDeleteDateOverride(dateOverride) },
                )
            }
        } else {
            item {
                TimeListHeading(
                    title = stringResource(R.string.weekly_blocks),
                    actionLabel = stringResource(R.string.add_weekly_block),
                    onAction = onAddWeeklyBlock,
                )
            }
            if (!isLoading && weeklyBlocks.isEmpty()) {
                item { TimeEmptyState(R.string.no_weekly_blocks, onAddWeeklyBlock) }
            }
            items(weeklyBlocks, key = WeeklyTimeBlock::id) { block ->
                WeeklyBlockCard(
                    block = block,
                    onEdit = { onEditWeeklyBlock(block) },
                    onDelete = { onDeleteWeeklyBlock(block) },
                )
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
private fun PlanDraftDialog(
    draft: PlanDraft,
    tasks: List<Task>,
    onDismiss: () -> Unit,
    onUpdateDraft: (PlanDraft) -> Unit,
    onAccept: () -> Unit,
) {
    val tasksById = tasks.associateBy(Task::id)
    val orderedTaskIds = draft.orderedTaskIds
        .distinct()
        .filter(tasksById::containsKey)
        .ifEmpty { (draft.segments.map(PlannedSegment::taskId) + draft.pendingTaskIds).distinct() }
    val priorityAssessmentsByTask = draft.priorityAssessments.associateBy(LocalPriorityAssessment::taskId)
    var editingSegmentId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_draft_title)) },
        text = {
            Column(
                modifier = Modifier
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.plan_draft_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (draft.priorityAssessments.isNotEmpty()) {
                    Text(
                        stringResource(R.string.local_planner_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (orderedTaskIds.isNotEmpty()) {
                    Text(
                        stringResource(R.string.plan_draft_task_order),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.plan_draft_drag_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                        }
                    }
                }
                if (draft.segments.isEmpty()) {
                    Text(
                        stringResource(
                            if (draft.pendingTaskIds.isNotEmpty()) R.string.list_only_plan
                            else R.string.no_plan_segments,
                        ),
                    )
                } else {
                    draft.segments.forEach { segment ->
                        Card(
                            modifier = Modifier.clickable { editingSegmentId = segment.id },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    tasksById[segment.taskId]?.displayName
                                        ?: stringResource(R.string.unknown_task),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    segment.date.toString() + " · " +
                                        TimeBlockValidator.formatTime(segment.startMinute) + "–" +
                                        TimeBlockValidator.formatTime(segment.endMinute),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (segment.isLocked) {
                                    Text(
                                        stringResource(R.string.segment_locked),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    TextButton(onClick = { editingSegmentId = segment.id }) {
                                        Text(stringResource(R.string.edit_plan_segment))
                                    }
                                    if (PlanDraftEditor.canSplit(segment)) {
                                        TextButton(
                                            onClick = {
                                                onUpdateDraft(PlanDraftEditor.splitSegment(draft, segment.id))
                                            },
                                        ) { Text(stringResource(R.string.split_plan_segment)) }
                                    }
                                    if (PlanDraftEditor.canMergeWithAdjacentSegment(draft, segment.id)) {
                                        TextButton(
                                            onClick = {
                                                onUpdateDraft(
                                                    PlanDraftEditor.mergeWithAdjacentSegment(draft, segment.id),
                                                )
                                            },
                                        ) { Text(stringResource(R.string.merge_plan_segment)) }
                                    }
                                    TextButton(
                                        onClick = {
                                            onUpdateDraft(
                                                PlanDraftEditor.setSegmentLocked(
                                                    draft,
                                                    segment.id,
                                                    !segment.isLocked,
                                                ),
                                            )
                                        },
                                    ) {
                                        Text(
                                            stringResource(
                                                if (segment.isLocked) R.string.unlock_segment
                                                else R.string.lock_segment,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                draft.pendingTaskIds.takeIf(List<String>::isNotEmpty)?.let { pendingIds ->
                    Text(
                        stringResource(R.string.plan_pending_tasks),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    pendingIds.forEach { taskId ->
                        Text("• " + (tasksById[taskId]?.displayName ?: stringResource(R.string.unknown_task)))
                    }
                }
                draft.unscheduledTasks.takeIf(List<*>::isNotEmpty)?.let { unscheduledTasks ->
                    Text(
                        stringResource(R.string.plan_feasibility_warning),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    unscheduledTasks.forEach { unscheduled ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(
                                    R.string.plan_unassigned_task,
                                    tasksById[unscheduled.taskId]?.displayName
                                        ?: stringResource(R.string.unknown_task),
                                    unscheduled.remainingMinutes,
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                stringResource(unscheduled.reason.labelRes()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAccept,
                modifier = Modifier.testTag("accept-plan-draft"),
                enabled = draft.segments.isNotEmpty() || draft.pendingTaskIds.isNotEmpty(),
            ) {
                Text(stringResource(R.string.accept_plan_draft))
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
private fun DraftTaskOrderRow(
    task: Task,
    assessment: LocalPriorityAssessment?,
    index: Int,
    total: Int,
    onMove: (Int) -> Unit,
) {
    var accumulatedDragY by remember(task.id) { mutableFloatStateOf(0f) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(task.id) {
                detectDragGestures(
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
                    Text(
                        text = stringResource(R.string.dynamic_priority_score, value.score),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
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
    hasConflict: (PlannedSegment) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (PlannedSegment) -> Unit,
) {
    var dateText by remember(segment) { mutableStateOf(segment.date.toString()) }
    var startTime by remember(segment) { mutableStateOf(TimeBlockValidator.formatTime(segment.startMinute)) }
    var endTime by remember(segment) { mutableStateOf(TimeBlockValidator.formatTime(segment.endMinute)) }
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
    AlertDialog(
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
) {
    if (isLoading) return
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
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            stringResource(R.string.local_reminders_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
        AiSettingsSection(
            aiEnabled = aiEnabled,
            aiConsented = aiConsented,
            onAiEnabledChange = onAiEnabledChange,
        )
        Text(stringResource(R.string.category_preferences_title), style = MaterialTheme.typography.titleLarge)
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
        ProfileEvidenceSection(
            evidence = profileEvidence,
            onGenerate = onGenerateProfileEvidence,
            onUpdate = onUpdateProfileEvidence,
            onDelete = onDeleteProfileEvidence,
            hasActionError = hasProfileActionError,
        )
        LocalDataManagementSection(
            isWorking = isDataWorking,
            result = dataResult,
            onExport = onExportLocalData,
            onClear = onClearLocalData,
        )
    }
}

@Composable
private fun AiSettingsSection(
    aiEnabled: Boolean,
    aiConsented: Boolean,
    onAiEnabledChange: (Boolean) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                        stringResource(R.string.ai_advisor_title),
                        style = MaterialTheme.typography.titleMedium,
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
            Text(
                stringResource(R.string.ai_advisor_not_configured),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
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
    @StringRes dialogTitle: Int? = null,
    @StringRes confirmLabel: Int = R.string.save,
    allowPriorityChange: Boolean = task == null,
    onDismiss: () -> Unit,
    onSave: (TaskDraft) -> Unit,
) {
    var displayName by remember(task) { mutableStateOf(task?.displayName.orEmpty()) }
    var description by remember(task) {
        mutableStateOf(task?.description.takeIf { it != task?.displayName }.orEmpty())
    }
    var category by remember(task) { mutableStateOf(task?.category ?: TaskCategory.COURSE) }
    var priority by remember(task) { mutableStateOf(task?.userPriority ?: TaskPriority.MEDIUM) }
    var estimatedDaysText by remember(task) { mutableStateOf(task?.estimatedDays?.toString() ?: "1") }
    var durationText by remember(task) { mutableStateOf(task?.totalDurationMinutes?.toString().orEmpty()) }
    var dueDateText by remember(task) { mutableStateOf(task?.dueDate?.toString().orEmpty()) }
    var showValidationError by remember { mutableStateOf(false) }

    AlertDialog(
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
                    label = { Text(stringResource(R.string.task_introduction_optional)) },
                    placeholder = { Text(stringResource(R.string.task_introduction_hint)) },
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
                )
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth().testTag("task-editor-duration"),
                    label = { Text(stringResource(R.string.duration_minutes_optional)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = dueDateText,
                    onValueChange = { dueDateText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.due_date_optional)) },
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
                    val draft = TaskDraft(
                        id = task?.id,
                        displayName = displayName,
                        description = description,
                        category = category,
                        userPriority = priority,
                        estimatedDays = estimatedDaysText.toIntOrNull() ?: 0,
                        totalDurationMinutes = durationText.takeIf(String::isNotBlank)?.toIntOrNull(),
                        dueDate = dueDateText.takeIf(String::isNotBlank)
                            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
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
) {
    var title by remember(block) { mutableStateOf(block?.title.orEmpty()) }
    var kind by remember(block) { mutableStateOf(block?.kind ?: TimeBlockKind.COURSE) }
    var dayOfWeek by remember(block) { mutableStateOf(block?.dayOfWeek ?: DayOfWeek.MONDAY) }
    var startTime by remember(block) {
        mutableStateOf(block?.startMinute?.let(TimeBlockValidator::formatTime) ?: "09:00")
    }
    var endTime by remember(block) {
        mutableStateOf(block?.endMinute?.let(TimeBlockValidator::formatTime) ?: "10:00")
    }
    var showValidationError by remember { mutableStateOf(false) }

    AlertDialog(
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
    var showValidationError by remember { mutableStateOf(false) }

    AlertDialog(
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
    AlertDialog(
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
    AiAdvisorFailureReason.DISABLED -> R.string.ai_failure_disabled
    AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED -> R.string.ai_failure_not_configured
    AiAdvisorFailureReason.TRANSPORT_FAILURE -> R.string.ai_failure_transport
    AiAdvisorFailureReason.TIMEOUT -> R.string.ai_failure_timeout
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
    PriorityReasonKind.LOCAL_AI_NEUTRAL -> stringResource(R.string.priority_reason_ai_neutral)
}

@StringRes
private fun UnscheduledReason.labelRes(): Int = when (this) {
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
