package com.swan1127.repland.testkit

import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

internal class PlanningOperationFixture(
    val clock: Clock = Clock.fixed(Instant.parse("2026-10-22T07:00:00Z"), ZoneOffset.UTC),
    initialTasks: List<Task> = listOf(task("task")),
    initialBlocks: List<WeeklyTimeBlock> = listOf(
        weeklyBlock(dayOfWeek = LocalDate.now(clock).dayOfWeek, startMinute = 480, endMinute = 720),
    ),
) {
    val tasks = MutableStateFlow(initialTasks)
    val logs = MutableStateFlow(emptyList<TaskExecutionLog>())
    val plans = MemoryPlanRepository()
    val time = MemoryTimeRepository(initialBlocks)
    val preferences = MemoryPreferenceRepository()
    val sessions = MemoryExecutionSessionRepository()
    var memory: MemoryReference? = null
    var beforeLogs: suspend () -> Unit = {}
    val taskRepository: TaskRepository = object : TaskRepository {
        override fun observeTasks() = tasks
        override fun observeExecutionLogs(taskId: String) = MutableStateFlow(logs.value.filter { it.taskId == taskId })
        override fun observeExecutionLogsForDate(date: LocalDate) = logs
        override suspend fun save(draft: TaskDraft) = unsupported()
        override suspend fun updateExisting(draft: TaskDraft) = unsupported()
        override suspend fun confirmStatus(taskId: String, status: TaskStatus, feedback: TaskFeedback) = unsupported()
        override suspend fun recordFeedback(taskId: String, feedback: TaskFeedback) = unsupported()
        override suspend fun recordPartialCompletion(taskId: String, feedback: TaskFeedback) = unsupported()
        override suspend fun correctExecutionLog(taskId: String, correctedLogId: String, feedback: TaskFeedback) = unsupported()
        override suspend fun replaceTask(taskId: String, replacement: TaskDraft): String = unsupported()
    }
    val reads = PlanningReadService(taskRepository, time, plans, preferences) { LocalDateTime.now(clock) }

    fun service(
        generator: PlanDraftGenerator = PlanGenerator,
        memory: (LocalDate) -> MemoryReference? = { this.memory },
        planningDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        numericProfile: NumericProfileRepository? = null,
    ) = PlanningOperationService(
        reads, plans, generator, sessions, numericProfile,
        memoryReference = memory,
        executionLogs = { beforeLogs(); logs.value },
        clock = clock,
        planningDispatcher = planningDispatcher,
    )
}

internal class MemoryPlanRepository : PlanRepository {
    val draft = MutableStateFlow<PlanDraft?>(null)
    val current = MutableStateFlow<ConfirmedPlan?>(null)
    val order = MutableStateFlow(emptyList<String>())
    val history = MutableStateFlow(emptyList<ConfirmedPlan>())
    val tracks = MutableStateFlow(RhythmTracks.defaults)
    val workspace = MutableStateFlow<AssistantWorkspace?>(null)
    val canUndo = MutableStateFlow(false)
    var saveDraftCalls = 0
    var acceptCalls = 0
    var acceptFailure: Throwable? = null
    var saveDraftFailure: Throwable? = null
    val acceptedDrafts = mutableListOf<PlanDraft>()

