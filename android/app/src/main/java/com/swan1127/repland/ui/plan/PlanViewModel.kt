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
) : ViewModel() {
    private val operation = MutableStateFlow<Pair<String?, Boolean>>(null to false)
    private val mutex = Mutex()
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

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
    ) {
        val input = PlanGenerationInput(tasks, weeklyBlocks, dateOverrides, semesterFirstWeekMonday,
            lockedSegments, categoryPreferences, manualTaskOrder)
        val revision = PlanningRevision.of(input, uiState.value.currentPlan, uiState.value.taskOrder)
        mutate {
            val result = if (todayOnly == null) planDraftGenerator.generate(input) else {
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
            planRepository.accept(accepted)
            onAccepted?.invoke()
        }
    }

    fun saveTasksAndPlace(tasks: List<TaskDraft>, segments: List<PlannedSegment>, onSaved: () -> Unit) =
        mutate { planRepository.saveTasksAndPlace(tasks, segments); onSaved() }

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

    class Factory(private val repository: PlanRepository, private val generator: PlanDraftGenerator) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(PlanViewModel::class.java))
            return PlanViewModel(repository, generator) as T
        }
    }
}
