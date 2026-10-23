package com.swan1127.repland.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.widget.Toast
import android.content.Intent
import android.speech.RecognizerIntent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.swan1127.repland.R
import com.swan1127.repland.domain.model.ClassPeriodClock
import com.swan1127.repland.domain.model.AgentChatModel
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.AiProviderConfig
import com.swan1127.repland.domain.model.AiProviderPreset
import com.swan1127.repland.domain.model.DEFAULT_AGNES_MODEL
import com.swan1127.repland.domain.model.FocusSettings
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
import com.swan1127.repland.domain.model.ProfileEvidenceOutputs
import com.swan1127.repland.domain.model.ProfileEvidenceScope
import com.swan1127.repland.domain.model.ProfileIdentity
import com.swan1127.repland.domain.model.AvatarSaveResult
import com.swan1127.repland.domain.model.SubTask
import com.swan1127.repland.domain.model.SubTasks
import com.swan1127.repland.domain.model.SubTaskValidator
import com.swan1127.repland.domain.model.RepeatRule
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
import com.swan1127.repland.domain.model.ToolOutputKind
import com.swan1127.repland.domain.model.ScheduleTimeline
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.EngagementMode
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import com.swan1127.repland.domain.model.UnscheduledReason
import com.swan1127.repland.domain.ports.DataExportFormat
import com.swan1127.repland.domain.ports.ExportSink
import com.swan1127.repland.ui.schedule.FocalCard
import com.swan1127.repland.ui.schedule.focalCard
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
import com.swan1127.repland.ui.ai.AiModelListState
import com.swan1127.repland.ui.agent.AgentCenterScreen
import com.swan1127.repland.ui.agent.GroupChatSheet
import com.swan1127.repland.ui.agent.ArrangementAssistantViewModel
import com.swan1127.repland.ui.tasks.BatchTaskAction
import com.swan1127.repland.ui.tasks.BatchTasks
import com.swan1127.repland.ui.tasks.TaskSelection
import com.swan1127.repland.ui.tasks.TaskViewModel
import com.swan1127.repland.ui.schedule.TimelineDashboard
import com.swan1127.repland.ui.schedule.WeekScheduleView
import com.swan1127.repland.ui.schedule.MonthScheduleView
import com.swan1127.repland.ui.schedule.TimelineEventObject
import com.swan1127.repland.ui.schedule.ToolSuggestionStrip
import com.swan1127.repland.ui.engagement.EngagementViewModel
import com.swan1127.repland.ui.components.CapacitySummary
import com.swan1127.repland.ui.components.DesignButton
import com.swan1127.repland.ui.components.DesignFilterChip
import com.swan1127.repland.ui.components.DesignIcons
import com.swan1127.repland.ui.components.DesignNavigationItem
import com.swan1127.repland.ui.components.EditorSheet
import com.swan1127.repland.ui.components.AssistantSheet
import com.swan1127.repland.ui.components.NowCard
import com.swan1127.repland.ui.components.TaskCaptureSheet
import com.swan1127.repland.ui.components.TaskDatePickerDialog
import com.swan1127.repland.ui.components.TaskRow
import com.swan1127.repland.ui.components.TaskRowEmphasis
import com.swan1127.repland.ui.components.PlannerIcons
import com.swan1127.repland.ui.components.CommandEntry
import com.swan1127.repland.ui.components.CommandGroup
import com.swan1127.repland.ui.components.CommandPaletteSheet
import com.swan1127.repland.ui.components.taskDateLabel
import com.swan1127.repland.ui.components.rememberDateFormatter
import com.swan1127.repland.ui.components.rememberPlanImageTexts
import com.swan1127.repland.ui.theme.LocalReplandVisuals
import com.swan1127.repland.ui.theme.replandAccent
import com.swan1127.repland.ReplandApplication
import com.swan1127.repland.data.AppearanceMode
import com.swan1127.repland.domain.model.ThemeAccent
import com.swan1127.repland.data.ProfileStore
import com.swan1127.repland.data.ReviewPoint
import com.swan1127.repland.data.export.IcsExporter
import com.swan1127.repland.data.export.PlanImageExporter
import com.swan1127.repland.data.export.XlsxExporter
import com.swan1127.repland.data.importer.timetableSource
import com.swan1127.repland.ui.tools.ExplanationSlot
import com.swan1127.repland.ui.tools.TimetableSetRow
import com.swan1127.repland.ui.tools.ToolHost
import com.swan1127.repland.ui.tools.accentLabelRes
import com.swan1127.repland.ui.tools.ToolId
import com.swan1127.repland.ui.tools.ToolSituation
import com.swan1127.repland.ui.tools.ToolSuggestion
import com.swan1127.repland.ui.tools.recommendTools
import com.swan1127.repland.ui.tools.buildKwlDraft
import com.swan1127.repland.ui.tools.buildReflectionDraft
import com.swan1127.repland.ui.undo.UndoStore
import com.swan1127.repland.reminders.LocalReminderScheduler
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelChildren
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

/**
 * Screen destinations.
 *
 * Only entries flagged [primary] appear in the bottom navigation bar. TIME and AGENT stay
 * fully functional screens but are reached from the command palette or a contextual
 * button, so the persistent shell stays at three tabs while nothing becomes unreachable.
 */
private enum class AppTab(
    @param:StringRes val titleRes: Int,
    @param:StringRes val shortRes: Int,
    val primary: Boolean = false,
) {
    TODAY(R.string.today_title, R.string.tab_today, primary = true),
    PLAN(R.string.plan_tab_title, R.string.tab_plan, primary = true),
    AGENT(R.string.agent_title, R.string.tab_agent),
    TIME(R.string.time_title, R.string.tab_time),
    MINE(R.string.mine_title, R.string.tab_mine, primary = true),
}

