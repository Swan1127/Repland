package com.swan1127.repland.domain.model

import java.time.LocalDate

/** A user-owned task. Status transitions are intentionally never delegated to AI. */
data class Task(
    val id: String,
    val description: String,
    val displayName: String,
    val category: TaskCategory,
    val userPriority: TaskPriority,
    val estimatedDays: Int,
    val totalDurationMinutes: Int?,
    val dueDate: LocalDate?,
    val status: TaskStatus,
    val completionSummary: String?,
    val actualDurationMinutes: Int?,
    val progressPercent: Int?,
    val postponeCount: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val completionResult: String? = null,
)

data class TaskDraft(
    val id: String? = null,
    val displayName: String,
    val description: String,
    val category: TaskCategory,
    val userPriority: TaskPriority,
    val estimatedDays: Int,
    val totalDurationMinutes: Int?,
    val dueDate: LocalDate?,
)

enum class TaskStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    POSTPONED,
    CANCELLED,
    REPLACED,
    ;

    val isActive: Boolean
        get() = this == NOT_STARTED || this == IN_PROGRESS || this == POSTPONED
}

enum class TaskPriority(val score: Int) {
    REQUIRED(100),
    HIGH(80),
    MEDIUM(50),
    LOW(20),
}

enum class TaskCategory(val defaultWeight: Int) {
    COURSE(30),
    EXTRACURRICULAR(25),
    OFFICE(25),
    LEISURE(20),
}

object TaskName {
    private const val MaxLength = 24

    fun fromDescription(description: String): String {
        val firstLine = description.trim().lineSequence().firstOrNull().orEmpty()
        return if (firstLine.length <= MaxLength) firstLine else firstLine.take(MaxLength) + "…"
    }
}

object TaskDraftValidator {
    fun isValid(draft: TaskDraft): Boolean =
        draft.displayName.isNotBlank() &&
            draft.estimatedDays in 1..30 &&
            (draft.totalDurationMinutes == null || draft.totalDurationMinutes in 1..1_440) &&
            (draft.dueDate == null || !draft.dueDate.isBefore(LocalDate.now()))
}
