package com.swan1127.repland.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.ArrangementConflictException
import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlanDraftGenerator
import com.swan1127.repland.domain.model.PlanGenerationInput
import com.swan1127.repland.domain.model.PlanLockedException
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.ports.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.CancellationException

data class PlanUiState(
    val currentPlan: ConfirmedPlan? = null,
    val planHistory: List<ConfirmedPlan> = emptyList(),
    val draft: PlanDraft? = null,
    val failureMessage: String? = null,
)

class PlanViewModel(
    private val planRepository: PlanRepository,
    private val planDraftGenerator: PlanDraftGenerator,
) : ViewModel() {
    private val mutationJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private val mutationScope = CoroutineScope(viewModelScope.coroutineContext + mutationJob)

    /** Cancels queued/in-flight writes without stopping the observed UI state. */
    fun cancelPendingMutations() {
        mutationJob.cancelChildren()
        failureMessage.value = null
    }

    private val draft = MutableStateFlow<PlanDraft?>(null)
    private val failureMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PlanUiState> = combine(
        planRepository.observeCurrentPlan(),
        planRepository.observePlanHistory(),
        draft,
        failureMessage,
    ) { currentPlan, planHistory, planDraft, failure ->
        PlanUiState(currentPlan = currentPlan, planHistory = planHistory, draft = planDraft, failureMessage = failure)
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(PlanUiState(draft = draft.value, failureMessage = "计划读取失败，请稍后重试。"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlanUiState(),
    )

    fun generateDraft(
        tasks: List<Task>,
        weeklyBlocks: List<WeeklyTimeBlock>,
        dateOverrides: List<DateOverride>,
        semesterFirstWeekMonday: LocalDate?,
        lockedSegments: List<com.swan1127.repland.domain.model.PlannedSegment>,
        categoryPreferences: Map<TaskCategory, Int>,
        manualTaskOrder: List<String>,
    ) {
        failureMessage.value = null
        draft.value = planDraftGenerator.generate(PlanGenerationInput(
            tasks = tasks,
            weeklyBlocks = weeklyBlocks,
            dateOverrides = dateOverrides,
            semesterFirstWeekMonday = semesterFirstWeekMonday,
            lockedSegments = lockedSegments,
            categoryPreferences = categoryPreferences,
            manualTaskOrder = manualTaskOrder,
        ))
    }

    fun discardDraft() {
        draft.value = null
        failureMessage.value = null
    }

    /** An Agent result stays a draft and never overwrites a draft the user is already editing. */
    fun showAgentDraft(agentDraft: PlanDraft) {
        if (draft.value == null) {
            draft.value = agentDraft
            failureMessage.value = null
        }
    }

    /** Keeps edits in an unconfirmed draft only; the current plan is never mutated here. */
    fun updateDraft(updatedDraft: PlanDraft) {
        if (draft.value != null) {
            draft.value = updatedDraft
            failureMessage.value = null
        }
    }

    fun acceptDraft(onAccepted: (() -> Unit)? = null) {
        val acceptedDraft = draft.value ?: return
        mutate(onSuccess = {
            if (draft.value == acceptedDraft) draft.value = null
            onAccepted?.invoke()
        }) {
            planRepository.accept(acceptedDraft)
        }
    }

    fun restore(planId: String) {
        mutate { planRepository.restore(planId) }
    }

    fun clearCurrentPlan() {
        mutate { planRepository.clearCurrentPlan() }
    }

    fun setSegmentLocked(segmentId: String, isLocked: Boolean) {
        mutate { planRepository.setSegmentLocked(segmentId, isLocked) }
    }

    fun placeTask(taskId: String, date: LocalDate, startMinute: Int, endMinute: Int, trackId: String) {
        mutate { planRepository.placeTask(taskId, date, startMinute, endMinute, trackId) }
    }

    fun movePlacement(segmentId: String, startMinute: Int, endMinute: Int, trackId: String) {
        mutate { planRepository.movePlacement(segmentId, startMinute, endMinute, trackId) }
    }

    fun removePlacement(segmentId: String) {
        mutate { planRepository.removePlacement(segmentId) }
    }

    /** Only typed domain failures may provide user-facing text; storage details remain private. */
    private fun mutate(onSuccess: () -> Unit = {}, action: suspend () -> Unit) {
        mutationScope.launch {
            failureMessage.value = null
            try {
                action()
                failureMessage.value = null
                onSuccess()
            } catch (cancelled: CancellationException) {
                failureMessage.value = null
                throw cancelled
            } catch (conflict: ArrangementConflictException) {
                failureMessage.value = conflict.userMessage
            } catch (locked: PlanLockedException) {
                failureMessage.value = locked.userMessage
            } catch (_: Exception) {
                failureMessage.value = "计划保存失败，请重试。"
            }
        }
    }

    class Factory(
        private val planRepository: PlanRepository,
        private val planDraftGenerator: PlanDraftGenerator,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(PlanViewModel::class.java))
            return PlanViewModel(planRepository, planDraftGenerator) as T
        }
    }
}
