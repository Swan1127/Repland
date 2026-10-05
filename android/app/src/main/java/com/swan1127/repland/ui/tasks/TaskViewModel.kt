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
import kotlinx.coroutines.flow.map
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
    val hasLoaded: Boolean = false,
    val readError: String? = null,
) {
    val isTrusted: Boolean get() = hasLoaded && !isLoading && readError == null
}

enum class TaskError {
    INVALID_DRAFT,
    INVALID_LIFECYCLE_INPUT,
    SAVE_FAILED,
}

data class TaskEditorSaveState(
    val saving: Boolean = false,
    val error: String? = null,
    val receipt: String? = null,
)

enum class TaskMutationKind { START, COMPLETE, POSTPONE, CANCEL, RESTORE, PARTIAL, FEEDBACK, CORRECT, REPLACE }

data class TaskMutationState(
    val kind: TaskMutationKind? = null,
    val taskId: String? = null,
    val logId: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val receipt: String? = null,
)

class TaskViewModel(
    private val taskRepository: TaskRepository,
    private val sessions: ExecutionSessionRepository? = null,
) : ViewModel() {
    private val actionError = MutableStateFlow<TaskError?>(null)
    private val editorState = MutableStateFlow(TaskEditorSaveState())
    val editorSaveState: StateFlow<TaskEditorSaveState> = editorState
    private val mutation = MutableStateFlow(TaskMutationState())
    val mutationState: StateFlow<TaskMutationState> = mutation
    private var retryMutation: (() -> Unit)? = null
    fun resetMutation() {
        if (!mutation.value.busy) { mutation.value = TaskMutationState(); retryMutation = null }
    }
    fun retryMutation() { if (!mutation.value.busy && mutation.value.receipt == null) retryMutation?.invoke() }
    fun resetEditorResult() { if (!editorState.value.saving) editorState.value = TaskEditorSaveState() }
    private val sessionMutex = Mutex()
    val sessionBusy = MutableStateFlow(false)
    val sessionError = MutableStateFlow<String?>(null)
    val sessionFinished = MutableStateFlow(false)
    fun dismissSessionResult() { sessionFinished.value = false }
    private val sessionReadRetries = MutableStateFlow(0)
    val sessionReadState = com.swan1127.repland.ui.state.recoverableRead<ExecutionSession?>(
        initial = null, retries = sessionReadRetries, errorMessage = "专注记录读取失败；原会话仍保留，请重新读取后操作。",
        source = { sessions?.observeActive() ?: flowOf(null) },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.swan1127.repland.ui.state.ReadSnapshot(null))
    val activeSession: StateFlow<ExecutionSession?> = sessionReadState.map { it.value }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun retrySessionRead() {
        if (!sessionReadState.value.isLoading && sessionReadState.value.error != null) sessionReadRetries.value++
    }

    fun startSession(segmentId: String) = sessionAction { it.start(segmentId); sessionFinished.value = false }
    fun pauseSession(id: String) = sessionAction { it.pause(id) }
    fun resumeSession(id: String) = sessionAction { it.resume(id) }
    fun finishSession(id: String, outcome: ExecutionOutcome, feedback: TaskFeedback = TaskFeedback()) =
        sessionAction { it.finish(id, outcome, feedback); sessionFinished.value = true }

    private fun sessionAction(action: suspend (ExecutionSessionRepository) -> Unit) {
        if (sessionBusy.value) return
        if (sessionReadState.value.error != null || (sessionReadState.value.hasLoaded && sessionReadState.value.isLoading) ||
            uiState.value.readError != null || (uiState.value.hasLoaded && uiState.value.isLoading)) {
            sessionError.value = "请先重新读取专注与任务；本轮未改变。"
            return
        }
        sessionBusy.value = true
        viewModelScope.launch {
            sessionMutex.withLock {
                try {
                    check(sessionReadState.value.error == null && !(sessionReadState.value.hasLoaded && sessionReadState.value.isLoading)) {
                        "请先重新读取专注；本轮未改变。"
                    }
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

    private val readRetries = MutableStateFlow(0)
    val uiState: StateFlow<TaskUiState> = com.swan1127.repland.ui.state.recoverableRead(
        initial = emptyList<Task>(), retries = readRetries, errorMessage = "任务读取失败，请重试；未清空任务库。",
        source = taskRepository::observeTasks,
    ).combine(actionError) { read, error ->
        TaskUiState(read.value, read.isLoading, error, read.hasLoaded, read.error)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TaskUiState(),
        )

    fun retryRead() { if (!uiState.value.isLoading && uiState.value.readError != null) readRetries.value++ }

    fun saveTask(draft: TaskDraft) {
        if (editorState.value.saving || editorState.value.receipt != null) return
        if (uiState.value.readError != null || (uiState.value.hasLoaded && uiState.value.isLoading)) {
            editorState.value = TaskEditorSaveState(error = "请先重新读取任务；输入仍保留，本次未保存。")
            return
        }
        if (!TaskDraftValidator.isValid(draft)) {
            actionError.value = TaskError.INVALID_DRAFT
            editorState.value = TaskEditorSaveState(error = "请检查名称、天数和时长，填写内容仍保留。")
            return
        }
        editorState.value = TaskEditorSaveState(saving = true)
        viewModelScope.launch {
            try {
                if (draft.id == null) taskRepository.save(draft) else taskRepository.updateExisting(draft)
                actionError.value = null
                editorState.value = TaskEditorSaveState(receipt = "已保存“${draft.displayName.trim().ifBlank { draft.description.trim() }}”的任务信息；现有时段未重新安排。")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                editorState.value = TaskEditorSaveState()
                throw cancelled
            } catch (error: Exception) {
                actionError.value = TaskError.SAVE_FAILED
                editorState.value = TaskEditorSaveState(error = "保存未成功，填写内容仍保留。请重试；若任务已被移除，请返回任务页。")
            }
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

    fun recordPartialCompletion(taskId: String, feedback: TaskFeedback) = lifecycleAction(TaskMutationKind.PARTIAL, taskId) {
        taskRepository.recordPartialCompletion(taskId, feedback)
    }

    fun recordFeedback(taskId: String, feedback: TaskFeedback) = lifecycleAction(TaskMutationKind.FEEDBACK, taskId) {
        taskRepository.recordFeedback(taskId, feedback)
    }

    fun correctExecutionLog(taskId: String, correctedLogId: String, feedback: TaskFeedback) = lifecycleAction(TaskMutationKind.CORRECT, taskId, correctedLogId) {
        taskRepository.correctExecutionLog(taskId, correctedLogId, feedback)
    }

    fun replaceTask(taskId: String, replacement: TaskDraft) = lifecycleAction(TaskMutationKind.REPLACE, taskId) {
        taskRepository.replaceTask(taskId, replacement)
    }

    private fun confirmStatus(
        taskId: String,
        status: TaskStatus,
        feedback: TaskFeedback = TaskFeedback(),
    ) = lifecycleAction(when (status) {
        TaskStatus.IN_PROGRESS -> TaskMutationKind.START
        TaskStatus.COMPLETED -> TaskMutationKind.COMPLETE
        TaskStatus.POSTPONED -> TaskMutationKind.POSTPONE
        TaskStatus.CANCELLED -> TaskMutationKind.CANCEL
        TaskStatus.NOT_STARTED -> TaskMutationKind.RESTORE
        TaskStatus.REPLACED -> TaskMutationKind.REPLACE
    }, taskId) {
        taskRepository.confirmStatus(taskId, status, feedback)
    }

    private fun lifecycleAction(kind: TaskMutationKind, taskId: String, logId: String? = null, action: suspend () -> Unit) {
        if (mutation.value.busy || mutation.value.receipt != null) return
        retryMutation = { lifecycleAction(kind, taskId, logId, action) }
        if (uiState.value.readError != null || (uiState.value.hasLoaded && uiState.value.isLoading)) {
            mutation.value = TaskMutationState(kind, taskId, logId, error = "请先重新读取任务；输入和原记录仍保留。")
            return
        }
        mutation.value = TaskMutationState(kind, taskId, logId, busy = true)
        viewModelScope.launch {
            try {
                action()
                actionError.value = null
                val message = when (kind) {
                    TaskMutationKind.START -> "已记录开始；不会根据时间经过自动完成。"
                    TaskMutationKind.COMPLETE -> "已确认完成；不会以计划时长或计时推断实际耗时。"
                    TaskMutationKind.POSTPONE -> "已记录本次推迟；现有计划未自动重新安排。"
                    TaskMutationKind.CANCEL -> "已取消任务，执行历史仍保留。"
                    TaskMutationKind.RESTORE -> "已恢复为未开始，执行历史仍保留。"
                    TaskMutationKind.PARTIAL -> "已保存部分进度；未将整个任务标记完成。"
                    TaskMutationKind.FEEDBACK -> "已保存反馈；任务状态未改变。"
                    TaskMutationKind.CORRECT -> "已追加纠正记录；原始历史仍保留。"
                    TaskMutationKind.REPLACE -> "已保存替换事项；原任务和历史仍保留。"
                }
                mutation.value = TaskMutationState(kind, taskId, logId, receipt = message)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                mutation.value = TaskMutationState(kind, taskId, logId)
                retryMutation = null
                throw cancelled
            } catch (error: Exception) {
                actionError.value = TaskError.INVALID_LIFECYCLE_INPUT
                mutation.value = TaskMutationState(kind, taskId, logId,
                    error = "本次记录未保存，输入仍保留。请重试；若原任务或记录已不存在，请关闭后重新选择。")
            }
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
