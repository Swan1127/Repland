package com.swan1127.repland.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.RepeatRule
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
    val scheduledForEpochDay: Long? = null,
    /** Serialised [com.swan1127.repland.domain.model.SubTask] list; null means none yet. */
    val subtasks: String? = null,
    /**
     * The name of a [com.swan1127.repland.domain.model.RepeatRule], or null for a one-off.
     *
     * Stored as text like every other enum on this entity, so an unknown value from a future version
     * fails loudly in [toDomain] rather than silently reading as "does not repeat".
     */
    val repeatRule: String? = null,
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
    scheduledForDate = scheduledForEpochDay?.let(LocalDate::ofEpochDay),
    subtasks = SubTaskJson.decode(subtasks),
    repeatRule = repeatRule?.let(RepeatRule::valueOf),
)