private enum class ScheduleRange { DAY, WEEK, MONTH }

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
    agentChatModel: AgentChatModel,
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    themeAccent: ThemeAccent,
    onThemeAccentChange: (ThemeAccent) -> Unit,
) {
    val resetState by dataManagementViewModel.uiState.collectAsStateWithLifecycle()
    val resetContext = LocalContext.current
    androidx.compose.runtime.key(resetState.localResetVersion) {
    LaunchedEffect(resetState.localResetVersion) {
        if (resetState.localResetVersion > 0) {
            com.swan1127.repland.ui.agent.clearGroupChatSession()
            planningAgentViewModel.dismiss()
            aiProviderConfigViewModel.resetTransientState()
            com.swan1127.repland.ui.widget.HomeWidgetProvider.requestRefresh(resetContext)
        }
    }
    val uiState by taskViewModel.uiState.collectAsStateWithLifecycle()
    val timeUiState by timeViewModel.uiState.collectAsStateWithLifecycle()
    val planUiState by planViewModel.uiState.collectAsStateWithLifecycle()
    val categoryPreferenceUiState by categoryPreferenceViewModel.uiState.collectAsStateWithLifecycle()
    val reminderSettingsUiState by reminderSettingsViewModel.uiState.collectAsStateWithLifecycle()
    val profileEvidenceUiState by profileEvidenceViewModel.uiState.collectAsStateWithLifecycle()
    val dataManagementUiState by dataManagementViewModel.uiState.collectAsStateWithLifecycle()
    val planningAgentUiState by planningAgentViewModel.uiState.collectAsStateWithLifecycle()
    val aiProviderConfigUiState by aiProviderConfigViewModel.uiState.collectAsStateWithLifecycle()
    val arrangementAssistantAccess by arrangementAssistantViewModel.access.collectAsStateWithLifecycle()
    val engagementMode by engagementViewModel.mode.collectAsStateWithLifecycle()
    var activeDate by remember { mutableStateOf(LocalDate.now()) }
    var currentMinute by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            currentMinute = LocalDateTime.now()
            activeDate = currentMinute.toLocalDate()
        }
    }
    val context = LocalContext.current
    // D5. Session-scoped: undo is for "I just mis-tapped", and persisting whole arrangements to
    // disk would mean reasoning about which of them still make sense after hand edits.
    val undoStore = remember { UndoStore() }
    val undoScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(planUiState.failureMessage, planUiState.draft) {
        if (planUiState.draft == null) {
            planUiState.failureMessage?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    /**
     * D5. The one way in and out of the undo history.
     *
     * Undo and redo differ only in direction, so they share a body; and after either one the user
     * is offered the way back, which is what makes a mis-tap a one-tap fix rather than a hunt
     * through a menu. The way back re-enters this same function, so every hop announces itself —
     * the design rule is that nothing moves silently, and that has to hold for the second hop too,
     * not just the first.
     */
    fun applyHistory(isRedo: Boolean) {
        undoScope.launch {
            val label = if (isRedo) undoStore.redo() else undoStore.undo()
            if (label == null) {
                snackbarHostState.showSnackbar(context.getString(R.string.undo_nothing))
                return@launch
            }
            val doneRes = if (isRedo) R.string.redo_done else R.string.undo_done
            val backRes = if (isRedo) R.string.undo else R.string.redo
            val result = snackbarHostState.showSnackbar(
                message = context.getString(doneRes, label),
                actionLabel = context.getString(backRes),
            )
            if (result == SnackbarResult.ActionPerformed) applyHistory(!isRedo)
        }
    }

    /**
     * D5. Runs a destructive-but-reversible write, then offers the way back on screen.
     *
     * The snackbar is the whole point of the feature: the moment a user is most likely to want an
     * undo is the moment right after they did the thing, and asking them to open a palette and
     * find "撤销上一步" there is asking them to do it while annoyed. The action hands off to
     * [applyHistory], so the chain keeps announcing itself instead of ending in a silent flip.
     *
     * The same [stepLabel] is used as the undo entry's name and as the snackbar text, so what the
     * user reads is literally what the history recorded — there is no second phrasing to drift.
     */
    fun runReversible(stepLabel: String, write: suspend () -> Unit) {
        undoScope.launch {
            undoStore.transaction(
                label = stepLabel,
                capture = { timeViewModel.currentArrangement() },
                apply = write,
                restore = { timeViewModel.restoreArrangement(it) },
            )
            val result = snackbarHostState.showSnackbar(
                message = stepLabel,
                actionLabel = context.getString(R.string.undo),
            )
            if (result == SnackbarResult.ActionPerformed) applyHistory(false)
        }
    }

    val notificationPermissionGranted = context.canPostLocalNotifications()
    val reminderScheduler = remember(context) { LocalReminderScheduler(context.applicationContext) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) reminderSettingsViewModel.setEnabled(true)
    }
    // The one place a Uri becomes a stream. Everything past this point works in bytes, which is
    // what lets the export logic be exercised in a unit test instead of only on a device.
    val exportSinkFor: (Uri) -> ExportSink = { uri ->
        ExportSink { bytes ->
            val stream = context.contentResolver.openOutputStream(uri)
            checkNotNull(stream) { "The selected export destination cannot be opened." }
            stream.use { it.write(bytes) }
        }
    }
    val auxiliaryStore = remember(context) {
        (context.applicationContext as ReplandApplication).appContainer.auxiliaryStore
    }
    // The JSON export carries the user's tool writing as its own array, so it is read here rather
    // than inside the ViewModel: the store lives outside Room and the ViewModel cannot see it. The
    // spreadsheet export does not take it — that file is a study plan, not a data dump, and adding
    // a sheet nothing reads would be worse than leaving it out.
    val localDataExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DataExportFormat.JSON.mimeType),
    ) { destination ->
        destination?.let {
            dataManagementViewModel.exportTo(
                DataExportFormat.JSON,
                exportSinkFor(it),
                auxiliaryStore.toolOutputRecords(),
            )
        }
    }
    // The picker's own filter follows the format's MIME type, so a phone that can open a
    // spreadsheet offers a spreadsheet app and nothing else.
    val spreadsheetExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DataExportFormat.XLSX.mimeType),
    ) { destination ->
        destination?.let { dataManagementViewModel.exportTo(DataExportFormat.XLSX, exportSinkFor(it)) }
    }
    // The focus-round length lives in SharedPreferences, so it survives a restart without a
    // Room migration. Only the focus screen writes it, so mirroring it in Compose state here
    // is enough — no revision counter is needed to notice an outside change.
    var focusMinutes by remember { mutableStateOf(auxiliaryStore.focusSettings.focusMinutes) }
    val onFocusMinutesChange: (Int) -> Unit = { minutes ->
        focusMinutes = minutes
        auxiliaryStore.focusSettings = FocusSettings.of(minutes)
    }
    // D3: the .ics text is generated locally and handed to the system picker, so no
    // storage permission is involved and the user chooses the destination.
    var pendingIcs by remember { mutableStateOf<String?>(null) }
    var icsExportMessage by remember { mutableStateOf<String?>(null) }
    var icsExportFailed by remember { mutableStateOf(false) }
    val icsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar"),
    ) { destination ->
        val payload = pendingIcs
        pendingIcs = null
        // A null destination means the user backed out of the picker, which is not a failure and
        // must not be reported as one.
        if (destination == null || payload == null) return@rememberLauncherForActivityResult
        val failed = runCatching {
            context.contentResolver.openOutputStream(destination)?.use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
            } ?: error("no output stream")
        }.isFailure
        icsExportFailed = failed
        icsExportMessage = context.getString(
            if (failed) R.string.export_ics_failed else R.string.export_ics_success,
        )
    }
    // D4: same shape as the .ics export — the bitmap is drawn locally and handed to the
    // system picker, so no storage permission is involved on any API level.
    var pendingImage by remember { mutableStateOf<Bitmap?>(null) }
    var imageExportMessage by remember { mutableStateOf<String?>(null) }
    var timetableSetMessage by remember { mutableStateOf<String?>(null) }
    val imageExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { destination ->
        val bitmap = pendingImage
        pendingImage = null
        if (destination == null || bitmap == null) return@rememberLauncherForActivityResult
        val written = PlanImageExporter.writeTo(context, bitmap, destination)
        imageExportMessage = context.getString(
            if (written) R.string.export_image_success else R.string.export_image_failed,
        )
    }
    // D1: the .xlsx picker is separate from the PDF one because SAF hands back an opaque
    // content:// id with no usable extension, so the ViewModel cannot tell the two formats
    // apart from the URI alone — the caller has to say which door the user came through.
    val spreadsheetImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { timeViewModel.readSpreadsheet(context.timetableSource(it)) }
    }
    val clipboardManager = LocalClipboardManager.current
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
    var secondaryReturnTab by rememberSaveable { mutableStateOf(AppTab.TODAY) }
    var showCommandPalette by rememberSaveable { mutableStateOf(false) }
    // Stored by name rather than as the enum so rememberSaveable only needs a String.
    var activeToolName by rememberSaveable { mutableStateOf<String?>(null) }
    val activeTool = activeToolName?.let { name -> ToolId.entries.firstOrNull { it.name == name } }
    // The auxiliary store is not observable, so writes bump a counter to force a re-read.
    var auxRevision by remember { mutableStateOf(0) }
    var selectedTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var taskEditorTarget by remember { mutableStateOf<Task?>(null) }
    var showTaskEditor by rememberSaveable { mutableStateOf(false) }
    var showTaskCapture by rememberSaveable { mutableStateOf(false) }
    var showQuickArrange by rememberSaveable { mutableStateOf(false) }
    var taskEditorSeed by rememberSaveable { mutableStateOf("") }
    var partiallyCompletingTask by remember { mutableStateOf<Task?>(null) }
    var feedbackTask by remember { mutableStateOf<Task?>(null) }
    var postponingTask by remember { mutableStateOf<Task?>(null) }
    var cancellingTask by remember { mutableStateOf<Task?>(null) }
    var replacingTask by remember { mutableStateOf<Task?>(null) }
    var correctingLog by remember { mutableStateOf<TaskExecutionLog?>(null) }
    // `null` means the task list is not in selection mode. One nullable field rather than a
    // `selecting` flag beside a set of ids: the pair `selecting = false, ids = {…}` is impossible
    // and should not be expressible, and `TaskRow` takes its `selected` from this same shape.
    var taskSelection by remember { mutableStateOf<TaskSelection?>(null) }
    // The action the user tapped and has not yet confirmed. Held separately from the selection so
    // that dismissing the dialog leaves the ticks alone.
    var pendingBatchAction by remember { mutableStateOf<BatchTaskAction?>(null) }
    var weeklyBlockEditorTarget by remember { mutableStateOf<WeeklyTimeBlock?>(null) }
    var showWeeklyBlockEditor by rememberSaveable { mutableStateOf(false) }
    var dateOverrideEditorTarget by remember { mutableStateOf<DateOverride?>(null) }
    var showDateOverrideEditor by rememberSaveable { mutableStateOf(false) }
    var deletingWeeklyBlock by remember { mutableStateOf<WeeklyTimeBlock?>(null) }
    var deletingDateOverride by remember { mutableStateOf<DateOverride?>(null) }
    var deletingTimetableSet by remember { mutableStateOf<String?>(null) }
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
    // The insight page draws a fixed 30-day window, so the query is bounded rather than
    // loading the whole log table as the database grows.
    val insightLogsFlow = remember(activeDate) {
        taskViewModel.observeExecutionLogsSince(activeDate.minusDays(29))
    }
    val insightLogs by insightLogsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val reviewPointDates = remember(auxRevision) {
        auxiliaryStore.reviewPoints().map(ReviewPoint::createdOn)
    }
    // Which metacognition tool to offer, decided by the pure rule in ToolRecommendation.kt.
    //
    // `nowMinute` is a key rather than a value read inside the block: the rule's answer depends on
    // whether a reading block has already ended, so a recommendation computed once and cached
    // across the evening would keep saying "your reading block just ended" at midnight. It is read
    // here the same way TodayScreen reads it — plain `LocalTime.now()` per composition, with no new
    // timer — so the strip and the timeline can never disagree about what time it is.
    val nowMinute = currentMinute.let { it.hour * 60 + it.minute }
    val toolSuggestions = remember(activeDate, uiState.tasks, planUiState.currentPlan, auxRevision, nowMinute) {
        recommendTools(
            ToolSituation(
                nowMinute = nowMinute,
                tasks = uiState.tasks,
                todaySegments = planUiState.currentPlan?.segments?.filter { it.date == activeDate }.orEmpty(),
                dueReviewPointCount = auxiliaryStore.dueReviewPoints(activeDate).size,
                hasReflection = auxiliaryStore.reflection(activeDate) != null,
                kwlLearnedTaskIds = auxiliaryStore.kwlEntries()
                    .filterValues { it.learned.isNotBlank() }
                    .keys,
                // The self-explanation tool keys its slots by task id, so these are task ids too.
                explainedTaskIds = auxiliaryStore.explainedLogKeys(),
            ),
        )
    }
    // The user's tool writing, shown next to the evidence row it relates to. Computed here rather
    // than inside the card so the decision stays a pure function with its own tests, and re-read
    // whenever the auxiliary store changes — it has no observable of its own, so `auxRevision` is
    // that signal, exactly as it is for the tool strip above.
    val profileEvidenceWithOutputs = remember(profileEvidenceUiState.evidence, auxRevision) {
        ProfileEvidenceOutputs.attach(profileEvidenceUiState.evidence, auxiliaryStore.toolOutputRecords())
    }
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
            onComplete = {
                // Finishing is the moment a task's own wording becomes worth resurfacing, so
                // the review ladder is seeded from whatever the task already carries. If it
                // carries nothing, the self-explanation tool is what fills that gap.
                auxiliaryStore.addReviewPointsFor(selectedTask, today = activeDate)
                taskViewModel.completeTask(selectedTask.id)
            },
            onPartialCompletion = { partiallyCompletingTask = selectedTask },
            onFeedback = { feedbackTask = selectedTask },
            onOpenAssistant = { assistantTaskId = selectedTask.id; showAssistant = true },
            onReplace = { replacingTask = selectedTask },
            onRestore = { taskViewModel.restoreTask(selectedTask.id) },
            onCorrectLog = { correctingLog = it },
            onReviewAdjustmentDraft = {
                selectedTaskId = null
                secondaryReturnTab = selectedTab
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
            onSubTaskToggle = { subTaskId, isDone -> taskViewModel.toggleSubTask(selectedTask.id, subTaskId, isDone) },
            onSubTaskAdd = { title -> taskViewModel.addSubTask(selectedTask.id, title) },
            onSubTaskRemove = { subTaskId -> taskViewModel.removeSubTask(selectedTask.id, subTaskId) },
        )
    } else if (activeTool != null) {
        // Tools render as full-screen destinations so the three-tab shell stays untouched.
        // Resolved here rather than inside the export callbacks: those are plain lambdas, not
        // composable ones, so they cannot read resources at the moment the user taps Export.
        val planImageTexts = rememberPlanImageTexts()
        ToolHost(
            tool = activeTool,
            today = activeDate,
            appearanceMode = appearanceMode,
            onAppearanceChange = onAppearanceModeChange,
            themeAccent = themeAccent,
            onThemeAccentChange = onThemeAccentChange,
            segmentCountToday = planUiState.currentPlan?.segments?.count { it.date == activeDate } ?: 0,
            segmentCountAll = planUiState.currentPlan?.segments?.size ?: 0,
            icsMessage = icsExportMessage,
            icsMessageIsError = icsExportFailed,
            onExportToday = {
                // Clear the previous result first, so a stale "已导出" cannot be mistaken for the
                // outcome of this attempt while the picker is still open.
                icsExportMessage = null
                pendingIcs = IcsExporter.build(planUiState.currentPlan, uiState.tasks, activeDate)
                icsExportLauncher.launch(IcsExporter.fileName(activeDate))
            },
            onExportAll = {
                icsExportMessage = null
                pendingIcs = IcsExporter.build(planUiState.currentPlan, uiState.tasks, null)
                icsExportLauncher.launch(IcsExporter.fileName(null))
            },
            reflectionDraft = remember(activeDate, dailyLogs, uiState.tasks) {
                buildReflectionDraft(activeDate, dailyLogs, uiState.tasks)
            },
            savedReflection = remember(activeDate, auxRevision) { auxiliaryStore.reflection(activeDate) },
            onSaveReflection = { text ->
                auxiliaryStore.saveReflection(activeDate, text)
                auxRevision++
                undoScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.reflection_saved),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            tasks = uiState.tasks,
            insightLogs = insightLogs,
            reviewPointDates = reviewPointDates,
            kwlDraftFor = { task -> buildKwlDraft(task, uiState.tasks) },
            kwlLoadFor = { taskId -> auxiliaryStore.kwl(taskId) },
            onSaveKwl = { entry ->
                auxiliaryStore.saveKwl(entry)
                // The "L" column is the one sentence in a KWL chart worth resurfacing later: it is
                // what the user says they did not know before. It seeds a review point through the
                // same entry point the self-explanation uses, so the spaced-repetition queue fills
                // up from the tool the user actually opened, rather than only from the review
                // screen they have to remember to visit.
                uiState.tasks.firstOrNull { it.id == entry.taskId }?.let { owner ->
                    auxiliaryStore.addReviewPointFromText(
                        taskId = owner.id,
                        taskTitle = owner.displayName,
                        content = entry.learned,
                        today = activeDate,
                    )
                }
                auxRevision++
                undoScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.kwl_saved),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            dueReviewPoints = remember(auxRevision, activeDate) { auxiliaryStore.dueReviewPoints(activeDate) },
            upcomingReviewCount = remember(auxRevision, activeDate) {
                auxiliaryStore.reviewStreakSummary(activeDate).second
            },
            onGradeReviewPoint = { id, remembered ->
                auxiliaryStore.gradeReviewPoint(id, remembered, activeDate)
                auxRevision++
            },
            explanationSlots = remember(uiState.tasks, auxRevision) {
                uiState.tasks
                    .filter { it.status == TaskStatus.COMPLETED }
                    .sortedByDescending { it.updatedAtEpochMillis }
                    .take(5)
                    .map { task ->
                        ExplanationSlot(
                            logKey = task.id,
                            taskTitle = task.displayName,
                            clue = task.completionResult?.takeIf(String::isNotBlank)
                                ?: task.completionSummary?.takeIf(String::isNotBlank),
                            existing = auxiliaryStore.selfExplanation(task.id),
                        )
                    }
            },
            onSaveExplanation = { slot, text ->
                auxiliaryStore.saveSelfExplanation(slot.logKey, text)
                // The sentence is also what seeds the spaced-repetition queue. Without this
                // line the review points would never fire: one-tap completion records no
                // completionResult, so addReviewPointsFor has nothing to mine.
                auxiliaryStore.addReviewPointFromText(slot.logKey, slot.taskTitle, text, activeDate)
                auxRevision++
                undoScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.self_explanation_saved),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            planSegmentCount = planUiState.currentPlan?.segments?.size ?: 0,
            timetableBlockCount = timeUiState.weeklyBlocks.size,
            imageMessage = imageExportMessage,
            onExportImagePlan = {
                val plan = planUiState.currentPlan
                if (plan != null) {
                    pendingImage = PlanImageExporter.renderPlan(
                        date = activeDate,
                        segments = plan.segments.filter { it.date == activeDate },
                        tasks = uiState.tasks,
                        texts = planImageTexts,
                    )
                    imageExportLauncher.launch(PlanImageExporter.fileName(activeDate))
                }
            },
            onExportImageTimetable = {
                pendingImage = PlanImageExporter.renderTimetable(timeUiState.weeklyBlocks, planImageTexts)
                imageExportLauncher.launch(PlanImageExporter.fileName(null))
            },
            timetableSetRows = remember(auxRevision, timeUiState.weeklyBlocks) {
                auxiliaryStore.timetableSets().map { set ->
                    TimetableSetRow(
                        name = set.name,
                        blockCount = auxiliaryStore.timetableBlockCount(set.blocksJson),
                        // Content, not a remembered name: once the user hand-edits a block or
                        // imports one extra course, no saved set describes the live timetable
                        // any more, and the list should stop claiming otherwise.
                        active = auxiliaryStore.timetableMatches(
                            set.blocksJson,
                            timeUiState.weeklyBlocks,
                        ),
                    )
                }
            },
            timetableSetMessage = timetableSetMessage,
            onSaveTimetableSet = { name ->
                auxiliaryStore.saveTimetableSet(
                    name,
                    auxiliaryStore.encodeTimetable(timeUiState.weeklyBlocks),
                )
                timetableSetMessage = context.getString(R.string.timetable_set_saved)
                auxRevision++
            },
            onActivateTimetableSet = { name ->
                val target = auxiliaryStore.timetableSets().firstOrNull { it.name == name }
                if (target != null) {
                    val drafts = auxiliaryStore.decodeTimetable(target.blocksJson)
                    // D5. `join` on the write is what makes the "after" snapshot real. The switch
                    // is a delete-all-then-insert-all pass that returns immediately, so capturing
                    // without waiting would read the arrangement mid-pass and record an entry
                    // whose two ends are the same state — an undo that visibly does nothing.
                    runReversible(context.getString(R.string.undo_step_switch_set, name)) {
                        timeViewModel.replaceTimetable(drafts).join()
                    }
                    timetableSetMessage = context.getString(R.string.timetable_set_switched, name)
                    auxRevision++
                }
            },
            onDeleteTimetableSet = { name -> deletingTimetableSet = name },
            onClose = { activeToolName = null },
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                if (selectedTab == AppTab.TIME) "导入课表" else stringResource(selectedTab.titleRes),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            if (selectedTab == AppTab.TODAY) {
                                val dateFormatter =
                                    rememberDateFormatter(R.string.date_pattern_month_day_short_weekday)
                                Text(
                                    text = dateFormatter.format(activeDate),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        // TIME and AGENT are secondary screens: they are not tabs, so they
                        // need an explicit way back to whichever primary tab opened them.
                        if (!selectedTab.primary) {
                            IconButton(
                                onClick = { selectedTab = secondaryReturnTab },
                                modifier = Modifier.testTag("secondary-back"),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.back_to_primary),
                                )
                            }
                        }
                    },
                    actions = {
                        if (selectedTab == AppTab.PLAN) {
                            TextButton(
                                onClick = { showVoiceComposer = true; voiceError = false },
                                modifier = Modifier.testTag("voice-capture"),
                            ) { Text("语音添加") }
                        }
                        // The single always-present entry point to every feature in the app.
                        IconButton(
                            onClick = { showCommandPalette = true },
                            modifier = Modifier.testTag("open-command-palette"),
                        ) {
                            Icon(
                                PlannerIcons.Schedule,
                                contentDescription = stringResource(R.string.command_open_palette),
                            )
                        }
                    },
                )
            },
            // D5. Scaffold owns the snackbar so it sits above the navigation bar rather than
            // behind it, and so an undo offer survives the dialog that triggered it closing.
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                // Three persistent tabs only. Everything else lives behind the palette.
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                ) {
                    AppTab.entries.filter { it.primary }.forEach { tab ->
                        DesignNavigationItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            modifier = Modifier.testTag("navigation-${tab.name.lowercase()}"),
                            icon = { TabIcon(tab) },
                            label = { Text(stringResource(tab.shortRes)) },
                            accentIndex = when (tab) {
                                AppTab.TODAY -> 0
                                AppTab.PLAN -> 1
                                AppTab.MINE -> 4
                                else -> 0
                            },
                        )
                    }
                }
            },
            floatingActionButton = {
                when (selectedTab) {
                    AppTab.PLAN -> {
                        // Hidden mid-selection: the FAB sits in the corner the action bar's last
                        // button occupies, and "add a task" is not a thing to do to a batch.
                        if (taskSelection == null) {
                            FloatingActionButton(
                                onClick = {
                                    showQuickArrange = true
                                },
                                modifier = Modifier.testTag("add-task"),
                                shape = RoundedCornerShape(20.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary,
                            ) { Icon(Icons.Outlined.Add, contentDescription = "添加任务") }
                        }
                    }

                    AppTab.TODAY -> {
                        FloatingActionButton(
                            onClick = { showQuickArrange = true },
                            modifier = Modifier.testTag("today-quick-arrange"),
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ) { Icon(PlannerIcons.Chat, contentDescription = "一句话安排") }
                    }

                    AppTab.AGENT -> Unit

                    AppTab.TIME -> Unit

                    AppTab.MINE -> Unit
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // Same edge-to-edge reason as in ToolScaffold: without this the IME overlays
                    // the bottom of the shell, and the tab content underneath it cannot be
                    // reached because the outer box does not scroll.
                    .imePadding(),
            ) {
                when (selectedTab) {
                    AppTab.TODAY -> TodayScreen(
                        now = currentMinute,
                        date = activeDate,
                        tasks = uiState.tasks.filter(Task::isTodayRelevant),
                        allTasks = uiState.tasks,
                        weeklyBlocks = timeUiState.weeklyBlocks,
                        dateOverrides = timeUiState.dateOverrides,
                        semesterFirstWeekMonday = timeUiState.semesterFirstWeekMonday,
                        engagementMode = engagementMode,
                        focusMinutes = focusMinutes,
                        onFocusMinutesChange = onFocusMinutesChange,
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
                        // A conflict row already holds the block, so this path skips the entry-id
                        // round trip the timeline rows need and opens the editor directly.
                        onEditWeeklyBlock = { block ->
                            weeklyBlockEditorTarget = block
                            showWeeklyBlockEditor = true
                        },
                        // Without this the two delete dialogs further down had no caller at all: a
                        // course block could be created, moved and edited from the timeline, but
                        // never removed, so the confirm dialog and the undo entry behind it were
                        // unreachable code.
                        onDeleteTimelineEntry = { entry ->
                            when {
                                entry.id.startsWith("weekly:") -> {
                                    deletingWeeklyBlock = timeUiState.weeklyBlocks.firstOrNull {
                                        it.id == entry.id.removePrefix("weekly:")
                                    }
                                }

                                entry.id.startsWith("override:") -> {
                                    deletingDateOverride = timeUiState.dateOverrides.firstOrNull {
                                        it.id == entry.id.removePrefix("override:")
                                    }
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
                        onOpenPlan = {
                            secondaryReturnTab = selectedTab
                            selectedTab = AppTab.AGENT
                        },
                        onPlaceEvent = planViewModel::placeTask,
                        onFocusStarted = taskViewModel::startTask,
                        onCommitFocusRound = { taskId, minutes, completeTask ->
                            val saved = taskViewModel.commitFocusRound(taskId, minutes, completeTask)
                            if (saved && completeTask) uiState.tasks.firstOrNull { it.id == taskId }?.let { task ->
                                auxiliaryStore.addReviewPointsFor(task, today = activeDate)
                            }
                            saved
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
                        toolSuggestions = toolSuggestions,
                        onOpenTool = { tool -> activeToolName = tool.name },
                    )

                    AppTab.PLAN -> TasksScreen(
                        tasks = uiState.tasks,
                        isLoading = uiState.isLoading,
                        selection = taskSelection,
                        onSelectionChange = { taskSelection = it },
                        onBatchAction = { action -> pendingBatchAction = action },
                        onOpen = { selectedTaskId = it.id },
                        onStart = taskViewModel::startTask,
                        onAdd = {
                            showTaskCapture = true
                        },
                    )

                    AppTab.AGENT -> AgentCenterScreen(
                        activeDate = activeDate,
                        occupiedEntries = agentTimelineEntries,
                        canRefineWithAi = arrangementAssistantAccess.isEnabled &&
                            arrangementAssistantAccess.hasExplicitConsent && aiProviderConfigUiState.config.hasApiKey,
                        onRefineWithAi = arrangementAssistantViewModel::refine,
                        onCommitArrangement = taskViewModel::commitArrangement,
                        commitFailureMessage = uiState.arrangementFailureMessage,
                        onOpenTimeStudio = {
                            secondaryReturnTab = AppTab.AGENT
                            selectedTab = AppTab.TIME
                        },
                    )

                    AppTab.TIME -> TimeScreen(
                        timetableImport = timeUiState.timetableImport,
                        // `TimeScreen` keeps reporting "the user picked this Uri"; turning it into
                        // something readable is the importer's business, and happens here where a
                        // Context is already in hand.
                        onImportPdf = { uri -> timeViewModel.readTimetable(context.timetableSource(uri)) },
                        onImportSpreadsheet = { uri -> timeViewModel.readSpreadsheet(context.timetableSource(uri)) },
                        onImportCalendar = { uri ->
                            timeViewModel.importCalendar(
                                source = context.timetableSource(uri),
                                // The placeholder for an event with no SUMMARY is language, so it is
                                // resolved here, where the resources are reachable, rather than
                                // hard-coded inside the pure mapping rule.
                                fallbackTitle = context.getString(R.string.calendar_import_untitled),
                            )
                        },
                        onPasteTimetable = {
                            // An empty clipboard flows through the same Failed state as a
                            // bad file, so there is one place that reports import trouble.
                            timeViewModel.readTimetableFromClipboard(
                                clipboardManager.getText()?.text.orEmpty(),
                            )
                        },
                        onClearTimetableImport = timeViewModel::clearTimetableImport,
                    )
                    AppTab.MINE -> MineScreen(
                        weights = categoryPreferenceUiState.weights,
                        defaultName = stringResource(R.string.profile_default_name),
                        profileEvidence = profileEvidenceWithOutputs,
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
                        aiProviderConfigError = aiProviderConfigUiState.errorMessage,
                        aiProviderConnectionTest = aiProviderConfigUiState.connectionTest,
                        aiProviderModelList = aiProviderConfigUiState.modelList,
                        onSaveAiProviderConfig = aiProviderConfigViewModel::save,
                        onClearAiProviderKey = aiProviderConfigViewModel::clearApiKey,
                        onTestAiProviderConnection = aiProviderConfigViewModel::testConnection,
                        onRefreshAiProviderModels = aiProviderConfigViewModel::refreshModels,
                        engagementMode = engagementMode,
                        onEngagementModeChange = engagementViewModel::setMode,
                    )
                }
            }
        }
    }

    if (showCommandPalette) {
        // Built lazily: the palette is the only consumer, so there is no reason to
        // allocate ~40 entries on every recomposition of the whole app shell.
        val goTo: (AppTab) -> Unit = { tab ->
            if (!tab.primary) secondaryReturnTab = selectedTab
            selectedTab = tab
        }
        // D5. Resolved out here rather than inside the entry: the subtitle has to say *what* would
        // move, because "撤销" on its own asks the user to remember what they last did.
        val undoSubtitle = undoStore.undoLabel
            ?.let { stringResource(R.string.undo_palette_will_undo, it) }
            ?: stringResource(R.string.undo_palette_empty_undo)
        val redoSubtitle = undoStore.redoLabel
            ?.let { stringResource(R.string.undo_palette_will_redo, it) }
            ?: stringResource(R.string.undo_palette_empty_redo)
        CommandPaletteSheet(
            entries = listOf(
                CommandEntry(
                    id = "nav-today",
                    title = stringResource(R.string.today_title),
                    subtitle = rememberDateFormatter(R.string.date_pattern_month_day_short_weekday).format(activeDate),
                    keywords = listOf("today", "jintian", "home"),
                    group = CommandGroup.NAVIGATE,
                    icon = Icons.Outlined.DateRange,
                    onSelect = { goTo(AppTab.TODAY) },
                ),
                CommandEntry(
                    id = "nav-plan",
                    title = stringResource(R.string.plan_tab_title),
                    subtitle = "任务库与当前计划",
                    keywords = listOf("plan", "jihua", "task", "renwu"),
                    group = CommandGroup.NAVIGATE,
                    icon = Icons.AutoMirrored.Outlined.List,
                    onSelect = { goTo(AppTab.PLAN) },
                ),
                CommandEntry(
                    id = "nav-agent",
                    title = stringResource(R.string.agent_title),
                    subtitle = stringResource(R.string.ai_advisor_description),
                    keywords = listOf("ai", "agent", "zhushou", "assistant"),
                    group = CommandGroup.NAVIGATE,
                    icon = Icons.Outlined.Add,
                    onSelect = { goTo(AppTab.AGENT) },
                ),
                CommandEntry(
                    id = "nav-time",
                    title = stringResource(R.string.time_title),
                    subtitle = "固定时段、学期周、课表导入",
                    keywords = listOf("time", "shijian", "timetable", "kebiao", "schedule"),
                    group = CommandGroup.NAVIGATE,
                    icon = PlannerIcons.Calendar,
                    onSelect = { goTo(AppTab.TIME) },
                ),
                CommandEntry(
                    id = "nav-mine",
                    title = stringResource(R.string.mine_title),
                    subtitle = "偏好、画像、提醒、数据",
                    keywords = listOf("mine", "wode", "settings", "shezhi"),
                    group = CommandGroup.NAVIGATE,
                    icon = Icons.Outlined.Person,
                    onSelect = { goTo(AppTab.MINE) },
                ),

                CommandEntry(
                    id = "task-new",
                    title = stringResource(R.string.add_task),
                    subtitle = stringResource(R.string.task_name_hint),
                    keywords = listOf("new", "add", "xinjian", "renwu"),
                    group = CommandGroup.TASK,
                    icon = Icons.Outlined.Add,
                    onSelect = { taskEditorSeed = ""; showTaskCapture = true },
                ),
                CommandEntry(
                    id = "task-voice",
                    title = "语音添加任务",
                    subtitle = "说一句话，转成任务",
                    keywords = listOf("voice", "yuyin", "speech"),
                    group = CommandGroup.TASK,
                    icon = PlannerIcons.Voice,
                    onSelect = { voiceError = false; showVoiceComposer = true },
                ),
                CommandEntry(
                    id = "task-ai-advice",
                    title = stringResource(R.string.request_agent_daily_summary),
                    subtitle = stringResource(R.string.ai_advisor_description),
                    keywords = listOf("ai", "summary", "zongjie"),
                    group = CommandGroup.TASK,
                    icon = Icons.Outlined.Add,
                    enabled = planningAgentUiState.preferences.isEnabled,
                    onSelect = { goTo(AppTab.AGENT) },
                ),

                CommandEntry(
                    id = "plan-overview",
                    title = stringResource(R.string.plan_overview_title),
                    subtitle = "当前计划与历史版本",
                    keywords = listOf("overview", "zonglan", "history", "lishi"),
                    group = CommandGroup.PLAN,
                    icon = PlannerIcons.Schedule,
                    onSelect = { showPlanOverview = true },
                ),
                CommandEntry(
                    id = "plan-generate",
                    title = "生成计划草稿",
                    subtitle = stringResource(R.string.plan_draft_notice),
                    keywords = listOf("draft", "caogao", "generate", "shengcheng"),
                    group = CommandGroup.PLAN,
                    icon = PlannerIcons.Schedule,
                    onSelect = {
                        secondaryReturnTab = selectedTab
                        selectedTab = AppTab.TIME
                        if (planUiState.draft == null) generatePlanDraft()
                    },
                ),
                CommandEntry(
                    id = "plan-clear",
                    title = stringResource(R.string.clear_current_plan),
                    keywords = listOf("clear", "qingkong", "reset"),
                    group = CommandGroup.PLAN,
                    icon = Icons.Outlined.Warning,
                    onSelect = { showClearPlanConfirmation = true },
                ),

                CommandEntry(
                    id = "time-blocks",
                    title = stringResource(R.string.edit_weekly_block),
                    subtitle = "每周固定占用与可用时段",
                    keywords = listOf("weekly", "guding", "shiduan"),
                    group = CommandGroup.TIME,
                    icon = PlannerIcons.Calendar,
                    onSelect = { goTo(AppTab.TIME) },
                ),
                CommandEntry(
                    id = "time-semester",
                    title = stringResource(R.string.set_semester_week),
                    subtitle = stringResource(R.string.semester_week_editor_hint),
                    keywords = listOf("semester", "xueqi", "week", "zhouci"),
                    group = CommandGroup.TIME,
                    icon = PlannerIcons.Calendar,
                    onSelect = { showSemesterStartEditor = true },
                ),
                CommandEntry(
                    id = "time-import-pdf",
                    title = stringResource(R.string.import_timetable_pdf),
                    subtitle = stringResource(R.string.import_timetable_hint),
                    keywords = listOf("pdf", "import", "daoru", "kebiao"),
                    group = CommandGroup.TIME,
                    icon = PlannerIcons.Calendar,
                    onSelect = { goTo(AppTab.TIME) },
                ),

                CommandEntry(
                    id = "review-daily",
                    title = stringResource(R.string.open_daily_review),
                    subtitle = stringResource(R.string.daily_review_notice),
                    keywords = listOf("review", "fansi", "daily", "daily review"),
                    group = CommandGroup.REVIEW,
                    icon = Icons.Outlined.CheckCircle,
                    onSelect = { showDailyReview = true },
                ),

                CommandEntry(
                    id = "data-export-json",
                    title = stringResource(R.string.export_local_json),
                    subtitle = stringResource(R.string.local_data_intro),
                    keywords = listOf("export", "daochu", "json", "backup"),
                    group = CommandGroup.DATA,
                    icon = Icons.AutoMirrored.Outlined.ArrowForward,
                    onSelect = {
                        localDataExportLauncher.launch("repland-local-export-${LocalDate.now()}.json")
                    },
                ),
                CommandEntry(
                    id = "data-export-xlsx",
                    title = stringResource(R.string.export_local_xlsx),
                    subtitle = stringResource(R.string.export_local_xlsx_hint),
                    keywords = listOf(
                        "export", "daochu", "xlsx", "excel", "biaoge", "spreadsheet",
                        "timetable", "kebiao", "table",
                    ),
                    group = CommandGroup.DATA,
                    icon = Icons.Outlined.DateRange,
                    onSelect = {
                        spreadsheetExportLauncher.launch(XlsxExporter.fileName(LocalDate.now()))
                    },
                ),
                CommandEntry(
                    id = "data-clear",
                    title = stringResource(R.string.clear_local_data),
                    keywords = listOf("clear", "delete", "qingkong", "shuju"),
                    group = CommandGroup.DATA,
                    icon = Icons.Outlined.Warning,
                    onSelect = { showClearLocalDataConfirmation = true },
                ),
                CommandEntry(
                    id = "data-ics",
                    title = stringResource(R.string.export_ics),
                    subtitle = stringResource(R.string.export_ics_today),
                    keywords = listOf("ics", "calendar", "rili", "export", "daochu"),
                    group = CommandGroup.DATA,
                    icon = PlannerIcons.Calendar,
                    onSelect = { activeToolName = ToolId.ICS_EXPORT.name },
                ),

                CommandEntry(
                    id = "review-reflection",
                    title = stringResource(R.string.reflection_title),
                    subtitle = "草稿已写好，改一改或直接确认",
                    keywords = listOf("reflection", "fansi", "journal", "rizhi"),
                    group = CommandGroup.REVIEW,
                    icon = Icons.Outlined.CheckCircle,
                    onSelect = { activeToolName = ToolId.REFLECTION.name },
                ),
                CommandEntry(
                    id = "review-points",
                    title = stringResource(R.string.review_points),
                    subtitle = "按 1/2/4/7/15 天间隔回看",
                    keywords = listOf("review", "fuxi", "spaced", "ebbinghaus", "jiange"),
                    group = CommandGroup.REVIEW,
                    icon = Icons.Outlined.CheckCircle,
                    onSelect = { activeToolName = ToolId.REVIEW_POINTS.name },
                ),
                // Deliberately always enabled. The page itself states what each chart is waiting
                // for, which is more useful than a greyed-out row that cannot say why — and a
                // disabled entry would need this list to recompute the insight arithmetic, so the
                // rule would live in two places and drift.
                CommandEntry(
                    id = "review-insights",
                    title = stringResource(R.string.insights_title),
                    subtitle = "预估准不准、投入趋势、分类占比、复习节奏",
                    keywords = listOf(
                        "insights", "dongcha", "chart", "tubiao", "trend", "qushi",
                        "calibration", "jiaozhun", "stats", "shuju", "report",
                    ),
                    group = CommandGroup.REVIEW,
                    icon = PlannerIcons.Insights,
                    onSelect = { activeToolName = ToolId.INSIGHTS.name },
                ),

                CommandEntry(
                    id = "method-kwl",
                    title = stringResource(R.string.kwl_title),
                    subtitle = stringResource(R.string.kwl_generated),
                    keywords = listOf("kwl", "known", "want", "learned"),
                    group = CommandGroup.METHOD,
                    icon = Icons.Outlined.Add,
                    onSelect = { activeToolName = ToolId.KWL.name },
                ),
                CommandEntry(
                    id = "method-sq3r",
                    title = stringResource(R.string.sq3r_title),
                    subtitle = stringResource(R.string.sq3r_hint),
                    keywords = listOf("sq3r", "reading", "yuedu", "scaffold", "jiaoshoujia"),
                    group = CommandGroup.METHOD,
                    icon = PlannerIcons.Schedule,
                    onSelect = { activeToolName = ToolId.SQ3R.name },
                ),

                CommandEntry(
                    id = "appearance-mode",
                    title = stringResource(R.string.appearance_title),
                    // Both axes are shown, because "外观" now answers two questions and a subtitle
                    // that named only the mode would hide the other one from the palette.
                    subtitle = listOf(
                        when (appearanceMode) {
                            AppearanceMode.SYSTEM -> stringResource(R.string.appearance_mode_system)
                            AppearanceMode.LIGHT -> stringResource(R.string.appearance_mode_light)
                            AppearanceMode.DARK -> stringResource(R.string.appearance_mode_dark)
                        },
                        stringResource(accentLabelRes(themeAccent)),
                    ).joinToString(" · "),
                    keywords = listOf(
                        "theme", "dark", "shense", "zhuti", "appearance", "wai guan",
                        "accent", "color", "peise", "yanse",
                    ),
                    group = CommandGroup.APPEARANCE,
                    icon = PlannerIcons.Schedule,
                    onSelect = { activeToolName = ToolId.APPEARANCE.name },
                ),
                CommandEntry(
                    id = "method-explain",
                    title = stringResource(R.string.self_explanation_title),
                    subtitle = stringResource(R.string.self_explanation_prompt),
                    keywords = listOf("explain", "feynman", "ziwo jieshi", "jiang qingchu", "self"),
                    group = CommandGroup.METHOD,
                    icon = Icons.Outlined.CheckCircle,
                    enabled = uiState.tasks.any { it.status == TaskStatus.COMPLETED },
                    onSelect = { activeToolName = ToolId.SELF_EXPLANATION.name },
                ),
                CommandEntry(
                    id = "data-image",
                    title = stringResource(R.string.export_image),
                    subtitle = stringResource(R.string.export_image_hint),
                    keywords = listOf("image", "png", "tupian", "daochu", "share", "screenshot"),
                    group = CommandGroup.DATA,
                    icon = Icons.Outlined.DateRange,
                    onSelect = { activeToolName = ToolId.IMAGE_EXPORT.name },
                ),
                CommandEntry(
                    id = "time-sets",
                    title = stringResource(R.string.timetable_sets),
                    subtitle = stringResource(R.string.timetable_set_switch_hint),
                    keywords = listOf("timetable", "sets", "kebiao", "qiehuan", "switcher"),
                    group = CommandGroup.TIME,
                    icon = PlannerIcons.Calendar,
                    onSelect = { activeToolName = ToolId.TIMETABLE_SETS.name },
                ),
                // D5. These two sit at the end of the list on purpose: the palette groups by
                // `CommandGroup` and the top of the NAVIGATE group is what muscle memory reaches
                // for, so undo must not displace "今天" as the first row.
                CommandEntry(
                    id = "undo-step",
                    title = stringResource(R.string.undo_palette_undo),
                    subtitle = undoSubtitle,
                    keywords = listOf("undo", "chexiao", "huitui", "back", "shangyibu"),
                    group = CommandGroup.TIME,
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    enabled = undoStore.canUndo,
                    onSelect = {
                        showCommandPalette = false
                        applyHistory(isRedo = false)
                    },
                ),
                CommandEntry(
                    id = "redo-step",
                    title = stringResource(R.string.undo_palette_redo),
                    subtitle = redoSubtitle,
                    keywords = listOf("redo", "chongzuo", "huifu", "xiayibu"),
                    group = CommandGroup.TIME,
                    icon = Icons.AutoMirrored.Outlined.ArrowForward,
                    enabled = undoStore.canRedo,
                    onSelect = {
                        showCommandPalette = false
                        applyHistory(isRedo = true)
                    },
                ),
            ),
            onDismiss = { showCommandPalette = false },
        )
    }

    // A tool is a destination, not a dialog: the system back gesture must close the tool
    // rather than leaving the app, otherwise back would skip a level of the hierarchy.
    BackHandler(enabled = activeTool != null) { activeToolName = null }
    BackHandler(enabled = activeTool == null && selectedTaskId != null) { selectedTaskId = null }
    BackHandler(enabled = activeTool == null && selectedTaskId == null && !selectedTab.primary) {
        selectedTab = secondaryReturnTab
    }

    if (showTaskCapture) {
        TaskCaptureSheet(
            initialText = taskEditorSeed,
            onDismiss = { showTaskCapture = false; taskEditorSeed = "" },
            onSave = {
                taskViewModel.saveTask(it)
                showTaskCapture = false
                taskEditorSeed = ""
            },
        )
    }

    if (showQuickArrange) {
        GroupChatSheet(
            activeDate = activeDate,
            occupiedEntries = agentTimelineEntries,
            canRefineWithAi = arrangementAssistantAccess.isEnabled &&
                arrangementAssistantAccess.hasExplicitConsent && aiProviderConfigUiState.config.hasApiKey,
            agentChat = agentChatModel,
            profileEvidence = profileEvidenceUiState.evidence,
            categoryPreferences = categoryPreferenceUiState.weights,
            onRefineWithAi = arrangementAssistantViewModel::refine,
            onCommitArrangement = taskViewModel::commitArrangement,
            commitFailureMessage = uiState.arrangementFailureMessage,
            onDismiss = { showQuickArrange = false },
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

    // The count shown here is computed the same way the write is, from the list as it is now — so
    // the number the user agrees to is the number of rows that will actually change. `targets` can
    // only be smaller than the selection (it filters, it never adds), and the difference is named
    // rather than silently dropped: a 恢复 over a view that also lists replaced tasks is the normal
    // case where the two numbers differ, and the user should not have to notice it afterwards.
    pendingBatchAction?.let { action ->
        val selection = taskSelection ?: TaskSelection.EMPTY
        val targets = BatchTasks.targets(action, selection, uiState.tasks)
        BatchConfirmDialog(
            action = action,
            targetCount = targets.size,
            skippedCount = selection.size - targets.size,
            onConfirm = {
                taskViewModel.confirmStatuses(action, selection)
                pendingBatchAction = null
                taskSelection = null
            },
            onDismiss = { pendingBatchAction = null },
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
                        PlanningAgentRequestType.TASK_UNDERSTANDING -> planningAgentViewModel.beginTaskUnderstanding(it, executionLogs, planUiState.currentPlan, prompt)
                        PlanningAgentRequestType.DIFFICULTY_AND_DURATION -> planningAgentViewModel.beginDifficultyAndDuration(it, executionLogs, planUiState.currentPlan, prompt)
                        PlanningAgentRequestType.TASK_BREAKDOWN -> planningAgentViewModel.beginTaskBreakdown(it, executionLogs, planUiState.currentPlan, prompt)
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
                            userMessage = prompt,
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
                // D5. Deleting a block is the most destructive thing this screen does and the
                // hardest to notice afterwards — the row simply stops existing. `join` so the
                // snapshot brackets the real write.
                runReversible(context.getString(R.string.undo_step_delete_block, block.title)) {
                    timeViewModel.deleteWeeklyBlock(block.id).join()
                }
                deletingWeeklyBlock = null
            },
        )
    }

    deletingDateOverride?.let { dateOverride ->
        DeleteTimeEntryDialog(
            title = dateOverride.title,
            onDismiss = { deletingDateOverride = null },
            onConfirm = {
                // D5. Same as the weekly block above.
                runReversible(context.getString(R.string.undo_step_delete_override, dateOverride.title)) {
                    timeViewModel.deleteDateOverride(dateOverride.id).join()
                }
                deletingDateOverride = null
            },
        )
    }

    deletingTimetableSet?.let { name ->
        DeleteTimeEntryDialog(
            title = name,
            dialogTitle = stringResource(R.string.timetable_set_delete_title),
            dialogMessage = stringResource(R.string.timetable_set_delete_message, name),
            onDismiss = { deletingTimetableSet = null },
            onConfirm = {
                // Destructive and not covered by undo: deleting a set wipes the saved arrangement
                // with no in-app way back. A confirm step is the only guard, and a "deleted"
                // line is the only signal that anything happened — both were missing before.
                auxiliaryStore.deleteTimetableSet(name)
                timetableSetMessage = context.getString(R.string.timetable_set_deleted, name)
                auxRevision++
                deletingTimetableSet = null
            },
        )
    }

    planUiState.draft?.let { draft ->
        PlanDraftDialog(
            draft = draft,
            tasks = uiState.tasks,
            weeklyBlocks = timeUiState.weeklyBlocks,
            dateOverrides = timeUiState.dateOverrides,
            semesterFirstWeekMonday = timeUiState.semesterFirstWeekMonday,
            failureMessage = planUiState.failureMessage,
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
                        planningAgentViewModel.dismiss()
                        aiProviderConfigViewModel.resetTransientState()
                        taskViewModel.cancelPendingMutations()
                        planViewModel.cancelPendingMutations()
                        timeViewModel.cancelPendingMutations()
                        profileEvidenceViewModel.cancelPendingMutations()
                        categoryPreferenceViewModel.cancelPendingMutations()
                        reminderSettingsViewModel.cancelPendingMutations()
                        engagementViewModel.cancelPendingMutations()
                        undoScope.coroutineContext.cancelChildren()
                        com.swan1127.repland.ui.agent.clearGroupChatSession()
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
    if (dataManagementUiState.isWorking) Dialog(onDismissRequest = {}) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator()
                Text("正在处理本地数据…")
            }
        }
    }
    }
}

@Composable
private fun TabIcon(tab: AppTab) {
    val icon = when (tab) {
        AppTab.TODAY -> Icons.Outlined.DateRange
        AppTab.PLAN -> Icons.AutoMirrored.Outlined.List
        AppTab.AGENT -> Icons.Outlined.Add
        AppTab.TIME -> Icons.Outlined.DateRange
        AppTab.MINE -> Icons.Outlined.Person
    }
    Icon(icon, contentDescription = null)
}

@Composable
private fun TodayScreen(
    now: LocalDateTime,
    date: LocalDate,
    tasks: List<Task>,
    allTasks: List<Task>,
    weeklyBlocks: List<WeeklyTimeBlock>,
    dateOverrides: List<DateOverride>,
    semesterFirstWeekMonday: LocalDate?,
    engagementMode: EngagementMode,
    focusMinutes: Int,
    onFocusMinutesChange: (Int) -> Unit,
    onOpenTimelineEntry: (String) -> Unit,
    onEditTimelineEntry: (TimelineEntry) -> Unit,
    /** Opens the weekly-block editor directly, for callers that already hold the block. */
    onEditWeeklyBlock: (WeeklyTimeBlock) -> Unit,
    onDeleteTimelineEntry: (TimelineEntry) -> Unit,
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
    onPlaceEvent: (String, LocalDate, Int, Int, String) -> Unit,
    onFocusStarted: (String) -> Unit,
    onCommitFocusRound: suspend (String, Int, Boolean) -> Boolean,
    onCreateCourse: (com.swan1127.repland.ui.schedule.CourseInsertionRequest, LocalDate) -> Unit,
    onRemovePlacement: (String) -> Unit,
    onMoveEntry: (TimelineEntry, Int) -> Unit,
    onUpdateEntryTime: (TimelineEntry, Int, Int) -> Unit,
    toolSuggestions: List<ToolSuggestion>,
    onOpenTool: (ToolId) -> Unit,
) {
    if (isLoading) return
    var scheduleRange by rememberSaveable { mutableStateOf(ScheduleRange.DAY) }
    var scheduleDate by remember { mutableStateOf(date) }
    LaunchedEffect(date) { scheduleDate = date }
    val taskById = allTasks.associateBy(Task::id)
    val timelineEntries = ScheduleTimeline.entries(scheduleDate, weeklyBlocks, dateOverrides, allPlanSegments, allTasks, semesterFirstWeekMonday)
    val placedTaskIdsForScheduleDate = allPlanSegments.filter { it.date == scheduleDate }.map(PlannedSegment::taskId).toSet()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (scheduleRange == ScheduleRange.DAY) {
            item {
                TodayFocalOverview(
                    // The whole day, unfiltered. The filter that used to be here removed exactly
                    // the blocks `ScheduleTimeline.clock` needs in order to say 已超时, which is
                    // why that branch was dead — see `ui/schedule/TimelineFocus.kt`.
                    state = focalCard(timelineEntries, now),
                    unplacedCount = allTasks.count { it.id !in placedTaskIdsForScheduleDate && it.status.isActive },
                    onOpenSchedule = { scheduleRange = ScheduleRange.DAY },
                    onOpenTasks = onOpenPlan,
                )
            }
            // Directly under the focal block, because that is the one card the user is already
            // reading. The metacognition tools have no competitor coverage at all, and until now
            // their only entry point was the command palette — a gesture you have to already know
            // to look for. The strip is situational, so it is usually absent, and it renders
            // nothing at all when no rule fires.
            item {
                ToolSuggestionStrip(
                    suggestions = toolSuggestions,
                    onOpenTool = onOpenTool,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesignFilterChip(selected = scheduleRange == ScheduleRange.DAY, onClick = { scheduleRange = ScheduleRange.DAY }, label = { Text("日") }, modifier = Modifier.testTag("today-timeline"), accentIndex = 0)
                DesignFilterChip(selected = scheduleRange == ScheduleRange.WEEK, onClick = { scheduleRange = ScheduleRange.WEEK }, label = { Text("周") }, modifier = Modifier.testTag("week-schedule"), accentIndex = 1)
                DesignFilterChip(selected = scheduleRange == ScheduleRange.MONTH, onClick = { scheduleRange = ScheduleRange.MONTH }, label = { Text("月") }, modifier = Modifier.testTag("month-schedule"), accentIndex = 2)
            }
        }
        // A `when` over the range with no `else`, not an `if / else if` chain ending in one. The
        // chain used to end in `else { DailyDesk(...) }`, and that is exactly how a whole screen
        // became unreachable: `ScheduleRange.OVERVIEW` was never assigned by any chip, so the
        // `else` could only be reached by a value nothing produced — and the range-inequality guard
        // further down (a `!=` against that same unassigned member) held for every reachable value.
        // Enumerating the three ranges exhaustively means a fourth cannot be added without a
        // compile error, instead of silently falling through to whatever the `else` held.
        when (scheduleRange) {
            ScheduleRange.DAY -> item {
                TimelineDashboard(
                    entries = timelineEntries,
                    mode = engagementMode,
                    focusMinutes = focusMinutes,
                    onFocusMinutesChange = onFocusMinutesChange,
                    onOpenEntry = onOpenTimelineEntry,
                    onOpenTask = { id -> taskById[id]?.let(onOpen) },
                    onEditEntry = onEditTimelineEntry,
                    onDeleteEntry = onDeleteTimelineEntry,
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
                    onCommitFocusRound = onCommitFocusRound,
                    onRemovePlacement = onRemovePlacement,
                    onMoveEntry = onMoveEntry,
                    onUpdateEntryTime = onUpdateEntryTime,
                )
            }
            ScheduleRange.WEEK -> item {
                WeekScheduleView(
                    date = scheduleDate,
                    weeklyBlocks = weeklyBlocks,
                    dateOverrides = dateOverrides,
                    planSegments = allPlanSegments,
                    tasks = allTasks,
                    semesterFirstWeekMonday = semesterFirstWeekMonday,
                    mode = engagementMode,
                    onOpenEntry = onOpenTimelineEntry,
                    onOpenBlock = onEditWeeklyBlock,
                )
            }
            ScheduleRange.MONTH -> item {
                MonthScheduleView(
                    date = scheduleDate,
                    weeklyBlocks = weeklyBlocks,
                    dateOverrides = dateOverrides,
                    planSegments = allPlanSegments,
                    tasks = allTasks,
                    semesterFirstWeekMonday = semesterFirstWeekMonday,
                    onSelectDate = { selectedDate -> scheduleDate = selectedDate; scheduleRange = ScheduleRange.DAY },
                    onOpenEntry = onOpenTimelineEntry,
                )
            }
        }
        // Unconditional: the guard that used to wrap this was a range inequality against the member
        // nothing could assign, so it held for every value the app could hold. The daily review
        // belongs under all three ranges, so it is no longer conditional on anything.
        item { TextButton(onClick = onOpenDailyReview) { Text(stringResource(R.string.open_daily_review)) } }
    }
}

@Composable
private fun TodayFocalOverview(
    state: FocalCard,
    unplacedCount: Int,
    onOpenSchedule: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    val visuals = LocalReplandVisuals.current
    // Every word below comes from the resource table, including the two the card used to build by
    // hand. This is the largest text on the Today tab, so an English-locale user reading Chinese
    // here reads it before anything else on the screen.
    val label = when (state) {
        is FocalCard.Named -> stringResource(state.emphasis.labelRes)
        FocalCard.Empty, FocalCard.Done -> stringResource(R.string.today_title)
    }
    val title = when (state) {
        is FocalCard.Named -> state.title
        FocalCard.Empty -> stringResource(R.string.widget_empty_title)
        FocalCard.Done -> stringResource(R.string.widget_done_title)
    }
    // A named block carries its own slot. `Done` deliberately gets no second line: "pick something
    // from your tasks" is advice for a day that has not started, not for one that is over.
    val subtitle = when (state) {
        is FocalCard.Named -> stringResource(
            R.string.widget_time_range,
            TimeBlockValidator.formatTime(state.startMinute),
            TimeBlockValidator.formatTime(state.endMinute),
        )
        FocalCard.Empty -> stringResource(R.string.widget_empty_hint)
        FocalCard.Done -> null
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSchedule).testTag("today-focus"),
        color = visuals.focusSurface,
        // The focal block gets the largest radius in the app, so even in a screenshot where the
        // text is too small to read, this card is still obviously the thing the screen is about.
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = visuals.focusMuted,
                )
                Spacer(Modifier.weight(1f))
                if (unplacedCount > 0) {
                    TextButton(onClick = onOpenTasks) {
                        Text(
                            stringResource(R.string.today_unplaced_count, unplacedCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = visuals.focusAction,
                        )
                    }
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = visuals.focusText,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = visuals.focusMuted,
                )
            }
            if (state is FocalCard.Named && state.parallelCount > 0) {
                Text(
                    stringResource(R.string.today_parallel_count, state.parallelCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = visuals.focusMuted,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "查看日程",
                    style = MaterialTheme.typography.labelLarge,
                    color = visuals.focusAction,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = visuals.focusAction,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun TodayEmptyState(onAdd: () -> Unit, onVoiceCapture: () -> Unit) {
    val visuals = LocalReplandVisuals.current
    Surface(shape = RoundedCornerShape(24.dp), color = visuals.focusSurface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
                Icon(
                    Icons.Outlined.DateRange,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                    tint = visuals.focusAction,
                )
            }
            Text("把今天，留给\n重要的事。", style = MaterialTheme.typography.headlineSmall, color = visuals.focusText)
            Text(
                "还没有安排。先记下一件事，\n让一天从容开始。",
                style = MaterialTheme.typography.bodyMedium,
                color = visuals.focusMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onAdd,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = visuals.focusAction,
                        contentColor = visuals.onFocusAction,
                    ),
                ) { Text("添加第一件事") }
                TextButton(
                    onClick = onVoiceCapture,
                    colors = ButtonDefaults.textButtonColors(contentColor = visuals.focusAction),
                ) { Text("说一句话") }
            }
        }
    }
}

/**
 * A group heading in a settings-style list.
 *
 * Two jobs: sit *below* the content in the type scale (`labelMedium`, never `labelLarge`, so it
 * reads as a signpost rather than as an entry), and push itself away from the group above with
 * extra top space. Without that gap every row in the list is equidistant and the groups stop
 * being groups.
 */
@Composable
private fun SectionEyebrow(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 10.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * Avatar at the top of the "mine" page: a circle that shows the chosen image, or the user's
 * initials on a tinted disc when none is set. The image is decoded on the device only — here,
 * not in the unit tests, which is the price of touching `BitmapFactory`.
 */
@Composable
private fun ProfileHeaderCard(
    nickname: String,
    defaultName: String,
    avatarPath: String?,
    onPickAvatar: () -> Unit,
    onClearAvatar: () -> Unit,
    onEditName: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val display = ProfileIdentity.displayName(nickname, defaultName)
    val initials = ProfileIdentity.initials(display)
    val pink = replandAccent(4)
    val lilac = replandAccent(2)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(pink.surface, lilac.surface)))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box {
                    AvatarCircle(
                        avatarPath = avatarPath,
                        initials = initials,
                        modifier = Modifier.clickable(onClick = onPickAvatar),
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp),
                    ) {
                        Icon(
                            DesignIcons.Camera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(5.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        display,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.clickable(onClick = onEditName).testTag("profile-name"),
                    )
                    Text(
                        stringResource(R.string.profile_identity_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onPickAvatar) { Text(stringResource(R.string.profile_avatar_change)) }
                TextButton(onClick = onEditName) { Text("编辑用户名") }
                if (avatarPath != null) {
                    TextButton(onClick = onClearAvatar) { Text(stringResource(R.string.profile_avatar_clear)) }
                }
            }
        }
    }
}

