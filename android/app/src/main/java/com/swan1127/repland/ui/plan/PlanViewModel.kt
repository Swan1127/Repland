package com.swan1127.repland.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlanDraftGenerator
import com.swan1127.repland.domain.model.PlanGenerationInput
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.ports.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlanUiState(
    val currentPlan: ConfirmedPlan? = null,
    val planHistory: List<ConfirmedPlan> = emptyList(),
    val draft: PlanDraft? = null,
)

class PlanViewModel(
    private val planRepository: PlanRepository,
    private val planDraftGenerator: PlanDraftGenerator,
) : ViewModel() {
    private val draft = MutableStateFlow<PlanDraft?>(null)

    val uiState: StateFlow<PlanUiState> = combine(
        planRepository.observeCurrentPlan(),
        planRepository.observePlanHistory(),
        draft,
    ) { currentPlan, planHistory, planDraft ->
        PlanUiState(currentPlan = currentPlan, planHistory = planHistory, draft = planDraft)
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
    }

    /** An Agent result stays a draft and never overwrites a draft the user is already editing. */
    fun showAgentDraft(agentDraft: PlanDraft) {
        if (draft.value == null) draft.value = agentDraft
    }

    /** Keeps edits in an unconfirmed draft only; the current plan is never mutated here. */
    fun updateDraft(updatedDraft: PlanDraft) {
        if (draft.value != null) draft.value = updatedDraft
    }

    fun acceptDraft(onAccepted: (() -> Unit)? = null) {
        val acceptedDraft = draft.value ?: return
        viewModelScope.launch {
            runCatching { planRepository.accept(acceptedDraft) }
                .onSuccess {
                    draft.value = null
                    onAccepted?.invoke()
                }
        }
    }

    fun restore(planId: String) {
        viewModelScope.launch { planRepository.restore(planId) }
    }

    fun clearCurrentPlan() {
        viewModelScope.launch { planRepository.clearCurrentPlan() }
    }

    fun setSegmentLocked(segmentId: String, isLocked: Boolean) {
        viewModelScope.launch { planRepository.setSegmentLocked(segmentId, isLocked) }
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