    override fun observeDraft() = draft
    override fun observeCurrentPlan() = current
    override fun observePlanHistory() = history
    override fun observeTaskOrder() = order
    override fun observeCanUndoTaskOrder() = canUndo
    override fun observeTracks() = tracks
    override fun observeAssistantWorkspace() = workspace
    override suspend fun saveDraft(draft: PlanDraft?) {
        saveDraftCalls++
        saveDraftFailure?.let { throw it }
        this.draft.value = draft
    }
    override suspend fun replaceDraftIfCurrent(expected: PlanDraft, updated: PlanDraft) {
        require(draft.value == expected)
        require(expected.generatedAt == updated.generatedAt && expected.sourceRevision == updated.sourceRevision)
        draft.value = updated
    }
    override suspend fun accept(draft: PlanDraft) {
        acceptCalls++
        acceptFailure?.let { throw it }
        require(this.draft.value == draft) { "The draft was replaced or already accepted." }
        acceptedDrafts += draft
        current.value = ConfirmedPlan("accepted-$acceptCalls", 1L, true, draft.segments, draft.orderedTaskIds, draft.hasManualTaskOrder)
        history.value = history.value + requireNotNull(current.value)
        this.draft.value = null
    }
    override suspend fun saveTaskOrder(ids: List<String>) { order.value = ids }
    override suspend fun saveTracks(tracks: List<RhythmTrack>) { this.tracks.value = tracks }
    override suspend fun saveAssistantWorkspace(value: AssistantWorkspace?) { workspace.value = value }
    override suspend fun clearCurrentPlan() { current.value = null }
    override suspend fun restore(planId: String) = unsupported()
    override suspend fun undoTaskOrder() = unsupported()
    override suspend fun saveTasksAndPlace(tasks: List<TaskDraft>, segments: List<PlannedSegment>) = unsupported()
    override suspend fun saveAssistantChanges(tasks: List<TaskDraft>, segments: List<PlannedSegment>, existingTaskIds: Set<String>, date: LocalDate): AssistantSaveResult = unsupported()
    override suspend fun setSegmentLocked(segmentId: String, isLocked: Boolean) = unsupported()
    override suspend fun placeTask(taskId: String, date: LocalDate, startMinute: Int, endMinute: Int, trackId: String) = unsupported()
    override suspend fun movePlacement(segmentId: String, startMinute: Int, endMinute: Int, trackId: String) = unsupported()
    override suspend fun removePlacement(segmentId: String) = unsupported()
}

internal class MemoryTimeRepository(initialBlocks: List<WeeklyTimeBlock>) : TimeRepository {
    val blocks = MutableStateFlow(initialBlocks)
    val overrides = MutableStateFlow(emptyList<DateOverride>())
    val settings = MutableStateFlow(TimeConstraintSettings(null, 0))
    override fun observeWeeklyBlocks() = blocks
    override fun observeDateOverrides() = overrides
    override fun observeTimeConstraintSettings() = settings
    override suspend fun saveWeeklyBlock(draft: WeeklyTimeBlockDraft) = unsupported()
    override suspend fun saveWeeklyBlocks(drafts: List<WeeklyTimeBlockDraft>) = unsupported()
    override suspend fun importWeeklyBlocks(drafts: List<WeeklyTimeBlockDraft>): Int = unsupported()
    override suspend fun deleteWeeklyBlock(id: String) = unsupported()
    override suspend fun saveDateOverride(draft: DateOverrideDraft) = unsupported()
    override suspend fun deleteDateOverride(id: String) = unsupported()
    override suspend fun saveSemesterFirstWeekMonday(date: LocalDate?) { settings.value = settings.value.copy(semesterFirstWeekMonday = date) }
}

internal class MemoryPreferenceRepository : CategoryPreferenceRepository {
    val weights = MutableStateFlow(CategoryPreferences.defaults)
    override fun observe() = weights
    override suspend fun save(weights: Map<TaskCategory, Int>) { this.weights.value = weights }
}

internal class MemoryExecutionSessionRepository : ExecutionSessionRepository {
    val active = MutableStateFlow<ExecutionSession?>(null)
    override fun observeActive() = active
    override suspend fun start(segmentId: String) = unsupported()
    override suspend fun pause(sessionId: String) = unsupported()
    override suspend fun resume(sessionId: String) = unsupported()
    override suspend fun finish(sessionId: String, outcome: ExecutionOutcome, feedback: TaskFeedback) = unsupported()
}

private fun unsupported(): Nothing = error("This planning fixture does not mutate task or execution facts.")
