package com.swan1127.repland.testkit

import com.swan1127.repland.domain.model.AiAdvisor
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.AiAdvisorRequest
import com.swan1127.repland.domain.model.AiAdvisorResult
import com.swan1127.repland.domain.model.AiPreferences
import com.swan1127.repland.domain.model.AiProviderConfig
import com.swan1127.repland.domain.model.AiProviderSecret
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceRequest
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceResult
import com.swan1127.repland.domain.model.ArrangementAssistantAdvisor
import com.swan1127.repland.domain.model.CategoryPreferences
import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.DateOverrideDraft
import com.swan1127.repland.domain.model.EngagementMode
import com.swan1127.repland.domain.model.LocalDataSnapshot
import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlanDraftGenerator
import com.swan1127.repland.domain.model.PlanGenerationInput
import com.swan1127.repland.domain.model.ProfileEvidence
import com.swan1127.repland.domain.model.ReminderPreferences
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.SubTask
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TimeArrangementSnapshot
import com.swan1127.repland.domain.model.TimeConstraintSettings
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import com.swan1127.repland.domain.ports.AiModelCatalog
import com.swan1127.repland.domain.ports.AiModelCatalogResult
import com.swan1127.repland.domain.ports.AiProviderConfigRepository
import com.swan1127.repland.domain.ports.AiSettingsRepository
import com.swan1127.repland.domain.ports.CategoryPreferenceRepository
import com.swan1127.repland.domain.ports.DataManagementRepository
import com.swan1127.repland.domain.ports.EngagementRepository
import com.swan1127.repland.domain.ports.PlanRepository
import com.swan1127.repland.domain.ports.ProfileEvidenceRepository
import com.swan1127.repland.domain.ports.ReminderSettingsRepository
import com.swan1127.repland.domain.ports.TaskRepository
import com.swan1127.repland.domain.ports.TimeRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-written fakes for the repository ports the view models depend on.
 *
 * They exist because the ports are already interfaces with no Android types in them, so a view
 * model can be driven end to end in a plain JVM test. Two deliberate properties:
 *
 * 1. **Every mutating call is recorded, not just applied.** The interesting behaviour of these
 *    view models is mostly *refusal* — an invalid draft must never reach the repository. A fake
 *    that only stored the value could not tell "saved nothing" apart from "was never asked".
 * 2. **`observe` is replaceable.** The error paths are reached by making the observed flow throw,
 *    which needs a flow the test itself supplies rather than a `MutableStateFlow`.
 */
class FakeCategoryPreferenceRepository : CategoryPreferenceRepository {
    val state = MutableStateFlow(CategoryPreferences.defaults)
    var observeFlow: Flow<Map<TaskCategory, Int>> = state

    /** Every accepted `save`, in call order. A refused save leaves no entry. */
    val savedWeights = mutableListOf<Map<TaskCategory, Int>>()
    var failWith: Throwable? = null

    override fun observe(): Flow<Map<TaskCategory, Int>> = observeFlow

    override suspend fun save(weights: Map<TaskCategory, Int>) {
        failWith?.let { throw it }
        savedWeights += weights
        state.value = weights
    }
}

class FakeReminderSettingsRepository : ReminderSettingsRepository {
    val state = MutableStateFlow(ReminderPreferences())
    var observeFlow: Flow<ReminderPreferences> = state

    val enabledCalls = mutableListOf<Boolean>()
    var failWith: Throwable? = null

    override fun observe(): Flow<ReminderPreferences> = observeFlow

    override suspend fun setEnabled(enabled: Boolean) {
        failWith?.let { throw it }
        enabledCalls += enabled
        state.value = ReminderPreferences(isEnabled = enabled)
    }
}

class FakeEngagementRepository : EngagementRepository {
    val state = MutableStateFlow(EngagementMode.GUIDED)
    var observeFlow: Flow<EngagementMode> = state

    val modes = mutableListOf<EngagementMode>()
    val openedEntries = mutableListOf<String>()
    var failWith: Throwable? = null

    override fun observeMode(): Flow<EngagementMode> = observeFlow

    override suspend fun setMode(mode: EngagementMode) {
        failWith?.let { throw it }
        modes += mode
        state.value = mode
    }

