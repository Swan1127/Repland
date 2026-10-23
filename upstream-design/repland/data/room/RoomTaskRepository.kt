package com.swan1127.repland.data.room

import androidx.room.withTransaction
import com.swan1127.repland.domain.model.ExecutionLogEventType
import com.swan1127.repland.domain.model.RecurrenceAdvance
import com.swan1127.repland.domain.model.SubTask
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskDraftValidator
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskLifecycleValidator
import com.swan1127.repland.domain.model.TaskName
import com.swan1127.repland.domain.model.TaskRecurrence
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

    override fun observeExecutionLogsSince(from: LocalDate): Flow<List<TaskExecutionLog>> {
        val start = from.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // No upper bound: the existing `observeBetween` query already takes an end, and an
        // open-ended variant would be a second query saying the same thing.
        return executionLogDao.observeBetween(start, Long.MAX_VALUE)
            .map { logs -> logs.map(ExecutionLogEntity::toDomain) }
    }

    override suspend fun save(draft: TaskDraft) {
        require(TaskDraftValidator.isValid(draft)) { "The task needs valid task information." }
        require(draft.id == null || draft.id.isNotBlank()) { "A task identity cannot be blank." }
        val now = System.currentTimeMillis()
        val existing = if (draft.id == null) null else taskDao.getById(draft.id)
        taskDao.insert(draft.toTaskEntity(existing, now))
    }

    override suspend fun setSubTasks(taskId: String, subtasks: List<SubTask>) =
        database.withTransaction {
            val existing = requireTask(taskId)
            taskDao.update(
                existing.copy(
                    subtasks = SubTaskJson.encode(subtasks),
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
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
                "Completion accepts optional feedback and valid progress values."
            }
        } else {
            require(TaskLifecycleValidator.isValidFeedback(feedback)) { "Invalid feedback values." }
        }
        val now = System.currentTimeMillis()
        val effectiveFeedback = if (status == TaskStatus.COMPLETED && feedback.actualDurationMinutes == null) {
            feedback.copy(actualDurationMinutes = inferActualDurationMinutes(existing, taskId, now))
        } else {
            feedback
        }
        val logCreatedAt = nextLogCreatedAt(taskId, now)
        // A repeating task does not become a museum piece. The completion is still written to the
        // log below — with `confirmedStatus = COMPLETED`, exactly as before — so evidence, the daily
        // review and profile evidence see a real completion and nothing downstream changes. What
        // moves is the *row*: it becomes the next occurrence instead of holding the finished one.
        // The decision itself is pure and lives in TaskRecurrence; this is the straight-line mapping
        // of it, and `LocalDate.now(zone)` is the clock it is given.
        val advance = if (status == TaskStatus.COMPLETED) {
            TaskRecurrence.onCompletion(existing.toDomain(), LocalDate.now(ZoneId.systemDefault()))
        } else {
            null
        }
        taskDao.update(
            if (advance != null) {
                existing.withRecurrenceAdvanced(advance, now)
            } else {
                existing.withConfirmedStatus(status, effectiveFeedback, now)
            },
        )
        executionLogDao.insert(
            effectiveFeedback.toEntity(
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
                    scheduledForEpochDay = replacement.scheduledForDate?.toEpochDay(),
                    // A replacement is the same intent under a new identity, so it keeps the cadence
                    // the user chose; the draft is the only thing that can say otherwise.
                    repeatRule = replacement.repeatRule?.name,
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

    private suspend fun inferActualDurationMinutes(existing: TaskEntity, taskId: String, now: Long): Int? {
        val startedAt = executionLogDao.getForTask(taskId)
            .lastOrNull { it.confirmedStatus == TaskStatus.IN_PROGRESS.name }
            ?.createdAtEpochMillis
        val elapsed = startedAt?.let { ((now - it) / 60_000L).toInt().coerceIn(1, 1_440) }
        return elapsed ?: existing.totalDurationMinutes?.coerceIn(1, 1_440)
    }

    /** Makes chronological ordering deterministic even for confirmations in one clock millisecond. */
    private suspend fun nextLogCreatedAt(taskId: String, now: Long): Long {
        val previous = executionLogDao.latestCreatedAtForTask(taskId) ?: return now
        return maxOf(now, previous + 1)
    }
}

/** Shared by individual edits and atomic arrangement commits, so supplied identities survive. */
internal fun TaskDraft.toTaskEntity(existing: TaskEntity?, now: Long): TaskEntity = TaskEntity(
    id = existing?.id ?: id ?: UUID.randomUUID().toString(),
    description = description.trim(),
    displayName = displayName.trim().ifBlank { TaskName.fromDescription(description) },
    category = category.name,
    userPriority = existing?.userPriority ?: userPriority.name,
    estimatedDays = estimatedDays,
    totalDurationMinutes = totalDurationMinutes,
    dueDateEpochDay = dueDate?.toEpochDay(),
    status = existing?.status ?: TaskStatus.NOT_STARTED.name,
    completionSummary = existing?.completionSummary,
    actualDurationMinutes = existing?.actualDurationMinutes,
    progressPercent = existing?.progressPercent,
    postponeCount = existing?.postponeCount ?: 0,
    createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
    updatedAtEpochMillis = now,
    completionResult = existing?.completionResult,
    scheduledForEpochDay = scheduledForDate?.toEpochDay(),
    subtasks = existing?.subtasks,
    repeatRule = repeatRule?.name,
)

private fun TaskEntity.withConfirmedStatus(
    status: TaskStatus,
    feedback: TaskFeedback,
    now: Long,
): TaskEntity = withFeedbackSnapshot(feedback, now).copy(
    status = status.name,
    progressPercent = if (status == TaskStatus.COMPLETED) 100 else feedback.progressPercent ?: progressPercent,
    postponeCount = if (status == TaskStatus.POSTPONED) postponeCount + 1 else postponeCount,
)

/**
 * The row after a repeating task's occurrence is finished: it becomes the next occurrence.
 *
 * The outcome of the occurrence that just ended is **not** carried over. It is already in the
 * execution log, and leaving `completionSummary` / `completionResult` / `progressPercent` on a row
 * that reads NOT_STARTED would describe the next occurrence with the previous one's result — the
 * "sentence that is false" defect this project keeps finding. [postponeCount] resets for the same
 * reason: postponements belong to the occurrence that ended.
 *
 * `internal` rather than private so `RoomEntityMappingTest` can drive it: it is a pure row
 * transformation, and the fields it *clears* are exactly the ones a test has to see go.
 */
internal fun TaskEntity.withRecurrenceAdvanced(advance: RecurrenceAdvance, now: Long): TaskEntity = copy(
    status = TaskStatus.NOT_STARTED.name,
    scheduledForEpochDay = advance.scheduledForDate?.toEpochDay(),
    dueDateEpochDay = advance.dueDate?.toEpochDay(),
    completionSummary = null,
    completionResult = null,
    actualDurationMinutes = null,
    progressPercent = null,
    postponeCount = 0,
    // The checklist describes work for the new occurrence. Keep its stable items, but a tick
    // from the completed occurrence must not make the next one appear already finished.
    subtasks = subtasks?.let { stored ->
        SubTaskJson.encode(SubTaskJson.decode(stored).map { item -> item.copy(isDone = false) })
    },
    updatedAtEpochMillis = now,
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
