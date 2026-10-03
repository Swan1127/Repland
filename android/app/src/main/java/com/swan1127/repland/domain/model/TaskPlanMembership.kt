package com.swan1127.repland.domain.model

import java.time.LocalDate

/** Read-only page membership; a confirmed placement is not a status/date mutation. */
object TaskPlanMembership {
    fun isToday(task: Task, segments: List<PlannedSegment>, today: LocalDate): Boolean =
        task.dueDate == today || task.scheduledForDate == today || segments.any { it.taskId == task.id && it.date == today }

    fun isInbox(task: Task, segments: List<PlannedSegment>, today: LocalDate): Boolean =
        task.dueDate == null && task.scheduledForDate == null &&
            segments.none { it.taskId == task.id && it.date >= today }
}