    override suspend fun recordTimelineOpened(entryId: String) {
        failWith?.let { throw it }
        openedEntries += entryId
    }
}

class FakeProfileEvidenceRepository : ProfileEvidenceRepository {
    val state = MutableStateFlow(emptyList<ProfileEvidence>())
    var observeFlow: Flow<List<ProfileEvidence>> = state

    var generateCount = 0
    val updatedConclusions = mutableListOf<Pair<String, String>>()
    val deletedIds = mutableListOf<String>()
    var failWith: Throwable? = null

    override fun observe(): Flow<List<ProfileEvidence>> = observeFlow

    override suspend fun generateFromConfirmedFeedback() {
        failWith?.let { throw it }
        generateCount += 1
    }

    override suspend fun updateConclusion(id: String, conclusion: String) {
        failWith?.let { throw it }
        updatedConclusions += id to conclusion
    }

    override suspend fun delete(id: String) {
        failWith?.let { throw it }
        deletedIds += id
    }
}

class FakeTaskRepository : TaskRepository {
    val tasks = MutableStateFlow(emptyList<Task>())
    var observeFlow: Flow<List<Task>> = tasks
    var logsFlow: Flow<List<TaskExecutionLog>> = MutableStateFlow(emptyList())
    var logsForDateFlow: Flow<List<TaskExecutionLog>> = MutableStateFlow(emptyList())
    var logsSinceFlow: Flow<List<TaskExecutionLog>> = MutableStateFlow(emptyList())

    val savedDrafts = mutableListOf<TaskDraft>()
    /** `taskId to status to feedback`, in call order. */
    val statusChanges = mutableListOf<Triple<String, TaskStatus, TaskFeedback>>()
    val partialCompletions = mutableListOf<Pair<String, TaskFeedback>>()
    val recordedFeedback = mutableListOf<Pair<String, TaskFeedback>>()
    val corrections = mutableListOf<Triple<String, String, TaskFeedback>>()
    val replacements = mutableListOf<Pair<String, TaskDraft>>()
    val observedLogTaskIds = mutableListOf<String>()
    val observedLogDates = mutableListOf<LocalDate>()
    val observedLogSinceDates = mutableListOf<LocalDate>()

    var failWith: Throwable? = null
    var replacementId: String = "replacement-1"

    override fun observeTasks(): Flow<List<Task>> = observeFlow

    override fun observeExecutionLogs(taskId: String): Flow<List<TaskExecutionLog>> {
        observedLogTaskIds += taskId
        return logsFlow
    }

    override fun observeExecutionLogsForDate(date: LocalDate): Flow<List<TaskExecutionLog>> {
        observedLogDates += date
        return logsForDateFlow
    }

    override fun observeExecutionLogsSince(from: LocalDate): Flow<List<TaskExecutionLog>> {
        observedLogSinceDates += from
        return logsSinceFlow
    }

    override suspend fun save(draft: TaskDraft) {
        failWith?.let { throw it }
        savedDrafts += draft
    }

    val subTaskWrites = mutableListOf<Pair<String, List<SubTask>>>()

    override suspend fun setSubTasks(taskId: String, subtasks: List<SubTask>) {
        failWith?.let { throw it }
        subTaskWrites += taskId to subtasks
    }

    override suspend fun confirmStatus(taskId: String, status: TaskStatus, feedback: TaskFeedback) {
        failWith?.let { throw it }
        statusChanges += Triple(taskId, status, feedback)
    }

    override suspend fun recordFeedback(taskId: String, feedback: TaskFeedback) {
        failWith?.let { throw it }
        recordedFeedback += taskId to feedback
    }

    override suspend fun recordPartialCompletion(taskId: String, feedback: TaskFeedback) {
        failWith?.let { throw it }
        partialCompletions += taskId to feedback
    }

    override suspend fun correctExecutionLog(
        taskId: String,
        correctedLogId: String,
        feedback: TaskFeedback,
    ) {
        failWith?.let { throw it }
        corrections += Triple(taskId, correctedLogId, feedback)
    }

