package com.swan1127.repland.domain.model

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class ReminderPreferences(
    val isEnabled: Boolean = false,
)

enum class ReminderKind {
    SEGMENT_START,
    DEADLINE,
    DAILY_REVIEW,
}

/** A local-only alarm candidate. It is produced from a confirmed plan, never a draft. */
data class LocalReminder(
    val kind: ReminderKind,
    val key: String,
    val triggerAt: Instant,
    val taskId: String? = null,
    val planId: String? = null,
    val segmentId: String? = null,
    val taskName: String? = null,
    val dueDate: LocalDate? = null,
)

object LocalReminderPlanner {
    const val DAILY_REVIEW_HOUR = 20
    const val DAILY_REVIEW_MINUTE = 30
    private const val DEADLINE_WARNING_HOUR = 20

    fun plan(
        currentPlan: ConfirmedPlan?,
        tasks: List<Task>,
        clock: Clock = Clock.systemDefaultZone(),
    ): List<LocalReminder> {
        val confirmedPlan = currentPlan ?: return emptyList()
        val now = Instant.now(clock)
        val zone = clock.zone
        val tasksById = tasks.associateBy(Task::id)
        val starts = confirmedPlan.segments.mapNotNull { segment ->
            val task = tasksById[segment.taskId] ?: return@mapNotNull null
            val triggerAt = segment.date.atStartOfDay().plusMinutes(segment.startMinute.toLong())
                .atZone(zone).toInstant()
            if (!task.status.isActive || !triggerAt.isAfter(now)) return@mapNotNull null
            LocalReminder(
                kind = ReminderKind.SEGMENT_START,
                key = "${confirmedPlan.id}:${segment.id}",
                triggerAt = triggerAt,
                taskId = task.id,
                planId = confirmedPlan.id,
                segmentId = segment.id,
                taskName = task.displayName,
            )
        }
        val deadlines = tasks.asSequence()
            .filter { task -> task.status.isActive }
            .mapNotNull { task ->
                val dueDate = task.dueDate ?: return@mapNotNull null
                val triggerAt = dueDate.minusDays(1).atTime(DEADLINE_WARNING_HOUR, 0)
                    .atZone(zone).toInstant()
                if (!triggerAt.isAfter(now)) return@mapNotNull null
                LocalReminder(
                    kind = ReminderKind.DEADLINE,
                    key = task.id,
                    triggerAt = triggerAt,
                    taskId = task.id,
                    taskName = task.displayName,
                    dueDate = dueDate,
                )
            }
            .toList()
        val dailyReview = LocalReminder(
            kind = ReminderKind.DAILY_REVIEW,
            key = "daily-review",
            triggerAt = nextDailyReview(now, zone),
        )
        return (starts + deadlines + dailyReview)
            .sortedWith(compareBy(LocalReminder::triggerAt, LocalReminder::kind, LocalReminder::key))
    }

    private fun nextDailyReview(now: Instant, zone: ZoneId): Instant {
        val localNow = LocalDateTime.ofInstant(now, zone)
        val todayReview = localNow.toLocalDate().atTime(DAILY_REVIEW_HOUR, DAILY_REVIEW_MINUTE)
        val next = if (todayReview.atZone(zone).toInstant().isAfter(now)) {
            todayReview
        } else {
            todayReview.plusDays(1)
        }
        return next.atZone(zone).toInstant()
    }
}

data class DailyReviewSummary(
    val plannedSegmentCount: Int,
    val pendingSegmentCount: Int,
    val feedbackCount: Int,
    val actualDurationMinutes: Int,
    val progressByTask: List<DailyTaskProgress>,
    val nextSuggestedTaskId: String?,
)

data class DailyTaskProgress(
    val taskId: String,
    val taskName: String,
    val progressPercent: Int?,
    val completionResult: String?,
)

/** Summarises only recorded evidence. It never infers or updates a task status. */
object DailyReviewSummarizer {
    fun summarize(
        date: LocalDate,
        tasks: List<Task>,
        plan: ConfirmedPlan?,
        logs: List<TaskExecutionLog>,
    ): DailyReviewSummary {
        val taskById = tasks.associateBy(Task::id)
        val scheduled = plan?.segments.orEmpty().filter { it.date == date }
        val scheduledTaskIds = scheduled.map(PlannedSegment::taskId).toSet()
        val progress = logs.mapNotNull { log ->
            taskById[log.taskId]?.let { task ->
                DailyTaskProgress(
                    taskId = task.id,
                    taskName = task.displayName,
                    progressPercent = log.feedback.progressPercent,
                    completionResult = log.feedback.completionResult,
                )
            }
        }
        val pending = scheduled.count { segment ->
            taskById[segment.taskId]?.status?.isActive == true
        }
        val nextTask = tasks.firstOrNull { task ->
            task.status.isActive && task.id !in scheduledTaskIds
        } ?: tasks.firstOrNull { task -> task.status.isActive }
        return DailyReviewSummary(
            plannedSegmentCount = scheduled.size,
            pendingSegmentCount = pending,
            feedbackCount = logs.size,
            actualDurationMinutes = logs.sumOf { it.feedback.actualDurationMinutes ?: 0 },
            progressByTask = progress,
            nextSuggestedTaskId = nextTask?.id,
        )
    }
}