/** The disc itself: a decoded image, or two initials on the tinted background. */
@Composable
private fun AvatarCircle(avatarPath: String?, initials: String, modifier: Modifier = Modifier) {
    val bitmap = remember(avatarPath) { decodeAvatar(avatarPath, maxPx = 128) }
    Box(
        modifier = modifier
            .testTag("profile-avatar")
            .size(80.dp)
            .clip(CircleShape)
            .background(if (bitmap != null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow)
            .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                initials,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Decodes an avatar file to an `ImageBitmap`, downscaled so a high-resolution photo does not sit
 * fully expanded in memory just to fill a 64dp disc. Returns null when the path is missing or the
 * file is not a decodable image.
 */
private fun decodeAvatar(path: String?, maxPx: Int): ImageBitmap? {
    if (path == null) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val longest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
    var sample = 1
    while (sample * 2 <= longest / maxPx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}

@Composable
private fun ProfileNameDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(current) { mutableStateOf(current) }
    val sanitized = ProfileIdentity.sanitizeNickname(value)
    // Empty is allowed: it clears the nickname back to the fallback name shown by the header.
    val valid = sanitized.length <= ProfileIdentity.NICKNAME_MAX_CHARS
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_name_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.profile_name_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onSave(sanitized) }, enabled = valid) {
                Text(stringResource(R.string.profile_name_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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
                    stringResource(R.string.daily_review_recorded_feedback),
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
    selection: TaskSelection?,
    onSelectionChange: (TaskSelection?) -> Unit,
    onBatchAction: (BatchTaskAction) -> Unit,
    onOpen: (Task) -> Unit,
    onStart: (String) -> Unit,
    onAdd: () -> Unit,
) {
    var view by rememberSaveable { mutableStateOf(TaskCenterView.OVERVIEW) }
    val activeTasks = tasks.filter { it.status.isActive }
    val today = LocalDate.now()
    val visibleTasks = when (view) {
        TaskCenterView.OVERVIEW -> activeTasks
        TaskCenterView.INBOX -> activeTasks.filter { it.dueDate == null && it.scheduledForDate == null }
        TaskCenterView.TODAY -> activeTasks.filter { it.dueDate == today || it.scheduledForDate == today }
        TaskCenterView.OVERDUE -> activeTasks.filter { it.dueDate?.isBefore(today) == true }
        TaskCenterView.COMPLETED -> tasks.filter { !it.status.isActive }
    }
    // Switching views must not leave ticks on rows the user can no longer see, because the action
    // bar would then report a count over invisible tasks. Keyed on the visible list, so it also
    // fires when the data itself changes underneath — a task deleted or completed elsewhere shrinks
    // the selection on the next frame instead of at the next write.
    LaunchedEffect(selection, visibleTasks) {
        selection?.let { current ->
            val kept = current.retained(visibleTasks)
            if (kept != current) onSelectionChange(kept)
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (selection != null) {
            BatchSelectionBar(
                count = selection.size,
                onExit = { onSelectionChange(null) },
                onSelectAll = { onSelectionChange(selection.allOf(visibleTasks)) },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaskCenterView.entries.forEach { option ->
                    DesignFilterChip(
                        selected = view == option,
                        onClick = { view = option },
                        label = { Text(option.label) },
                        accentIndex = option.ordinal,
                    )
                }
            }
            // The entry point lives outside the scrolling chips so it cannot be scrolled off; it
            // disappears once the mode is on, since the bar above now owns the exit.
            if (selection == null) {
                TextButton(
                    onClick = { onSelectionChange(TaskSelection.EMPTY) },
                    modifier = Modifier.testTag("batch-enter"),
                ) { Text(stringResource(R.string.batch_select_mode)) }
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
                hint = when (view) {
                    TaskCenterView.INBOX -> "记下来的事会先落在这里，之后再排时间。"
                    TaskCenterView.TODAY -> "今天还是空的。挑一件事放进日轨道，一天就有形状了。"
                    TaskCenterView.OVERDUE -> "没有拖欠的事，保持住。"
                    TaskCenterView.COMPLETED -> "完成第一件事之后，这里会留下记录。"
                    TaskCenterView.OVERVIEW -> "先记下一件事，它会自动进入待安排。"
                },
                actionLabel = if (view == TaskCenterView.COMPLETED || view == TaskCenterView.OVERDUE) null else "添加任务",
                onAction = onAdd,
            )
        } else if (!isLoading) {
            val rowSelection: (Task) -> Boolean? = { task -> selection?.contains(task.id) }
            val onToggle: (Task) -> Unit = { task ->
                onSelectionChange(selection?.toggle(task.id))
            }
            Box(modifier = Modifier.weight(1f)) {
                if (view == TaskCenterView.OVERVIEW) {
                    TaskControlCenter(
                        tasks = activeTasks,
                        selected = rowSelection,
                        onToggleSelect = onToggle,
                        onOpen = onOpen,
                        onStart = onStart,
                    )
                } else {
                    TaskList(
                        tasks = visibleTasks,
                        selected = rowSelection,
                        onToggleSelect = onToggle,
                        onOpen = onOpen,
                        onStart = onStart,
                    )
                }
            }
        }
        if (selection != null) {
            // `actionsFor` reads the same rule the write does, so a button only appears when it has
            // at least one row to change — the confirm dialog can never say "0 tasks".
            BatchActionBar(
                actions = BatchTasks.actionsFor(selection, visibleTasks),
                enabled = !selection.isEmpty,
                onAction = onBatchAction,
            )
        }
    }
}

/**
 * The bar that replaces the filter chips' trailing action while the list is in selection mode.
 *
 * The count is the selection's own size, not a number the caller passes in: the two cannot drift,
 * and "已选 N 项" is then a statement about the same set the action bar will act on.
 */
@Composable
private fun BatchSelectionBar(
    count: Int,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onExit, modifier = Modifier.testTag("batch-exit")) {
                Text(stringResource(R.string.batch_exit))
            }
            Text(
                text = stringResource(R.string.batch_selected_count, count),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .weight(1f)
                    .testTag("batch-count"),
            )
            TextButton(onClick = onSelectAll, modifier = Modifier.testTag("batch-select-all")) {
                Text(stringResource(R.string.batch_select_all))
            }
        }
    }
}

/**
 * The actions a batch can take, at the bottom where a thumb reaches them.
 *
 * The buttons are disabled rather than hidden when nothing is ticked: an action bar that vanishes
 * the moment the last tick comes off reads as the mode having ended, and the user would have to
 * find 退出 to understand that it has not.
 */
@Composable
private fun BatchActionBar(
    actions: List<BatchTaskAction>,
    enabled: Boolean,
    onAction: (BatchTaskAction) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            actions.forEach { action ->
                OutlinedButton(
                    onClick = { onAction(action) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("batch-action-${action.name.lowercase()}"),
                ) { Text(stringResource(action.labelRes)) }
            }
        }
    }
}

/**
 * Confirms a batch before it writes, and names the rows it will not touch.
 *
 * A batch is the one place a mis-tap costs several rows at once, and the rows it *skips* are the
 * part the user cannot predict from the selection alone — so the skipped count is spelled out
 * instead of being left to be discovered in the list afterwards.
 */
@Composable
private fun BatchConfirmDialog(
    action: BatchTaskAction,
    targetCount: Int,
    skippedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.batch_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.batch_confirm_body, targetCount, stringResource(action.labelRes)))
                if (skippedCount > 0) {
                    Text(
                        text = stringResource(R.string.batch_confirm_skipped, skippedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("batch-confirm")) {
                Text(stringResource(action.labelRes))
            }
        },
        // The generic `cancel`, deliberately not `batch_action_cancel`: the latter is one of the
        // enum's own labels, and reusing it here would make the enum-to-key guard in
        // TaskBatchResourceTest unable to tell a renamed key from a missing one.
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("batch-confirm-dismiss")) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private enum class TaskCenterView(val label: String) {
    OVERVIEW("总览"), INBOX("待安排"), TODAY("今天"), OVERDUE("逾期"), COMPLETED("已完成"),
}

