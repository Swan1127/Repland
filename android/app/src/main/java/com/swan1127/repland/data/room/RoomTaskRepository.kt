package com.swan1127.repland.data.room

import androidx.room.withTransaction
import com.swan1127.repland.domain.model.ExecutionLogEventType
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskDraftValidator
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskLifecycleValidator
import com.swan1127.repland.domain.model.TaskName
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.ports.TaskRepository
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTaskRepository(
    private val database: ReplandDatabase,
) : TaskRepository {
    private val taskDao = database.taskDao()
    private val executionLogDao = database.executionLogDao()

    override fun observeTasks(): Flow<List<com.swan1127.repland.domain.model.Task>> =
        taskDao.observeAll().map { entities -> entities.map(TaskEntity::toDomain) }

    override fun observeExecutionLogs(taskId: String): Flow<List<TaskExecutionLog>> =
        executionLogDao.observeForTask(taskId).map { logs -> logs.map(ExecutionLogEntity::toDomain) }

    override fun observeExecutionLogsForDate(date: LocalDate): Flow<List<TaskExecutionLog>> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return executionLogDao.observeBetween(start, end).map { logs -> logs.map(ExecutionLogEntity::toDomain) }
    }

    override suspend fun save(draft: TaskDraft) {
        val now = System.currentTimeMillis()
        val existing = if (draft.id == null) null else taskDao.getById(draft.id)
        val entity = TaskEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            description = draft.description.trim(),
            displayName = draft.displayName.trim().ifBlank { TaskName.fromDescription(draft.description) },
            category = draft.category.name,
            // A task's initial priority is evidence of the user's original intent and
            // must not be silently rewritten by later edits.
            userPriority = existing?.userPriority ?: draft.userPriority.name,
            estimatedDays = draft.estimatedDays,
            totalDurationMinutes = draft.totalDurationMinutes,
            dueDateEpochDay = draft.dueDate?.toEpochDay(),
            status = existing?.status ?: TaskStatus.NOT_STARTED.name,
            completionSummary = existing?.completionSummary,
            actualDurationMinutes = existing?.actualDurationMinutes,
            progressPercent = existing?.progressPercent,
            postponeCount = existing?.postponeCount ?: 0,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
            completionResult = existing?.completionResult,
        )
        taskDao.insert(entity)
    }

    override suspend fun confirmStatus(
        taskId: String,
        status: TaskStatus,
        feedback: TaskFeedback,
    ) = database.withTransaction {
        require(status != TaskStatus.REPLACED) {
            "Task replacement must create a new task identity."
        }
        val existing = requireTask(taskId)
        val currentStatus = TaskStatus.valueOf(existing.status)
        require(TaskLifecycleValidator.isValidStatusConfirmation(currentStatus, status)) {
            "Invalid user-confirmed status transition: $currentStatus -> $status"
        }
        if (status == TaskStatus.COMPLETED) {
            require(TaskLifecycleValidator.isValidCompletion(feedback)) {
                "Completion requires completed content and a valid 100% progress value."
            }
        } else {
            require(TaskLifecycleValidator.isValidFeedback(feedback)) { "Invalid feedback values." }
        }
        val now = System.currentTimeMillis()
        val logCreatedAt = nextLogCreatedAt(taskId, now)
        taskDao.update(existing.withConfirmedStatus(status, feedback, now))
        executionLogDao.insert(
            feedback.toEntity(
                taskId = taskId,
                eventType = ExecutionLogEventType.STATUS_CHANGE,
                confirmedStatus = status,
                createdAtEpochMillis = logCreatedAt,
            ),
        )
    }

    override suspend fun recordFeedback(taskId: String, feedback: TaskFeedback) =
        database.withTransaction {
            require(TaskLifecycleValidator.isValidFeedback(feedback)) { "Invalid feedback values." }
            require(TaskLifecycleValidator.hasFeedback(feedback)) { "Feedback cannot be empty." }
            val existing = requireTask(taskId)
            val status = TaskStatus.valueOf(existing.status)
            require(status != TaskStatus.REPLACED) { "A replaced task keeps its historical evidence." }
            val now = System.currentTimeMillis()
            val logCreatedAt = nextLogCreatedAt(taskId, now)
            taskDao.update(existing.withFeedbackSnapshot(feedback, now))
            executionLogDao.insert(
                feedback.toEntity(
                    taskId = taskId,
                    eventType = ExecutionLogEventType.FEEDBACK,
                    confirmedStatus = status,
                    createdAtEpochMillis = logCreatedAt,
                ),
            )
        }

    override suspend fun recordPartialCompletion(taskId: String, feedback: TaskFeedback) =
        database.withTransaction {
            require(TaskLifecycleValidator.isValidPartialCompletion(feedback)) {
                "Partial completion needs completed content and progress from 1 to 99."
            }
            val existing = requireTask(taskId)
            val currentStatus = TaskStatus.valueOf(existing.status)
            require(currentStatus.isActive) { "Only an active task can be partially completed." }
            val now = System.currentTimeMillis()
            val logCreatedAt = nextLogCreatedAt(taskId, now)
            taskDao.update(existing.withConfirmedStatus(TaskStatus.IN_PROGRESS, feedback, now))
            executionLogDao.insert(
                feedback.toEntity(
                    taskId = taskId,
                    eventType = ExecutionLogEventType.PARTIAL_COMPLETION,
                    confirmedStatus = TaskStatus.IN_PROGRESS,
                    createdAtEpochMillis = logCreatedAt,
                ),
            )
        }

    override suspend fun correctExecutionLog(
        taskId: String,
        correctedLogId: String,
        feedback: TaskFeedback,
    ) = database.withTransaction {
        require(TaskLifecycleValidator.isValidFeedback(feedback)) { "Invalid feedback values." }
        require(TaskLifecycleValidator.hasFeedback(feedback)) { "A correction must contain feedback." }
        requireTask(taskId)
        val corrected = requireNotNull(executionLogDao.getById(correctedLogId)) {
            "The execution log to correct does not exist."
        }
        require(corrected.taskId == taskId) { "A correction must belong to the same task." }
        val now = System.currentTimeMillis()
        val logCreatedAt = nextLogCreatedAt(taskId, now)
        // A historical correction does not silently rewrite the task's current state.
        executionLogDao.insert(
            feedback.toEntity(
                taskId = taskId,
                eventType = ExecutionLogEventType.CORRECTION,
                confirmedStatus = TaskStatus.valueOf(corrected.confirmedStatus),
                correctedLogId = correctedLogId,
                createdAtEpochMillis = logCreatedAt,
            ),
        )
    }

    override suspend fun replaceTask(taskId: String, replacement: TaskDraft): String =
        database.withTransaction {
            require(TaskDraftValidator.isValid(replacement.copy(id = null))) {
                "The replacement task needs valid task information."
            }
            val original = requireTask(taskId)
            require(TaskStatus.valueOf(original.status).isActive) {
                "Only an active task can be replaced."
            }
            val now = System.currentTimeMillis()
            val logCreatedAt = nextLogCreatedAt(taskId, now)
            val replacementId = UUID.randomUUID().toString()
            taskDao.insert(
                TaskEntity(
                    id = replacementId,
                    description = replacement.description.trim(),
                    displayName = replacement.displayName.trim()
                        .ifBlank { TaskName.fromDescription(replacement.description) },
                    category = replacement.category.name,
                    userPriority = replacement.userPriority.name,
                    estimatedDays = replacement.estimatedDays,
                    totalDurationMinutes = replacement.totalDurationMinutes,
                    dueDateEpochDay = replacement.dueDate?.toEpochDay(),
                    status = TaskStatus.NOT_STARTED.name,
                    completionSummary = null,
                    actualDurationMinutes = null,
                    progressPercent = null,
                    postponeCount = 0,
                    createdAtEpochMillis = logCreatedAt,
                    updatedAtEpochMillis = now,
                    completionResult = null,
                ),
            )
            taskDao.update(original.copy(status = TaskStatus.REPLACED.name, updatedAtEpochMillis = now))
            executionLogDao.insert(
                TaskFeedback().toEntity(
                    taskId = taskId,
                    eventType = ExecutionLogEventType.REPLACEMENT,
                    confirmedStatus = TaskStatus.REPLACED,
                    replacementTaskId = replacementId,
                    createdAtEpochMillis = logCreatedAt,
                ),
            )
            replacementId
        }

    private suspend fun requireTask(taskId: String): TaskEntity =
        requireNotNull(taskDao.getById(taskId)) { "Task does not exist." }

    /** Makes chronological ordering deterministic even for confirmations in one clock millisecond. */
    private suspend fun nextLogCreatedAt(taskId: String, now: Long): Long {
        val previous = executionLogDao.latestCreatedAtForTask(taskId) ?: return now
        return maxOf(now, previous + 1)
    }
}