    override suspend fun replaceTask(taskId: String, replacement: TaskDraft): String {
        failWith?.let { throw it }
        replacements += taskId to replacement
        return replacementId
    }
}

// --- batch B: the planning, AI-config, data-management and advisor ports ----------------------

/** A 5-argument placement call, which Kotlin has no tuple for. */
data class FakePlacement(
    val taskId: String,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val trackId: String,
)

data class FakeMove(
    val segmentId: String,
    val startMinute: Int,
    val endMinute: Int,
    val trackId: String,
)

class FakePlanRepository : PlanRepository {
    val currentPlan = MutableStateFlow<ConfirmedPlan?>(null)
    val history = MutableStateFlow(emptyList<ConfirmedPlan>())
    var currentPlanFlow: Flow<ConfirmedPlan?> = currentPlan
    var historyFlow: Flow<List<ConfirmedPlan>> = history

    val acceptedDrafts = mutableListOf<PlanDraft>()
    val restoredPlanIds = mutableListOf<String>()
    var clearCurrentPlanCount = 0
    val segmentLocks = mutableListOf<Pair<String, Boolean>>()
    val placements = mutableListOf<FakePlacement>()
    val moves = mutableListOf<FakeMove>()
    val removals = mutableListOf<String>()
    var failWith: Throwable? = null

    override fun observeCurrentPlan(): Flow<ConfirmedPlan?> = currentPlanFlow

    override fun observePlanHistory(): Flow<List<ConfirmedPlan>> = historyFlow

    override suspend fun accept(draft: PlanDraft) {
        failWith?.let { throw it }
        acceptedDrafts += draft
    }

    override suspend fun restore(planId: String) {
        failWith?.let { throw it }
        restoredPlanIds += planId
    }

    override suspend fun clearCurrentPlan() {
        failWith?.let { throw it }
        clearCurrentPlanCount += 1
    }

    override suspend fun setSegmentLocked(segmentId: String, isLocked: Boolean) {
        failWith?.let { throw it }
        segmentLocks += segmentId to isLocked
    }

    override suspend fun placeTask(
        taskId: String,
        date: LocalDate,
        startMinute: Int,
        endMinute: Int,
        trackId: String,
    ) {
        failWith?.let { throw it }
        placements += FakePlacement(taskId, date, startMinute, endMinute, trackId)
    }

    override suspend fun movePlacement(
        segmentId: String,
        startMinute: Int,
        endMinute: Int,
        trackId: String,
    ) {
        failWith?.let { throw it }
        moves += FakeMove(segmentId, startMinute, endMinute, trackId)
    }

    override suspend fun removePlacement(segmentId: String) {
        failWith?.let { throw it }
        removals += segmentId
    }
}

class FakePlanDraftGenerator : PlanDraftGenerator {
    /** Every input the view model handed down, so a mis-assembled input is visible. */
    val inputs = mutableListOf<PlanGenerationInput>()
    var result: PlanDraft = PlanDraft(
        generatedAt = java.time.LocalDateTime.of(2026, 3, 2, 7, 0),
        segments = emptyList(),
        pendingTaskIds = emptyList(),
        unscheduledTasks = emptyList(),
        orderedTaskIds = emptyList(),
        priorityAssessments = emptyList(),
        hasManualTaskOrder = false,
    )

    override fun generate(input: PlanGenerationInput): PlanDraft {
        inputs += input
        return result
    }
}

class FakeAiSettingsRepository : AiSettingsRepository {
    val state = MutableStateFlow(AiPreferences())
    var observeFlow: Flow<AiPreferences> = state

    var grantConsentCount = 0
    val enabledCalls = mutableListOf<Boolean>()
    var failWith: Throwable? = null

    override fun observe(): Flow<AiPreferences> = observeFlow

    override suspend fun grantConsentAndEnable() {
        failWith?.let { throw it }
        grantConsentCount += 1
    }

    override suspend fun setEnabled(enabled: Boolean) {
        failWith?.let { throw it }
        enabledCalls += enabled
    }
}

