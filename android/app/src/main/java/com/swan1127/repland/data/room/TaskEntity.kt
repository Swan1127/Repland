package com.swan1127.repland.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import java.time.LocalDate

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val description: String,
    val displayName: String,
    val category: String,
    val userPriority: String,
    val estimatedDays: Int,
    val totalDurationMinutes: Int?,
    val dueDateEpochDay: Long?,
    val status: String,
    val completionSummary: String?,
    val actualDurationMinutes: Int?,
    val progressPercent: Int?,
    val postponeCount: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val completionResult: String? = null,
)

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    description = description,
    displayName = displayName,
    category = TaskCategory.valueOf(category),
    userPriority = TaskPriority.valueOf(userPriority),
    estimatedDays = estimatedDays,
    totalDurationMinutes = totalDurationMinutes,
    dueDate = dueDateEpochDay?.let(LocalDate::ofEpochDay),
    status = TaskStatus.valueOf(status),
    completionSummary = completionSummary,
    actualDurationMinutes = actualDurationMinutes,
    progressPercent = progressPercent,
    postponeCount = postponeCount,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    completionResult = completionResult,
)
