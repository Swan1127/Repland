package com.swan1127.repland.domain.model

import java.time.LocalDate

// SubTask is referenced by Task.subtasks; both live in this package.

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
    /** The day the user intends to work on it; unlike [dueDate], this is not a deadline. */
    val scheduledForDate: LocalDate? = null,
    /**
     * The task's checklist. Independent of [status]: ticking boxes never changes the parent's
     * lifecycle, it only feeds [SubTasks.progress] for display. Stored as a JSON string on the
     * entity and decoded in the repository, so it never appears in a SQL clause.
     */
    val subtasks: List<SubTask> = emptyList(),
    /**
     * How the task comes back after it is finished, or null when it is a one-off. The rule is
     * interpreted by [TaskRecurrence]; nothing else may re-derive it.
     *
     * Appended rather than slotted next to [scheduledForDate] so that no positional constructor call
     * has to change, and because it is the second field on this type that is stored as an encoded
     * string on the entity.
     */
    val repeatRule: RepeatRule? = null,
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
    /** Optional planned day, kept independent from a final deadline. */
    val scheduledForDate: LocalDate? = null,
    /** How the task comes back after completion, or null for a one-off. */
    val repeatRule: RepeatRule? = null,
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
            (draft.totalDurationMinutes == null || draft.totalDurationMinutes in 1..1_440)
}