class FakeArrangementAssistantAdvisor : ArrangementAssistantAdvisor {
    val requests = mutableListOf<ArrangementAssistantAdviceRequest>()
    var result: ArrangementAssistantAdviceResult =
        ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)

    /** When set, `refine` blocks until the test completes it — the only way to observe a
     *  transient state such as `Testing`. */
    var gate: CompletableDeferred<Unit>? = null
    var failWith: Throwable? = null

    override suspend fun refine(
        request: ArrangementAssistantAdviceRequest,
    ): ArrangementAssistantAdviceResult {
        requests += request
        gate?.await()
        failWith?.let { throw it }
        return result
    }
}

class FakeAiModelCatalog : AiModelCatalog {
    var callCount = 0
    var result: AiModelCatalogResult =
        AiModelCatalogResult.Failed(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)

    /** When set, `listModels` blocks until the test completes it — the only way to observe the
     *  transient `Loading` state. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun listModels(): AiModelCatalogResult {
        callCount++
        gate?.await()
        return result
    }
}

class FakeDataManagementRepository : DataManagementRepository {
    var gate: CompletableDeferred<Unit>? = null
    var clearCount = 0
    var failWith: Throwable? = null

    override suspend fun snapshot(): LocalDataSnapshot {
        gate?.await()
        failWith?.let { throw it }
        return LocalDataSnapshot(
            generatedAtEpochMillis = 1_000L,
            tasks = emptyList(),
            executionLogs = emptyList(),
            weeklyTimeBlocks = emptyList(),
            dateOverrides = emptyList(),
            timeConstraintSettings = TimeConstraintSettings(null, 0L),
            planHistory = emptyList(),
            categoryPreferences = emptyMap(),
            reminderPreferences = ReminderPreferences(),
            aiPreferences = AiPreferences(),
            profileEvidence = emptyList(),
        )
    }

    override suspend fun clearAllLocalData() {
        gate?.await()
        failWith?.let { throw it }
        clearCount += 1
    }
}

class FakeAiProviderConfigRepository : AiProviderConfigRepository {
    val state = MutableStateFlow(AiProviderConfig())
    var observeFlow: Flow<AiProviderConfig> = state

    val saves = mutableListOf<Triple<String, String, String>>()
    var clearApiKeyCount = 0
    var secret: AiProviderSecret? = null
    var failWith: Throwable? = null

    override fun observe(): Flow<AiProviderConfig> = observeFlow

    override suspend fun save(baseUrl: String, model: String, apiKey: String) {
        failWith?.let { throw it }
        saves += Triple(baseUrl, model, apiKey)
    }

    override suspend fun clearApiKey() {
        failWith?.let { throw it }
        clearApiKeyCount += 1
    }

    override suspend fun readSecret(): AiProviderSecret? {
        failWith?.let { throw it }
        return secret
    }
}

/**
 * The provider boundary. `PlanningAgent` takes an `AiAdvisor`, and `PlanningAgentWorkflow`
 * takes a `PlanningAgent` — both concrete classes, but built from interfaces — so a real
 * workflow can be driven in a JVM test without any Android type.
 */
class FakeAiAdvisor : AiAdvisor {
    val requests = mutableListOf<AiAdvisorRequest>()
    var result: AiAdvisorResult =
        AiAdvisorResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
    var gate: CompletableDeferred<Unit>? = null
    var failWith: Throwable? = null

    override suspend fun request(request: AiAdvisorRequest): AiAdvisorResult {
        requests += request
        gate?.await()
        failWith?.let { throw it }
        return result
    }
}

/**
 * An in-memory [TimeRepository].
 *
 * The writes normalise **exactly as `RoomTimeRepository` does** — `title.trim()`, and a blank
 * `weekPattern` stored as null. That is not cosmetic: the timetable import compares a freshly
 * parsed draft against what is already stored, so a fake that kept raw values would make that
 * comparison look right when it is not, and the test would be checking the fake instead of the
 * ViewModel. If the real writer ever changes its normalisation, this has to change with it.
 */
open class FakeTimeRepository : TimeRepository {

    val blocks = MutableStateFlow<List<WeeklyTimeBlock>>(emptyList())
    val overrides = MutableStateFlow<List<DateOverride>>(emptyList())
    val constraintSettings = MutableStateFlow(TimeConstraintSettings(null, 0L))

