package com.swan1127.repland.domain.model

import java.time.LocalDate
import java.util.UUID

enum class TaskCaptureStage { CAPTURE, INTENT, DURATION, DEADLINE, DETAILS }

/** Uncommitted local input; its identity makes a retried save idempotent. */
data class TaskCaptureDraft(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val stage: TaskCaptureStage = TaskCaptureStage.CAPTURE,
    val dueDate: LocalDate? = null,
    val scheduledForDate: LocalDate? = null,
    val duration: Int = 0,
    val category: TaskCategory = TaskCategory.UNSPECIFIED,
    val priority: TaskPriority = TaskPriority.UNSPECIFIED,
    val isCustomDuration: Boolean = false,
    val customDurationText: String = "",
    val categorySource: TaskInputSource = if (category == TaskCategory.UNSPECIFIED) TaskInputSource.UNKNOWN else TaskInputSource.USER_INPUT,
    val prioritySource: TaskInputSource = if (priority == TaskPriority.UNSPECIFIED) TaskInputSource.UNKNOWN else TaskInputSource.USER_INPUT,
) {
    val selectedDuration: Int? get() = if (isCustomDuration) customDurationText.takeIf { raw ->
        raw.isNotEmpty() && raw.all { it in '0'..'9' }
    }?.toIntOrNull()?.takeIf { it in 1..1440 }
        else duration.takeIf { it > 0 }
    val durationIsValid: Boolean get() = !isCustomDuration || selectedDuration != null
    fun toTaskDraft(): TaskDraft = TaskDraft(id, TaskName.fromDescription(text.trim()), text.trim(), category, priority,
        null, selectedDuration, dueDate, scheduledForDate).let { draft ->
            draft.copy(inputSources = draft.inputSources.copy(category = categorySource, priority = prioritySource))
        }
}
