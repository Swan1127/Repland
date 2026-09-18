package com.swan1127.repland

import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.DailyReviewSummarizer
import com.swan1127.repland.domain.model.LocalReminderPlanner
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.ReminderKind
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReminderPlannerTest {
    private val date = LocalDate.of(2026, 9, 14)
    private val clock = Clock.fixed(Instant.parse("2026-09-14T08:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `only a confirmed current plan produces local reminder candidates`() {
        val reminders = LocalReminderPlanner.plan(
            currentPlan = plan(),
            tasks = listOf(activeTask(), inactiveTask()),
            clock = clock,
        )

        assertEquals(
            listOf(ReminderKind.SEGMENT_START, ReminderKind.DAILY_REVIEW, ReminderKind.DEADLINE),
            reminders.map { it.kind },
        )
        assertEquals("current:future", reminders.first().key)
        assertFalse(reminders.any { it.taskId == "inactive" })
    }

    @Test
    fun `no confirmed plan means no start deadline or review reminder`() {
        val reminders = LocalReminderPlanner.plan(
            currentPlan = null,
            tasks = listOf(activeTask()),
            clock = clock,
        )

        assertTrue(reminders.isEmpty())
    }

    @Test
    fun `daily review summarises recorded evidence without changing active task state`() {
        val task = activeTask()
        val original = task.copy()
        val log = TaskExecutionLog(
            id = "log",
            taskId = task.id,
            eventType = com.swan1127.repland.domain.model.ExecutionLogEventType.FEEDBACK,
            confirmedStatus = TaskStatus.IN_PROGRESS,
            feedback = TaskFeedback(actualDurationMinutes = 45, progressPercent = 50, completionResult = "掌握核心概念"),
            correctedLogId = null,
            replacementTaskId = null,
            createdAtEpochMillis = 1,
        )

        val summary = DailyReviewSummarizer.summarize(date, listOf(task), plan(), listOf(log))

        assertEquals(1, summary.plannedSegmentCount)
        assertEquals(1, summary.pendingSegmentCount)
        assertEquals(45, summary.actualDurationMinutes)
        assertEquals(50, summary.progressByTask.single().progressPercent)
        assertEquals(original, task)
        assertEquals(TaskStatus.IN_PROGRESS, task.status)
    }

    private fun plan() = ConfirmedPlan(
        id = "current",
        createdAtEpochMillis = 1,
        isCurrent = true,
        segments = listOf(
            PlannedSegment(
                id = "future",
                taskId = "active",
                date = date,
                startMinute = 9 * 60,
                endMinute = 10 * 60,
            ),
        ),
    )

    private fun activeTask() = Task(
        id = "active",
        description = "",
        displayName = "复习数据结构",
        category = TaskCategory.COURSE,
        userPriority = TaskPriority.HIGH,
        estimatedDays = 1,
        totalDurationMinutes = 60,
        dueDate = date.plusDays(2),
        status = TaskStatus.IN_PROGRESS,
        completionSummary = null,
        actualDurationMinutes = null,
        progressPercent = null,
        postponeCount = 0,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )

    private fun inactiveTask() = activeTask().copy(
        id = "inactive",
        status = TaskStatus.COMPLETED,
        dueDate = date.plusDays(3),
    )
}
