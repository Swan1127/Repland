package com.swan1127.repland.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.swan1127.repland.domain.model.ExecutionLogEventType
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskStatus

@Entity(
    tableName = "execution_logs",
    indices = [Index(value = ["taskId", "createdAtEpochMillis"])],
)
data class ExecutionLogEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val eventType: String,
    val confirmedStatus: String,
    val actualDurationMinutes: Int?,
    val progressPercent: Int?,
    val completedContent: String?,
    val completionResult: String?,
    val postponeReason: String?,
    val correctedLogId: String?,
    val replacementTaskId: String?,
    val createdAtEpochMillis: Long,
)

fun ExecutionLogEntity.toDomain(): TaskExecutionLog = TaskExecutionLog(
    id = id,
    taskId = taskId,
    eventType = ExecutionLogEventType.valueOf(eventType),
    confirmedStatus = TaskStatus.valueOf(confirmedStatus),
    feedback = TaskFeedback(
        actualDurationMinutes = actualDurationMinutes,
        progressPercent = progressPercent,
        completedContent = completedContent,
        completionResult = completionResult,
        postponeReason = postponeReason,
    ),
    correctedLogId = correctedLogId,
    replacementTaskId = replacementTaskId,
    createdAtEpochMillis = createdAtEpochMillis,
)
