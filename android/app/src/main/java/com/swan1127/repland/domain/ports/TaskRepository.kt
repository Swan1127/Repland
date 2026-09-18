package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskDraft
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeTasks(): Flow<List<Task>>

    fun observeExecutionLogs(taskId: String): Flow<List<TaskExecutionLog>>

    fun observeExecutionLogsForDate(date: LocalDate): Flow<List<TaskExecutionLog>>

    suspend fun save(draft: TaskDraft)

    /** Records a user-confirmed lifecycle change and appends immutable evidence. */
    suspend fun confirmStatus(
        taskId: String,
        status: TaskStatus,
        feedback: TaskFeedback = TaskFeedback(),
    )

    suspend fun recordFeedback(taskId: String, feedback: TaskFeedback)

    suspend fun recordPartialCompletion(taskId: String, feedback: TaskFeedback)

    /** Appends a correction linked to its source event; the source event is never edited. */
    suspend fun correctExecutionLog(
        taskId: String,
        correctedLogId: String,
        feedback: TaskFeedback,
    )

    /** Marks the original as replaced and creates a separate task identity. */
    suspend fun replaceTask(taskId: String, replacement: TaskDraft): String
}
