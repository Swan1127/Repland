package com.swan1127.repland.domain.ports

import com.swan1127.repland.domain.model.SubTask
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

    /**
     * Every log from the start of [from] onwards, for the insight page's trend.
     *
     * A window rather than "everything" so the query stays bounded as the database grows:
     * the page only ever draws a fixed number of days.
     */
    fun observeExecutionLogsSince(from: LocalDate): Flow<List<TaskExecutionLog>>

    /** Keeps a supplied draft identity when creating a task; generates one only when id is null. */
    suspend fun save(draft: TaskDraft)

    /** Replaces a task's entire subtask list. Add/remove/toggle are composed above this in the UI. */
    suspend fun setSubTasks(taskId: String, subtasks: List<SubTask>)

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
