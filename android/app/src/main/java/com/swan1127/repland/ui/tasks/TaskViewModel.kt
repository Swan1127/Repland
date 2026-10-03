package com.swan1127.repland.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskDraftValidator
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskStatus
import java.time.LocalDate
import com.swan1127.repland.domain.ports.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.swan1127.repland.domain.model.ExecutionSession
import com.swan1127.repland.domain.model.ExecutionOutcome
import com.swan1127.repland.domain.ports.ExecutionSessionRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TaskUiState(
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = true,
    val error: TaskError? = null,
)

enum class TaskError {
    INVALID_DRAFT,
    INVALID_LIFECYCLE_INPUT,
    SAVE_FAILED,
}

class TaskViewModel(
    private val taskRepository: TaskRepository,
    private val sessions: ExecutionSessionRepository? = null,
) : ViewModel() {
    private val actionError = MutableStateFlow<TaskError?>(null)
    private val sessionMutex = Mutex()
    val sessionBusy = MutableStateFlow(false)
    val sessionError = MutableStateFlow<String?>(null)
    val sessionFinished = MutableStateFlow(false)
    fun dismissSessionResult() { sessionFinished.value = false }
    val activeSession: StateFlow<ExecutionSession?> = (sessions?.observeActive() ?: flowOf(null))
        .catch { sessionError.value = "专注记录读取失败，请重试。"; emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun startSession(segmentId: String) = sessionAction { it.start(segmentId); sessionFinished.value = false }
    fun pauseSession(id: String) = sessionAction { it.pause(id) }
    fun resumeSession(id: String) = sessionAction { it.resume(id) }
    fun finishSession(id: String, outcome: ExecutionOutcome, feedback: TaskFeedback = TaskFeedback()) =
        sessionAction { it.finish(id, outcome, feedback); sessionFinished.value = true }

    private fun sessionAction(action: suspend (ExecutionSessionRepository) -> Unit) {
        if (sessionBusy.value) return
        sessionBusy.value = true
        viewModelScope.launch {
            sessionMutex.withLock {
                try {
                    action(requireNotNull(sessions) { "当前版本未配置专注记录。" })
                    sessionError.value = null
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    sessionError.value = error.message ?: "本轮未保存，请重试；原记录仍保留。"
                } finally { sessionBusy.value = false }
            }
        }
    }

    val uiState: StateFlow<TaskUiState> = taskRepository.observeTasks()
        .combine(actionError) { tasks, error -> TaskUiState(tasks = tasks, isLoading = false, error = error) }
        .catch { emit(TaskUiState(isLoading = false, error = TaskError.SAVE_FAILED)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TaskUiState(),
        )

    fun saveTask(draft: TaskDraft) {
        if (!TaskDraftValidator.isValid(draft)) {
            actionError.value = TaskError.INVALID_DRAFT
            return
        }
        viewModelScope.launch {
            runCatching { taskRepository.save(draft) }
                .onSuccess { actionError.value = null }
                .onFailure { actionError.value = TaskError.SAVE_FAILED }
        }
    }

    fun observeExecutionLogs(taskId: String): kotlinx.coroutines.flow.Flow<List<TaskExecutionLog>> =
        taskRepository.observeExecutionLogs(taskId)

    fun observeExecutionLogsForDate(date: LocalDate): kotlinx.coroutines.flow.Flow<List<TaskExecutionLog>> =
        taskRepository.observeExecutionLogsForDate(date)

    fun startTask(taskId: String) = confirmStatus(taskId, TaskStatus.IN_PROGRESS)

    fun postponeTask(taskId: String, reason: String?) =
        confirmStatus(taskId, TaskStatus.POSTPONED, TaskFeedback(postponeReason = reason))

    fun cancelTask(taskId: String) = confirmStatus(taskId, TaskStatus.CANCELLED)

    fun restoreTask(taskId: String) = confirmStatus(taskId, TaskStatus.NOT_STARTED)

    fun completeTask(
        taskId: String,
        completedContent: String? = null,
        completionResult: String? = null,
        actualDurationMinutes: Int? = null,
        progressPercent: Int? = null,
    ) {
        confirmStatus(
            taskId = taskId,
            status = TaskStatus.COMPLETED,
            feedback = TaskFeedback(
                completedContent = completedContent,
                completionResult = completionResult,
                actualDurationMinutes = actualDurationMinutes,
                progressPercent = progressPercent,
            ),
        )
    }

    fun recordPartialCompletion(taskId: String, feedback: TaskFeedback) = lifecycleAction {
        taskRepository.recordPartialCompletion(taskId, feedback)
    }

    fun recordFeedback(taskId: String, feedback: TaskFeedback) = lifecycleAction {
        taskRepository.recordFeedback(taskId, feedback)
    }

    fun correctExecutionLog(taskId: String, correctedLogId: String, feedback: TaskFeedback) = lifecycleAction {
        taskRepository.correctExecutionLog(taskId, correctedLogId, feedback)
    }

    fun replaceTask(taskId: String, replacement: TaskDraft) = lifecycleAction {
        taskRepository.replaceTask(taskId, replacement)
    }

    private fun confirmStatus(
        taskId: String,
        status: TaskStatus,
        feedback: TaskFeedback = TaskFeedback(),
    ) = lifecycleAction {
        taskRepository.confirmStatus(taskId, status, feedback)
    }

    private fun lifecycleAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { actionError.value = null }
                .onFailure { actionError.value = TaskError.INVALID_LIFECYCLE_INPUT }
        }
    }

    class Factory(
        private val taskRepository: TaskRepository,
        private val sessions: ExecutionSessionRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(TaskViewModel::class.java))
            return TaskViewModel(taskRepository, sessions) as T
        }
    }
}
