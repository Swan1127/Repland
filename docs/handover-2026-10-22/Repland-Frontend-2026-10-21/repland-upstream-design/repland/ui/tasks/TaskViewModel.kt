package com.swan1127.repland.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.SubTask
import com.swan1127.repland.domain.model.SubTaskValidator
import com.swan1127.repland.domain.model.SubTasks
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskDraftValidator
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskStatus
import java.time.LocalDate
import com.swan1127.repland.domain.ports.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
) : ViewModel() {
    private val actionError = MutableStateFlow<TaskError?>(null)

    val uiState: StateFlow<TaskUiState> = taskRepository.observeTasks()
        .combine(actionError) { tasks, error -> TaskUiState(tasks = tasks, isLoading = false, error = error) }
        .catch { emit(TaskUiState(isLoading = false, error = TaskError.SAVE_FAILED)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TaskUiState(),
        )

    fun saveTask(draft: TaskDraft) {
        viewModelScope.launch { saveTaskAwait(draft) }
    }

    suspend fun saveTaskAwait(draft: TaskDraft): Boolean {
        if (!TaskDraftValidator.isValid(draft)) {
            actionError.value = TaskError.INVALID_DRAFT
            return false
        }
        return try {
            taskRepository.save(draft)
            actionError.value = null
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            actionError.value = TaskError.SAVE_FAILED
            false
        }
    }

    fun observeExecutionLogs(taskId: String): kotlinx.coroutines.flow.Flow<List<TaskExecutionLog>> =
        taskRepository.observeExecutionLogs(taskId)

    fun observeExecutionLogsForDate(date: LocalDate): kotlinx.coroutines.flow.Flow<List<TaskExecutionLog>> =
        taskRepository.observeExecutionLogsForDate(date)

    fun observeExecutionLogsSince(from: LocalDate): kotlinx.coroutines.flow.Flow<List<TaskExecutionLog>> =
        taskRepository.observeExecutionLogsSince(from)

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

    // --- batch ---------------------------------------------------------------------------------

    /**
     * Applies one lifecycle change to every selected row that can take it, in list order.
     *
     * **The eligibility filter is re-run here, against the list as it is right now, rather than
     * trusting the ids the screen sends.** The two disagree only in a race — the task list re-emits
     * between the confirm dialog opening and the user tapping the confirm button — but that race is
     * exactly when a row can have become `REPLACED`, and `RoomTaskRepository.confirmStatus` rejects
     * that transition with a `require`. A batch that trusted the screen would throw part-way through
     * the loop: the rows before the throw would be written, the rest would not, and the only signal
     * would be a generic `INVALID_LIFECYCLE_INPUT` flag — a half-applied batch with no explanation
     * of which half. Re-deriving costs one pass over a list already in memory and makes every call
     * in the loop a call the repository will accept.
     *
     * Reads [uiState]'s current value, which is the same list the screen is rendering; like
     * [subtaskWrite] it is a no-op when nothing is subscribed yet.
     */
    fun confirmStatuses(action: BatchTaskAction, selection: TaskSelection) = lifecycleAction {
        BatchTasks.targets(action, selection, uiState.value.tasks)
            .forEach { task -> taskRepository.confirmStatus(task.id, action.status) }
    }

    // --- subtasks --------------------------------------------------------------------------

    fun addSubTask(taskId: String, title: String) {
        if (!SubTaskValidator.isValidTitle(title)) {
            actionError.value = TaskError.INVALID_DRAFT
            return
        }
        subtaskWrite(taskId) { task ->
            taskRepository.setSubTasks(taskId, SubTasks.add(task, title, System.currentTimeMillis()).subtasks)
        }
    }

    fun toggleSubTask(taskId: String, subTaskId: String, isDone: Boolean) = subtaskWrite(taskId) { task ->
        taskRepository.setSubTasks(taskId, SubTasks.toggle(task, subTaskId, isDone).subtasks)
    }

    fun removeSubTask(taskId: String, subTaskId: String) = subtaskWrite(taskId) { task ->
        taskRepository.setSubTasks(taskId, SubTasks.remove(task, subTaskId).subtasks)
    }

    /**
     * Reads the current task from the in-memory state, applies a pure [SubTasks] transform, and
     * persists the result. The repository flow re-emits and the UI updates on the next emission.
     */
    private fun subtaskWrite(taskId: String, transform: suspend (Task) -> Unit) {
        viewModelScope.launch {
            val task = uiState.value.tasks.firstOrNull { it.id == taskId } ?: return@launch
            runCatching { transform(task) }
                .onFailure { actionError.value = TaskError.INVALID_LIFECYCLE_INPUT }
        }
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(TaskViewModel::class.java))
            return TaskViewModel(taskRepository) as T
        }
    }
}