@Composable
private fun TaskControlCenter(
    tasks: List<Task>,
    selected: (Task) -> Boolean?,
    onToggleSelect: (Task) -> Unit,
    onOpen: (Task) -> Unit,
    onStart: (String) -> Unit,
) {
    val today = LocalDate.now()
    val attention = tasks.filter {
        it.dueDate?.isBefore(today) == true ||
            it.scheduledForDate?.isBefore(today) == true ||
            it.status == TaskStatus.POSTPONED
    }
    val todayTasks = tasks.filter { (it.dueDate == today || it.scheduledForDate == today) && it !in attention }
    val inbox = tasks.filter { it.dueDate == null && it.scheduledForDate == null && it !in attention && it !in todayTasks }
    val later = tasks.filter { it !in attention && it !in todayTasks && it !in inbox }
    var expandedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
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
                    TaskRow(
                        task,
                        { onOpen(task) },
                        { onStart(task.id) },
                        emphasis = if (title == "需要处理") TaskRowEmphasis.ATTENTION else TaskRowEmphasis.NORMAL,
                        selected = selected(task),
                        onToggleSelect = { onToggleSelect(task) },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun TaskList(
    tasks: List<Task>,
    selected: (Task) -> Boolean?,
    onToggleSelect: (Task) -> Unit,
    onOpen: (Task) -> Unit,
    onStart: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(tasks, key = Task::id) { task ->
            TaskRow(
                task = task,
                onOpen = { onOpen(task) },
                onStart = { onStart(task.id) },
                selected = selected(task),
                onToggleSelect = { onToggleSelect(task) },
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
    onSubTaskToggle: (String, Boolean) -> Unit,
    onSubTaskAdd: (String) -> Unit,
    onSubTaskRemove: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
                .imePadding()
                .testTag("task-detail-scroll"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                val hero = replandAccent(task.id.hashCode())
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = hero.surface,
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
                                color = hero.ink,
                            )
                            Spacer(Modifier.width(12.dp))
                            TaskStatusButton(task = task, onStart = { onStart(task.id) })
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = task.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = hero.ink,
                        )
                        task.description.takeIf { it.isNotBlank() && it != task.displayName }?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = hero.ink.copy(alpha = 0.78f),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailMetaPill(stringResource(task.category.labelRes()), hero.ink)
                            DetailMetaPill(stringResource(task.userPriority.labelRes()), hero.ink)
                            task.totalDurationMinutes?.let {
                                DetailMetaPill(stringResource(R.string.duration_format, it), hero.ink)
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
            item {
                SubTaskSection(
                    subtasks = task.subtasks,
                    onToggle = onSubTaskToggle,
                    onAdd = onSubTaskAdd,
                    onRemove = onSubTaskRemove,
                )
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
private fun SubTaskSection(
    subtasks: List<SubTask>,
    onToggle: (String, Boolean) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val progress = SubTasks.progress(subtasks)
    var newTitle by rememberSaveable { mutableStateOf("") }

    DetailSection(
        title = stringResource(R.string.subtasks_title),
        eyebrow = stringResource(R.string.subtasks_eyebrow),
    ) {
        if (subtasks.isEmpty()) {
            Text(stringResource(R.string.subtasks_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            subtasks.forEach { sub ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subtask-row-${sub.id}")
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = sub.isDone,
                        onCheckedChange = { onToggle(sub.id, it) },
                        modifier = Modifier.testTag("subtask-check-${sub.id}"),
                    )
                    Text(
                        text = sub.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (sub.isDone) TextDecoration.LineThrough else null,
                        color = if (sub.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(
                        onClick = { onRemove(sub.id) },
                        modifier = Modifier.testTag("subtask-remove-${sub.id}"),
                    ) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.subtask_remove, sub.title))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                modifier = Modifier.weight(1f).testTag("subtask-input"),
                placeholder = { Text(stringResource(R.string.subtask_add_hint)) },
                singleLine = true,
                isError = newTitle.isNotBlank() && !SubTaskValidator.isValidTitle(newTitle),
            )
            Button(
                onClick = {
                    if (SubTaskValidator.isValidTitle(newTitle)) {
                        onAdd(newTitle.trim())
                        newTitle = ""
                    }
                },
                enabled = SubTaskValidator.isValidTitle(newTitle),
                modifier = Modifier.testTag("subtask-add"),
            ) { Text(stringResource(R.string.subtask_add)) }
        }
        if (progress.hasSubtasks) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { progress.percent / 100f }, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.subtask_progress_label, progress.done, progress.total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun DetailMetaPill(text: String, ink: Color) {
    Surface(
        color = ink.copy(alpha = 0.08f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = ink,
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
                request.userMessage?.let { DetailLine("你本次说的话", it) }
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
    onImportSpreadsheet: (Uri) -> Unit,
    onImportCalendar: (Uri) -> Unit,
    onPasteTimetable: () -> Unit,
    onClearTimetableImport: () -> Unit,
) {
    val timetablePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(onImportPdf) },
    )
    // A second launcher rather than a wider MIME filter on the first one: SAF hands back an
    // opaque content:// id, so the only reliable signal about which format the user chose is
    // which picker they came through.
    val spreadsheetPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(onImportSpreadsheet) },
    )
    // The calendar gets its own picker for the same reason. `text/calendar` is the registered type,
    // but plenty of file providers report an .ics as `text/plain`, so both are offered — the parser
    // checks for the VCALENDAR header itself and reports a wrong file as such.
    val calendarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(onImportCalendar) },
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
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(R.string.import_excel),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.import_excel_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        spreadsheetPicker.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("import-timetable-xlsx"),
                ) { Text(stringResource(R.string.import_excel_pick)) }
                OutlinedButton(
                    onClick = onPasteTimetable,
                    modifier = Modifier.fillMaxWidth().testTag("import-timetable-paste"),
                ) { Text(stringResource(R.string.import_paste)) }
                Text(
                    stringResource(R.string.import_paste_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Calendar import sits in its own card rather than inside the spreadsheet one: it is the
        // only path whose entries become *occupied time* instead of courses, and that difference is
        // the whole reason a user would reach for it.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(R.string.import_calendar_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.import_calendar_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Button(
                    onClick = { calendarPicker.launch(arrayOf("text/calendar", "text/plain")) },
                    modifier = Modifier.fillMaxWidth().testTag("import-calendar-ics"),
                ) { Text(stringResource(R.string.import_calendar_pick)) }
            }
        }
        when (timetableImport) {
            TimetableImportState.Idle -> Unit

            // Says what actually landed, including the rows that were skipped as duplicates.
            // Without this, re-importing an unchanged file flickers "reading…" and then says
            // nothing at all — which the user can only read as a failure.
            is TimetableImportState.Done -> ImportResultCard(
                summary = when {
                    timetableImport.added > 0 ->
                        stringResource(R.string.import_excel_success, timetableImport.added)

                    timetableImport.skipped > 0 ->
                        stringResource(R.string.import_timetable_done_none, timetableImport.skipped)

                    else -> stringResource(R.string.import_timetable_done_empty)
                },
                details = listOfNotNull(
                    if (timetableImport.added > 0 && timetableImport.skipped > 0) {
                        stringResource(R.string.import_timetable_done_skip, timetableImport.skipped)
                    } else {
                        null
                    },
                ),
                onDismiss = onClearTimetableImport,
            )

            // Every number gets its own sentence because they mean different things: "already
            // there" is the *correct* outcome of re-importing an unchanged calendar, "ignored" is
            // something the file asked for that this app will not do, and "approximated" is a real
            // import carrying a known caveat. One summed "skipped" count would read as a failure.
            is TimetableImportState.CalendarDone -> ImportResultCard(
                summary = when {
                    timetableImport.added > 0 ->
                        stringResource(R.string.calendar_import_success, timetableImport.added)

                    timetableImport.duplicates > 0 ->
                        stringResource(R.string.calendar_import_none_new, timetableImport.duplicates)

                    else -> stringResource(R.string.calendar_import_none)
                },
                details = listOfNotNull(
                    if (timetableImport.added > 0) {
                        stringResource(
                            R.string.calendar_import_breakdown,
                            timetableImport.blocks,
                            timetableImport.overrides,
                        )
                    } else {
                        null
                    },
                    if (timetableImport.added > 0 && timetableImport.duplicates > 0) {
                        stringResource(R.string.calendar_import_duplicate, timetableImport.duplicates)
                    } else {
                        null
                    },
                    if (timetableImport.ignored > 0) {
                        stringResource(R.string.calendar_import_ignored, timetableImport.ignored)
                    } else {
                        null
                    },
                    if (timetableImport.approximatedSeries > 0) {
                        stringResource(
                            R.string.calendar_import_approximated,
                            timetableImport.approximatedSeries,
                        )
                    } else {
                        null
                    },
                ),
                onDismiss = onClearTimetableImport,
            )

            TimetableImportState.Reading -> {
                Text(
                    stringResource(R.string.import_reading),
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

/**
 * The card that reports what an import did.
 *
 * Extracted because both import paths now report several independent numbers, and a second copy of
 * this layout would be the place where one of them quietly stops showing a detail. [details] is a
 * list rather than two nullable strings so adding a third reason cannot mean adding a third branch.
 */
@Composable
private fun ImportResultCard(
    summary: String,
    details: List<String>,
    onDismiss: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(summary, style = MaterialTheme.typography.bodyMedium)
                details.forEach { detail ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
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
    failureMessage: String? = null,
) {
    val tasksById = tasks.associateBy(Task::id)
    val unknownTaskLabel = stringResource(R.string.unknown_task)
    val orderedTaskIds = draft.orderedTaskIds
        .distinct()
        .filter(tasksById::containsKey)
        .ifEmpty { (draft.segments.map(PlannedSegment::taskId) + draft.pendingTaskIds).distinct() }
    val priorityAssessmentsByTask = draft.priorityAssessments.associateBy(LocalPriorityAssessment::taskId)
    var editingSegmentId by remember { mutableStateOf<String?>(null) }
    var showTaskOrder by rememberSaveable { mutableStateOf(false) }
    EditorSheet(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_draft_title)) },
        text = {
            Column(
                modifier = Modifier
                    .height(500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                failureMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    stringResource(R.string.plan_draft_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PlanDraftTimelinePreview(
                    draft = draft,
                    tasks = tasks,
                    weeklyBlocks = weeklyBlocks,
                    dateOverrides = dateOverrides,
                    semesterFirstWeekMonday = semesterFirstWeekMonday,
                    onEditSegment = { editingSegmentId = it },
                )
                draft.pendingTaskIds.takeIf(List<String>::isNotEmpty)?.let { pendingIds ->
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
                                stringResource(R.string.plan_feasibility_warning),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            unscheduledTasks.forEach { unscheduled ->
                                Text(
                                    stringResource(
                                        R.string.plan_unassigned_task,
                                        tasksById[unscheduled.taskId]?.displayName
                                            ?: stringResource(R.string.unknown_task),
                                        unscheduled.remainingMinutes,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
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
                                Text("只影响下次自动安排，不会改变上面的时段。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                availableTrackIds = (listOf("focus", "parallel-2", "parallel-3") + draft.segments.map(PlannedSegment::trackId)).distinct(),
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
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
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
                    dates.forEachIndexed { index, date ->
                        DesignFilterChip(
                            selected = selectedDate == date,
                            onClick = { selectedDate = date },
                            label = { Text(rememberDateFormatter(R.string.date_pattern_numeric_month_day_weekday).format(date)) },
                            modifier = Modifier.testTag("plan-draft-date-$date"),
                            accentIndex = index,
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
                DraftDayTrackPreview(entries = entries, onEditSegment = onEditSegment)
            }
        }
    }
}

@Composable
private fun DraftDayTrackPreview(
    entries: List<TimelineEntry>,
    onEditSegment: (String) -> Unit,
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                            draftTrackLabel(trackId),
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

private fun draftTrackLabel(trackId: String): String = when (trackId) {
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
                    availableTrackIds.forEachIndexed { index, trackId ->
                        DesignFilterChip(
                            selected = selectedTrackId == trackId,
                            onClick = {
                                selectedTrackId = trackId
                                confirmConflict = false
                            },
                            label = { Text(draftTrackLabel(trackId)) },
                            accentIndex = index,
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
    defaultName: String,
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
    aiProviderConfigError: String?,
    aiProviderConnectionTest: AiProviderConnectionTest,
    aiProviderModelList: AiModelListState,
    onSaveAiProviderConfig: (String, String, String) -> Unit,
    onClearAiProviderKey: () -> Unit,
    onTestAiProviderConnection: () -> Unit,
    onRefreshAiProviderModels: () -> Unit,
    engagementMode: EngagementMode,
    onEngagementModeChange: (EngagementMode) -> Unit,
) {
    if (isLoading) return
    val context = LocalContext.current
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

    // The cosmetic identity lives in a store keyed off the application context, never a picked
    // `content://` URI — a persisted URI is revocable and would leave the avatar pointing at
    // another app's bytes after a reboot. State mirrors the store and is updated in both places so
    // the header repaints without a full recomposition from above.
    val profileStore = remember(context) { ProfileStore(context) }
    var nickname by remember(profileStore) { mutableStateOf(profileStore.nickname) }
    var avatarPath by remember(profileStore) { mutableStateOf(profileStore.avatarPath()) }
    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = context.contentResolver.openInputStream(uri)?.use { profileStore.saveAvatar(it) }
        when (result) {
            is AvatarSaveResult.Saved -> avatarPath = result.path
            is AvatarSaveResult.NotAnImage -> Toast
                .makeText(context, R.string.profile_avatar_not_image, Toast.LENGTH_SHORT)
                .show()
            is AvatarSaveResult.TooLarge -> Toast
                .makeText(context, R.string.profile_avatar_too_large, Toast.LENGTH_SHORT)
                .show()
            null -> Unit
        }
    }
    val pickAvatar = { avatarPicker.launch("image/*") }
    val removeAvatar = {
        profileStore.clearAvatar()
        avatarPath = null
    }
    val saveName = { value: String ->
        profileStore.nickname = value
        nickname = value
        showNameDialog = false
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProfileHeaderCard(
            nickname = nickname,
            defaultName = defaultName,
            avatarPath = avatarPath,
            onPickAvatar = pickAvatar,
            onClearAvatar = removeAvatar,
            onEditName = { showNameDialog = true },
            modifier = Modifier.fillMaxWidth(),
        )
        if (showNameDialog) {
            ProfileNameDialog(
                current = nickname,
                onDismiss = { showNameDialog = false },
                onSave = saveName,
            )
        }
        SectionEyebrow("日常使用")
        EngagementModeSection(engagementMode, onEngagementModeChange)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(24.dp),
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
        SectionEyebrow("智能与个性化")
        AiSettingsSection(
            aiEnabled = aiEnabled,
            aiConsented = aiConsented,
            onAiEnabledChange = onAiEnabledChange,
            providerConfig = aiProviderConfig,
            providerConfigError = aiProviderConfigError,
            providerConnectionTest = aiProviderConnectionTest,
            providerModelList = aiProviderModelList,
            onSaveProviderConfig = onSaveAiProviderConfig,
            onClearProviderKey = onClearAiProviderKey,
            onTestConnection = onTestAiProviderConnection,
            onRefreshModels = onRefreshAiProviderModels,
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
        SectionEyebrow("数据与隐私")
        SettingsDisclosure("本地数据", "导出业务记录与清理数据", showData, { showData = !showData })
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
        shape = RoundedCornerShape(24.dp),
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
private fun EngagementModeSection(
    selected: EngagementMode,
    onSelect: (EngagementMode) -> Unit,
) {
    Text("你的参与方式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text("随时切换；只改变信息密度与建议方式，不替你做决定。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
        Column {
            listOf(
                Triple(EngagementMode.CO_PLANNER, "重度参与", "看完整时间轨道，主动编排与复盘"),
                Triple(EngagementMode.GUIDED, "适度引导", "保留全局视图，在关键节点接受建议"),
                Triple(EngagementMode.EXECUTOR, "轻量执行", "先看到接下来三项，需要时展开全局"),
            ).forEachIndexed { index, (mode, label, detail) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(mode) }
                        .testTag("engagement-${mode.name.lowercase()}")
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selected == mode) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)) {
                            Text("当前", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                if (index < 2) Spacer(Modifier.height(1.dp))
            }
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
    providerModelList: AiModelListState,
    onSaveProviderConfig: (String, String, String) -> Unit,
    onClearProviderKey: () -> Unit,
    onTestConnection: () -> Unit,
    onRefreshModels: () -> Unit,
) {
    var showProviderSettings by rememberSaveable { mutableStateOf(false) }
    // The card names whichever service the saved address actually points at. It used to say
    // "Agnes API" unconditionally, which was true only while Agnes was the only option.
    val activePreset = AiProviderPreset.matching(providerConfig.baseUrl)
    val providerLabel = if (activePreset == AiProviderPreset.CUSTOM) {
        "自定义服务"
    } else {
        activePreset.displayName
    }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
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
                        Text(providerLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (providerConfig.hasApiKey) "密钥已加密保存在本设备 · ${providerConfig.model}"
                            else "尚未添加密钥 · 一句话安排需要云端模型",
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
            modelList = providerModelList,
            onDismiss = { showProviderSettings = false },
            onSave = onSaveProviderConfig,
            onClearKey = onClearProviderKey,
            onTestConnection = onTestConnection,
            onRefreshModels = onRefreshModels,
        )
    }
}

@Composable
private fun AiProviderSettingsDialog(
    config: AiProviderConfig,
    errorMessage: String?,
    connectionTest: AiProviderConnectionTest,
    modelList: AiModelListState,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onClearKey: () -> Unit,
    onTestConnection: () -> Unit,
    onRefreshModels: () -> Unit,
) {
    var baseUrl by remember(config.baseUrl) { mutableStateOf(config.baseUrl) }
    var model by remember(config.model) { mutableStateOf(config.model) }
    var apiKey by remember { mutableStateOf("") }
    val activePreset = AiProviderPreset.matching(baseUrl)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 服务配置") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "用于需要更深层语义理解的可选建议。安排写入仍然必须经过轨道草案与手动确认。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("服务商", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AiProviderPreset.configured.forEachIndexed { index, preset ->
                        DesignFilterChip(
                            selected = activePreset == preset,
                            onClick = {
                                if (activePreset != preset) {
                                    baseUrl = preset.baseUrl
                                    // Model names differ per service and are deliberately not
                                    // baked in, so a name carried over from the previous
                                    // provider would be sent to one that does not serve it.
                                    model = when (preset) {
                                        AiProviderPreset.AGNES, AiProviderPreset.AGNES_CN -> DEFAULT_AGNES_MODEL
                                        else -> ""
                                    }
                                }
                            },
                            label = { Text(preset.displayName) },
                            modifier = Modifier.testTag("ai-provider-preset-${preset.name}"),
                            accentIndex = index,
                        )
                    }
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth().testTag("ai-provider-endpoint"),
                    label = { Text("服务地址") },
                    supportingText = {
                        Text(
                            if (activePreset == AiProviderPreset.CUSTOM) {
                                "自定义地址，需包含服务商要求的版本路径"
                            } else {
                                "已识别为 ${activePreset.displayName}"
                            },
                        )
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth().testTag("ai-provider-model"),
                    label = { Text("模型") },
                    supportingText = {
                        Text(
                            when (modelList) {
                                AiModelListState.Idle -> "可从服务商读取列表，或直接填写模型名"
                                AiModelListState.Loading -> "正在读取模型列表…"
                                is AiModelListState.Loaded -> "服务商提供 ${modelList.ids.size} 个模型"
                                is AiModelListState.Failed -> modelList.reason.connectionTestMessage()
                            },
                        )
                    },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onRefreshModels,
                        enabled = config.hasApiKey && modelList !is AiModelListState.Loading,
                        modifier = Modifier.testTag("ai-provider-fetch-models"),
                    ) { Text("获取模型列表") }
                    if (!config.hasApiKey) {
                        Text(
                            "需先保存密钥",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (modelList is AiModelListState.Loaded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 132.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        modelList.ids.forEach { id ->
                            Surface(
                                onClick = { model = id },
                                color = if (model == id) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    id,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
            // The user's own tool writing, when any of it names a task this row was drawn from.
            // Kept visually apart from the sources above and labelled as *not* counted: the
            // conclusion counts its sources, and these are not among them. Presenting them in the
            // same list would make the page imply a count it does not have.
            if (!evidence.relatedToolOutputs.isEmpty) {
                Text(
                    stringResource(R.string.profile_evidence_tool_outputs_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
                evidence.relatedToolOutputs.shown.forEach { output ->
                    Text(
                        text = stringResource(
                            R.string.profile_evidence_tool_output_format,
                            stringResource(output.kind.labelRes()),
                            output.taskDisplayName,
                            output.excerpt,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (evidence.relatedToolOutputs.hiddenCount > 0) {
                    Text(
                        stringResource(
                            R.string.profile_evidence_tool_output_more,
                            evidence.relatedToolOutputs.hiddenCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
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

/**
 * The empty state, built as the same L2 focal block the Today tab uses.
 *
 * An empty screen is where hierarchy matters most: there is no content to organise the page, so
 * a lone 16sp sentence centred in a void reads as "something failed to load". One mint card with
 * an icon, a headline and a single action gives the screen a subject and an obvious next step.
 */
@Composable
private fun EmptyState(
    title: String,
    actionLabel: String?,
    onAction: () -> Unit,
    hint: String? = null,
    icon: ImageVector = Icons.Outlined.DateRange,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val accent = replandAccent(icon.hashCode())
                Surface(shape = RoundedCornerShape(14.dp), color = accent.surface) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                        tint = accent.ink,
                    )
                }
                Text(title, style = MaterialTheme.typography.headlineSmall)
                hint?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                actionLabel?.let { label ->
                    DesignButton(onClick = onAction) { Text(label) }
                }
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
    var repeatRule by remember(task) { mutableStateOf(task?.repeatRule) }
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
                // Sits under the planned day rather than beside the deadline, because that is the
                // date a repeat is anchored on: the control's position is what teaches the rule.
                // `null` is one of the choices, not the absence of the control — clearing a rule has
                // to be expressible, or a task could never stop repeating.
                Text(stringResource(R.string.repeat_rule), style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = listOf<RepeatRule?>(null) + RepeatRule.entries,
                    selected = repeatRule,
                    label = { rule -> stringResource(rule?.labelRes() ?: R.string.repeat_none) },
                    onSelected = { repeatRule = it },
                )
                Text(
                    stringResource(R.string.repeat_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        // Carried explicitly: the editor is the only thing that can clear a rule, so
                        // omitting this would make every edit silently stop the task repeating.
                        repeatRule = repeatRule,
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
                // A lambda rather than `::taskDateLabel`: the label now has a composable overload
                // and a pure one, and a bare function reference would have to pick between them.
                selectedDate?.let { taskDateLabel(it) } ?: "未设置",
                style = MaterialTheme.typography.bodyLarge,
                color = if (selectedDate == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DesignFilterChip(
                    selected = selectedDate == today,
                    onClick = { onSelectDate(today) },
                    label = { Text("今天") },
                    accentIndex = 0,
                )
                DesignFilterChip(
                    selected = selectedDate == today.plusDays(1),
                    onClick = { onSelectDate(today.plusDays(1)) },
                    label = { Text("明天") },
                    accentIndex = 1,
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
        values.forEachIndexed { index, value ->
            DesignFilterChip(
                selected = value == selected,
                onClick = { onSelected(value) },
                label = { Text(label(value)) },
                accentIndex = index,
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
    dialogTitle: String? = null,
    dialogMessage: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle ?: stringResource(R.string.delete_time_entry)) },
        text = { Text(dialogMessage ?: stringResource(R.string.delete_time_entry_message, title)) },
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

/**
 * `internal` rather than `private` like its siblings: the task capture sheet lives in
 * `ui/components` and offers the same control, and a second copy of this mapping is exactly the
 * "two lists that must agree" defect the resource guards exist to prevent.
 */
@StringRes
internal fun RepeatRule.labelRes(): Int = when (this) {
    RepeatRule.DAILY -> R.string.repeat_daily
    RepeatRule.WEEKLY -> R.string.repeat_weekly
    RepeatRule.MONTHLY -> R.string.repeat_monthly
}

/**
 * The resource label for a tool output's origin.
 *
 * The `when` has no `else`, which is the point: adding a tool that writes about a task without
 * giving it a label is a compile error, not a row that renders as the empty string.
 */
@StringRes
internal fun ToolOutputKind.labelRes(): Int = when (this) {
    ToolOutputKind.KWL -> R.string.profile_evidence_tool_kind_kwl
    ToolOutputKind.REVIEW_POINT -> R.string.profile_evidence_tool_kind_review_point
    ToolOutputKind.SELF_EXPLANATION -> R.string.profile_evidence_tool_kind_self_explanation
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
