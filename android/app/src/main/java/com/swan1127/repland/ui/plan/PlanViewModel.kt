package com.swan1127.repland.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PlanUiState(
    val currentPlan: ConfirmedPlan? = null,
    val planHistory: List<ConfirmedPlan> = emptyList(),
    val draft: PlanDraft? = null,
    val errorMessage: String? = null,
    val isWorking: Boolean = false,
    val taskOrder: List<String> = emptyList(),
    val assistantReceipt: AssistantSaveReceipt? = null,
)

data class InteractionWorkspaceUiState(
    val isLoading: Boolean = true,
    val tracks: List<RhythmTrack> = RhythmTracks.defaults,
    val assistant: AssistantWorkspace? = null,
    val canUndoOrder: Boolean = false,
)

class PlanViewModel(
    private val planRepository: PlanRepository,
    private val planDraftGenerator: PlanDraftGenerator,
    private val timeRepository: com.swan1127.repland.domain.ports.TimeRepository? = null,
    private val taskRepository: com.swan1127.repland.domain.ports.TaskRepository? = null,
    private val operations: PlanningOperationService? = null,
) : ViewModel() {
    private val operation = MutableStateFlow<Pair<String?, Boolean>>(null to false)
    private val mutex = Mutex()
    private val assistantReceipt = MutableStateFlow<AssistantSaveReceipt?>(null)
    fun dismissAssistantReceipt() { assistantReceipt.value = null }
    suspend fun queryTasks(scope: TaskQueryScope, date: LocalDate): List<Task> = requireNotNull(operations).query(scope, date)
    suspend fun explainOrder(taskId: String): LocalPriorityAssessment = requireNotNull(operations).explainOrder(taskId)
    val workspaceUiState = combine(planRepository.observeTracks(), planRepository.observeAssistantWorkspace(), planRepository.observeCanUndoTaskOrder()) { tracks, assistant, canUndo ->
        InteractionWorkspaceUiState(false, tracks, assistant, canUndo)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InteractionWorkspaceUiState())

    fun saveTracks(tracks: List<RhythmTrack>) = mutate { planRepository.saveTracks(tracks) }
    fun saveAssistantWorkspace(value: AssistantWorkspace) = mutate { planRepository.saveAssistantWorkspace(value) }
    fun undoTaskOrder() = mutate { planRepository.undoTaskOrder() }
    val uiState: StateFlow<PlanUiState> = combine(
        planRepository.observeCurrentPlan(), planRepository.observePlanHistory(),
        planRepository.observeDraft(), operation, planRepository.observeTaskOrder(),
    ) { current, history, draft, state, order ->
        PlanUiState(current, history, draft, state.first, state.second, order)
    }.combine(assistantReceipt) { state, receipt -> state.copy(assistantReceipt = receipt) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    fun generateDraft(
        tasks: List<Task>,
        weeklyBlocks: List<WeeklyTimeBlock>,
        dateOverrides: List<DateOverride>,
        semesterFirstWeekMonday: LocalDate?,
        lockedSegments: List<PlannedSegment>,
        categoryPreferences: Map<TaskCategory, Int>,
        manualTaskOrder: List<String>,
        orderOnly: Boolean = false,
        todayOnly: LocalDate? = null,
        reorder: Boolean = false,
    ) {
        val input = PlanGenerationInput(tasks, weeklyBlocks, dateOverrides, semesterFirstWeekMonday,
            lockedSegments, categoryPreferences, manualTaskOrder)
        val revision = PlanningRevision.of(input, uiState.value.currentPlan, uiState.value.taskOrder)
        mutate {
            if (operations != null) {
                require(todayOnly == null || todayOnly == LocalDate.now()) { "安排今天只操作今天，请切换到今天后重试。" }
                operations.preview(when {
                    orderOnly -> PlanningPreviewKind.SORT_ONLY
                    todayOnly != null -> PlanningPreviewKind.ARRANGE_TODAY
                    reorder -> PlanningPreviewKind.FORMULATE
                    else -> PlanningPreviewKind.REPLAN_REMAINING
                })
                return@mutate
            }
            val result = if (todayOnly == null) planDraftGenerator.generateReplan(input, uiState.value.currentPlan) else {
                require(todayOnly == LocalDate.now()) { "安排今天只操作今天，请切换到今天后重试。" }
                PlanGenerator.generateToday(input, uiState.value.currentPlan)
            }
            planRepository.saveDraft(result.copy(
                sourceRevision = revision, orderOnly = orderOnly,
                segments = if (orderOnly) emptyList() else result.segments,
                unscheduledTasks = if (orderOnly) emptyList() else result.unscheduledTasks,
            ))
        }
    }

    fun discardDraft() = mutate { planRepository.saveDraft(null) }

    fun saveAvailabilityAndGenerate(value: QuickAvailability, categoryPreferences: Map<TaskCategory, Int>, onSaved: () -> Unit) {
        if (operation.value.second) return
        operation.value = null to true
        mutate {
            require(value.isValid()) { "请填写今天或未来的日期及有效起止时间。" }
            val time = requireNotNull(timeRepository)
            if (value.repeatWeekly) {
                val exists = time.observeWeeklyBlocks().first().any { it.kind == TimeBlockKind.AVAILABLE && it.weekPattern == null &&
                    it.dayOfWeek == value.date.dayOfWeek && it.startMinute == value.startMinute && it.endMinute == value.endMinute }
                if (!exists) time.saveWeeklyBlock(WeeklyTimeBlockDraft(title = "每周可用时间", kind = TimeBlockKind.AVAILABLE,
                    dayOfWeek = value.date.dayOfWeek, startMinute = value.startMinute, endMinute = value.endMinute, trackId = "focus"))
            } else {
                val exists = time.observeDateOverrides().first().any { it.type == DateOverrideType.AVAILABLE && it.date == value.date &&
                    it.startMinute == value.startMinute && it.endMinute == value.endMinute }
                if (!exists) time.saveDateOverride(DateOverrideDraft(title = "本次可用时间", type = DateOverrideType.AVAILABLE,
                    date = value.date, startMinute = value.startMinute, endMinute = value.endMinute))
            }
            // Re-read after saving; generated revision uses persisted IDs, not temporary UI placeholders.
            val current = planRepository.observeCurrentPlan().first()
            val order = planRepository.observeTaskOrder().first()
            val input = PlanGenerationInput(requireNotNull(taskRepository).observeTasks().first(), time.observeWeeklyBlocks().first(),
                time.observeDateOverrides().first(), time.observeTimeConstraintSettings().first().semesterFirstWeekMonday,
                categoryPreferences = categoryPreferences)
            try {
                if (operations != null) operations.preview(PlanningPreviewKind.FORMULATE) else {
                    val result = planDraftGenerator.generateReplan(input, current)
                    planRepository.saveDraft(result.copy(sourceRevision = PlanningRevision.of(input, current, order)))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                throw IllegalArgumentException("可用时间已保存，但预览未生成；请重试，原计划保持不变。", error)
            }
            onSaved()
        }
    }

    fun showAgentDraft(agentDraft: PlanDraft) = mutate {
        if (uiState.value.draft == null) planRepository.saveDraft(agentDraft)
    }

    fun updateDraft(updatedDraft: PlanDraft) = mutate {
        if (uiState.value.draft != null) planRepository.saveDraft(updatedDraft)
    }

    fun acceptDraft(onAccepted: (() -> Unit)? = null) {
        val accepted = uiState.value.draft ?: return
        mutate {
            // A queued second click must not apply an already confirmed draft again.
            if (uiState.value.draft != accepted) return@mutate
            if (operations != null) operations.confirm(accepted) else planRepository.accept(accepted)
            onAccepted?.invoke()
        }
    }

    fun saveTasksAndPlace(tasks: List<TaskDraft>, segments: List<PlannedSegment>, onSaved: () -> Unit) =
        mutate {
            if (operations != null) operations.confirmChanges(tasks, segments, emptySet(), segments.firstOrNull()?.date ?: LocalDate.now())
            else planRepository.saveTasksAndPlace(tasks, segments)
            onSaved()
        }
    fun saveAssistantChanges(tasks: List<TaskDraft>, segments: List<PlannedSegment>, existingIds: Set<String>, date: LocalDate, onSaved: () -> Unit) =
        mutate {
            assistantReceipt.value = null
            val result = operations?.confirmChanges(tasks, segments, existingIds, date)
                ?: planRepository.saveAssistantChanges(tasks, segments, existingIds, date)
            assistantReceipt.value = AssistantSaveReceipt(java.util.UUID.randomUUID().toString(), result)
            onSaved()
        }

    fun restore(planId: String) = mutate { planRepository.restore(planId) }
    fun clearCurrentPlan() = mutate { planRepository.clearCurrentPlan() }
    fun setSegmentLocked(segmentId: String, isLocked: Boolean) = mutate { planRepository.setSegmentLocked(segmentId, isLocked) }
    fun placeTask(taskId: String, date: LocalDate, startMinute: Int, endMinute: Int, trackId: String) =
        mutate { planRepository.placeTask(taskId, date, startMinute, endMinute, trackId) }
    fun movePlacement(segmentId: String, startMinute: Int, endMinute: Int, trackId: String) =
        mutate { planRepository.movePlacement(segmentId, startMinute, endMinute, trackId) }
    fun removePlacement(segmentId: String) = mutate { planRepository.removePlacement(segmentId) }
    fun dismissError() { operation.value = null to operation.value.second }

    private fun mutate(action: suspend () -> Unit) {
        viewModelScope.launch {
            mutex.withLock {
                operation.value = null to true
                try {
                    action()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    operation.value = (if (error is IllegalArgumentException) {
                        error.message ?: "请检查这次安排后重试。"
                    } else "操作未保存，请重试；原计划保持不变。") to true
                } finally {
                    operation.value = operation.value.first to false
                }
            }
        }
    }

    class Factory(private val repository: PlanRepository, private val generator: PlanDraftGenerator,
        private val timeRepository: com.swan1127.repland.domain.ports.TimeRepository? = null,
        private val taskRepository: com.swan1127.repland.domain.ports.TaskRepository? = null,
        private val operations: PlanningOperationService? = null) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(PlanViewModel::class.java))
            return PlanViewModel(repository, generator, timeRepository, taskRepository, operations) as T
        }
    }
}