    /** Every batch handed to a write, in order, unnormalised — so a test can see what was asked for. */
    val savedBlockBatches = mutableListOf<List<WeeklyTimeBlockDraft>>()
    val savedOverrides = mutableListOf<DateOverrideDraft>()
    val deletedBlockIds = mutableListOf<String>()
    val deletedOverrideIds = mutableListOf<String>()
    val savedFirstWeekMondays = mutableListOf<LocalDate?>()
    val restoredSnapshots = mutableListOf<TimeArrangementSnapshot>()

    /** Flattened view of [savedBlockBatches], for the common "how many rows went in" question. */
    val savedBlocks: List<WeeklyTimeBlockDraft> get() = savedBlockBatches.flatten()

    /** Blocks [saveWeeklyBlocks] so a test can see the state while a write is still in flight. */
    var saveBlocksGate: CompletableDeferred<Unit>? = null

    /**
     * Every write in the order it happened.
     *
     * The per-kind lists above cannot answer "did the delete pass finish before the insert pass
     * started?", which is the whole point of `replaceTimetable`'s shape — so sequencing needs one
     * log that all the writers append to.
     */
    val events = mutableListOf<String>()

    private var nextId = 1

    override fun observeWeeklyBlocks(): Flow<List<WeeklyTimeBlock>> = blocks

    override fun observeDateOverrides(): Flow<List<DateOverride>> = overrides

    override fun observeTimeConstraintSettings(): Flow<TimeConstraintSettings> = constraintSettings

    override suspend fun currentWeeklyBlocks(): List<WeeklyTimeBlock> = blocks.value

    override suspend fun currentDateOverrides(): List<DateOverride> = overrides.value

    override suspend fun restoreArrangement(snapshot: TimeArrangementSnapshot) {
        events += "restore(${snapshot.blocks.size},${snapshot.overrides.size})"
        restoredSnapshots += snapshot
        blocks.value = snapshot.blocks.map { it.toBlock() }
        overrides.value = snapshot.overrides.map { it.toOverride() }
    }

    override suspend fun saveWeeklyBlock(draft: WeeklyTimeBlockDraft) = saveWeeklyBlocks(listOf(draft))

    override suspend fun saveWeeklyBlocks(drafts: List<WeeklyTimeBlockDraft>) {
        saveBlocksGate?.await()
        events += "saveBlocks(${drafts.size})"
        savedBlockBatches += drafts
        blocks.value = blocks.value + drafts.map { it.toBlock() }
    }

    override suspend fun deleteWeeklyBlock(id: String) {
        events += "deleteBlock($id)"
        deletedBlockIds += id
        blocks.value = blocks.value.filterNot { it.id == id }
    }

    override suspend fun saveDateOverride(draft: DateOverrideDraft) {
        events += "saveOverride"
        savedOverrides += draft
        overrides.value = overrides.value + draft.toOverride()
    }

    override suspend fun deleteDateOverride(id: String) {
        events += "deleteOverride($id)"
        deletedOverrideIds += id
        overrides.value = overrides.value.filterNot { it.id == id }
    }

    override suspend fun saveSemesterFirstWeekMonday(date: LocalDate?) {
        savedFirstWeekMondays += date
        constraintSettings.value = constraintSettings.value.copy(semesterFirstWeekMonday = date)
    }

    private fun WeeklyTimeBlockDraft.toBlock() = WeeklyTimeBlock(
        id = id ?: "block-${nextId++}",
        title = title.trim(),
        kind = kind,
        dayOfWeek = dayOfWeek,
        startMinute = startMinute,
        endMinute = endMinute,
        weekPattern = weekPattern?.trim()?.takeIf(String::isNotBlank),
        createdAtEpochMillis = createdAtEpochMillis ?: 0L,
        updatedAtEpochMillis = 0L,
        trackId = trackId,
        note = note,
    )

    private fun DateOverrideDraft.toOverride() = DateOverride(
        id = id ?: "override-${nextId++}",
        title = title.trim(),
        type = type,
        date = date,
        startMinute = startMinute,
        endMinute = endMinute,
        createdAtEpochMillis = createdAtEpochMillis ?: 0L,
        updatedAtEpochMillis = 0L,
        note = note,
    )
}
