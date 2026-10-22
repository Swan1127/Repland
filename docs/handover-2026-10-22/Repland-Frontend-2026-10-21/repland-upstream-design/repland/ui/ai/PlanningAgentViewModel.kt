package com.swan1127.repland.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.AiPreferences
import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.PlanGenerationInput
import com.swan1127.repland.domain.model.PlanningAgentAccess
import com.swan1127.repland.domain.model.PlanningAgentState
import com.swan1127.repland.domain.model.PlanningAgentWork
import com.swan1127.repland.domain.model.PlanningAgentWorkflow
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.ports.AiSettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class PlanningAgentUiState(
    val preferences: AiPreferences = AiPreferences(),
    val workflow: PlanningAgentState = PlanningAgentState.Idle,
    val isLoading: Boolean = true,
)

/** Compose hands this ViewModel local records only; it never builds an AI wire request. */
class PlanningAgentViewModel(
    private val settingsRepository: AiSettingsRepository,
    private val workflow: PlanningAgentWorkflow,
) : ViewModel() {
    private var activeRequest: Job? = null
    val uiState: StateFlow<PlanningAgentUiState> = combine(
        settingsRepository.observe(),
        workflow.state,
    ) { preferences, workflowState ->
        PlanningAgentUiState(preferences = preferences, workflow = workflowState, isLoading = false)
    }.catch {
        emit(
            PlanningAgentUiState(
                isLoading = false,
                workflow = PlanningAgentState.Idle,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlanningAgentUiState(),
    )

    fun beginTaskUnderstanding(
        task: Task,
        executionLogs: List<TaskExecutionLog>,
        currentPlan: ConfirmedPlan?,
    ) = begin(PlanningAgentWork.TaskUnderstanding(task, executionLogs, currentPlan))

    fun beginDifficultyAndDuration(
        task: Task,
        executionLogs: List<TaskExecutionLog>,
        currentPlan: ConfirmedPlan?,
    ) = begin(PlanningAgentWork.DifficultyAndDuration(task, executionLogs, currentPlan))

    fun beginTaskBreakdown(
        task: Task,
        executionLogs: List<TaskExecutionLog>,
        currentPlan: ConfirmedPlan?,
    ) = begin(PlanningAgentWork.TaskBreakdown(task, executionLogs, currentPlan))

    fun beginSortingExplanation(
        task: Task,
        executionLogs: List<TaskExecutionLog>,
        currentPlan: ConfirmedPlan?,
        localRankingReasons: List<String>,
    ) = begin(
        PlanningAgentWork.SortingExplanation(task, executionLogs, currentPlan, localRankingReasons),
    )

    fun beginReplan(
        affectedTasks: List<Task>,
        executionLogs: List<TaskExecutionLog>,
        currentPlan: ConfirmedPlan?,
        localPlanInput: PlanGenerationInput,
        constraintSummary: List<String>,
    ) = begin(
        PlanningAgentWork.Replan(
            affectedTasks = affectedTasks,
            executionLogs = executionLogs,
            currentPlan = currentPlan,
            localPlanInput = localPlanInput,
            constraintSummary = constraintSummary,
        ),
    )

    fun beginDailySummary(day: LocalDate, confirmedLogs: List<TaskExecutionLog>) = begin(
        PlanningAgentWork.DailySummary(day, confirmedLogs),
    )

    fun grantConsentAndEnable() {
        viewModelScope.launch {
            runCatching { settingsRepository.grantConsentAndEnable() }
                .onSuccess { workflow.resumeAfterConsent(PlanningAgentAccess(isEnabled = true, hasExplicitConsent = true)) }
        }
    }

    fun setEnabled(enabled: Boolean) {
        val existing = uiState.value.preferences
        if (!enabled) {
            activeRequest?.cancel()
            activeRequest = null
            workflow.disable()
        }
        viewModelScope.launch {
            runCatching { settingsRepository.setEnabled(enabled) }
                .onSuccess {
                    workflow.resumeAfterConsent(
                        PlanningAgentAccess(isEnabled = enabled, hasExplicitConsent = existing.hasExplicitConsent),
                    )
                }
        }
    }

    fun confirmPreview() {
        activeRequest?.cancel()
        activeRequest = viewModelScope.launch {
            try {
                workflow.confirmPreview(uiState.value.preferences.toAccess())
            } finally {
                activeRequest = null
            }
        }
    }

    fun dismiss() {
        activeRequest?.cancel()
        activeRequest = null
        workflow.dismiss()
    }

    /** Called only after the existing PlanViewModel has been asked to confirm an unconfirmed draft. */
    fun markAccepted() = workflow.markAccepted()

    private fun begin(work: PlanningAgentWork) {
        workflow.begin(work, uiState.value.preferences.toAccess())
    }

    private fun AiPreferences.toAccess(): PlanningAgentAccess = PlanningAgentAccess(
        isEnabled = isEnabled,
        hasExplicitConsent = hasExplicitConsent,
    )

    class Factory(
        private val settingsRepository: AiSettingsRepository,
        private val workflow: PlanningAgentWorkflow,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(PlanningAgentViewModel::class.java))
            return PlanningAgentViewModel(settingsRepository, workflow) as T
        }
    }
}