private fun TaskEntity.withConfirmedStatus(
    status: TaskStatus,
    feedback: TaskFeedback,
    now: Long,
): TaskEntity = withFeedbackSnapshot(feedback, now).copy(
    status = status.name,
    progressPercent = if (status == TaskStatus.COMPLETED) 100 else feedback.progressPercent ?: progressPercent,
    postponeCount = if (status == TaskStatus.POSTPONED) postponeCount + 1 else postponeCount,
)

private fun TaskEntity.withFeedbackSnapshot(feedback: TaskFeedback, now: Long): TaskEntity = copy(
    completionSummary = feedback.completedContent.cleanOrNull ?: completionSummary,
    completionResult = feedback.completionResult.cleanOrNull ?: completionResult,
    actualDurationMinutes = feedback.actualDurationMinutes ?: actualDurationMinutes,
    progressPercent = feedback.progressPercent ?: progressPercent,
    updatedAtEpochMillis = now,
)

private fun TaskFeedback.toEntity(
    taskId: String,
    eventType: ExecutionLogEventType,
    confirmedStatus: TaskStatus,
    correctedLogId: String? = null,
    replacementTaskId: String? = null,
    createdAtEpochMillis: Long,
): ExecutionLogEntity = ExecutionLogEntity(
    id = UUID.randomUUID().toString(),
    taskId = taskId,
    eventType = eventType.name,
    confirmedStatus = confirmedStatus.name,
    actualDurationMinutes = actualDurationMinutes,
    progressPercent = progressPercent,
    completedContent = completedContent.cleanOrNull,
    completionResult = completionResult.cleanOrNull,
    postponeReason = postponeReason.cleanOrNull,
    correctedLogId = correctedLogId,
    replacementTaskId = replacementTaskId,
    createdAtEpochMillis = createdAtEpochMillis,
)

private val String?.cleanOrNull: String?
    get() = this?.trim()?.takeIf(String::isNotBlank)
