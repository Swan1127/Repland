package com.swan1127.repland.domain.model

/**
 * The user-confirmed evidence attached to a task lifecycle event. These values are
 * intentionally separate from a task's planning fields: they describe what happened,
 * not what the planner should assume happened.
 */
data class TaskFeedback(
    val actualDurationMinutes: Int? = null,
    val progressPercent: Int? = null,
    val completedContent: String? = null,
    val completionResult: String? = null,
    val postponeReason: String? = null,
)

/** Every row is immutable evidence; corrections add another row instead of changing this one. */
data class TaskExecutionLog(
    val id: String,
    val taskId: String,
    val eventType: ExecutionLogEventType,
    val confirmedStatus: TaskStatus,
    val feedback: TaskFeedback,
    val correctedLogId: String?,
    val replacementTaskId: String?,
    val createdAtEpochMillis: Long,
)

enum class ExecutionLogEventType {
    STATUS_CHANGE,
    FEEDBACK,
    PARTIAL_COMPLETION,
    CORRECTION,
    REPLACEMENT,
}

/**
 * Keeps lifecycle decisions at the user-operation boundary. It deliberately has no
 * AI or planner entry points, so callers must supply a concrete user action.
 */
object TaskLifecycleValidator {
    fun isValidFeedback(feedback: TaskFeedback): Boolean =
        (feedback.actualDurationMinutes == null || feedback.actualDurationMinutes in 1..1_440) &&
            (feedback.progressPercent == null || feedback.progressPercent in 0..100)

    fun hasFeedback(feedback: TaskFeedback): Boolean =
        feedback.actualDurationMinutes != null ||
            feedback.progressPercent != null ||
            !feedback.completedContent.isNullOrBlank() ||
            !feedback.completionResult.isNullOrBlank() ||
            !feedback.postponeReason.isNullOrBlank()

    fun isValidStatusConfirmation(
        currentStatus: TaskStatus,
        confirmedStatus: TaskStatus,
    ): Boolean = when (currentStatus) {
        TaskStatus.NOT_STARTED -> confirmedStatus in setOf(
            TaskStatus.IN_PROGRESS,
            TaskStatus.COMPLETED,
            TaskStatus.POSTPONED,
            TaskStatus.CANCELLED,
        )

        TaskStatus.IN_PROGRESS -> confirmedStatus in setOf(
            TaskStatus.COMPLETED,
            TaskStatus.POSTPONED,
            TaskStatus.CANCELLED,
        )

        // Another user-confirmed delay is a new event, even when the visible state is unchanged.
        TaskStatus.POSTPONED -> confirmedStatus in setOf(
            TaskStatus.IN_PROGRESS,
            TaskStatus.COMPLETED,
            TaskStatus.POSTPONED,
            TaskStatus.CANCELLED,
        )

        // Restoration is a user action, never an automatic resurrection.
        TaskStatus.COMPLETED,
        TaskStatus.CANCELLED -> confirmedStatus == TaskStatus.NOT_STARTED

        TaskStatus.REPLACED -> false
    }

    fun isValidCompletion(feedback: TaskFeedback): Boolean =
        isValidFeedback(feedback) &&
            (feedback.progressPercent == null || feedback.progressPercent == 100) &&
            !feedback.completedContent.isNullOrBlank()

    fun isValidPartialCompletion(feedback: TaskFeedback): Boolean =
        isValidFeedback(feedback) &&
            feedback.progressPercent in 1..99 &&
            !feedback.completedContent.isNullOrBlank()
}
