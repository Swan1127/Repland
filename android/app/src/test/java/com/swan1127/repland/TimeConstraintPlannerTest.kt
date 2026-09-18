package com.swan1127.repland

import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.DateOverrideType
import com.swan1127.repland.domain.model.PlanGenerator
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeConstraintPlannerTest {
    private val monday = LocalDate.of(2026, 9, 14)
    private val clock = Clock.fixed(Instant.parse("2026-09-14T06:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `missing explicit availability yields a list-only plan instead of invented time slots`() {
        val plan = PlanGenerator.generate(
            tasks = listOf(task()),
            weeklyBlocks = listOf(block(TimeBlockKind.COURSE, 9 * 60, 10 * 60)),
            dateOverrides = emptyList(),
            clock = clock,
        )

        assertTrue(plan.segments.isEmpty())
        assertEquals(listOf("task"), plan.pendingTaskIds)
        assertTrue(plan.unscheduledTasks.isEmpty())
    }

    @Test
    fun `weekly availability respects recurring blockers and a one-day available override`() {
        val plan = PlanGenerator.generate(
            tasks = listOf(task(duration = 60)),
            weeklyBlocks = listOf(
                block(TimeBlockKind.AVAILABLE, 8 * 60, 10 * 60),
                block(TimeBlockKind.COURSE, 8 * 60, 9 * 60),
                block(TimeBlockKind.OTHER, 9 * 60 + 30, 10 * 60),
            ),
            dateOverrides = listOf(
                DateOverride(
                    id = "override",
                    title = "课程取消",
                    type = DateOverrideType.AVAILABLE,
                    date = monday,
                    startMinute = 8 * 60,
                    endMinute = 8 * 60 + 30,
                    createdAtEpochMillis = 1,
                    updatedAtEpochMillis = 1,
                ),
            ),
            clock = clock,
        )

        assertEquals(
            listOf(8 * 60, 9 * 60),
            plan.segments.map(PlannedSegment::startMinute),
        )
        assertFalse(plan.segments.any { it.startMinute == 8 * 60 + 30 })
    }

    @Test
    fun `locked confirmed segments are retained and reserved during a new draft`() {
        val locked = PlannedSegment(
            id = "locked",
            taskId = "task",
            date = monday,
            startMinute = 8 * 60,
            endMinute = 8 * 60 + 30,
            isLocked = true,
        )
        val plan = PlanGenerator.generate(
            tasks = listOf(task(duration = 60)),
            weeklyBlocks = listOf(block(TimeBlockKind.AVAILABLE, 8 * 60, 10 * 60)),
            dateOverrides = emptyList(),
            lockedSegments = listOf(locked),
            clock = clock,
        )

        assertEquals(2, plan.segments.size)
        assertTrue(plan.segments.any { it.id == "locked" && it.isLocked })
        assertTrue(plan.segments.any { it.startMinute == 8 * 60 + 30 && !it.isLocked })
    }

    private fun task(duration: Int = 30) = Task(
        id = "task",
        description = "",
        displayName = "复习",
        category = TaskCategory.COURSE,
        userPriority = TaskPriority.HIGH,
        estimatedDays = 1,
        totalDurationMinutes = duration,
        dueDate = monday,
        status = TaskStatus.NOT_STARTED,
        completionSummary = null,
        actualDurationMinutes = null,
        progressPercent = null,
        postponeCount = 0,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )

    private fun block(kind: TimeBlockKind, start: Int, end: Int) = WeeklyTimeBlock(
        id = "$kind-$start",
        title = kind.name,
        kind = kind,
        dayOfWeek = DayOfWeek.MONDAY,
        startMinute = start,
        endMinute = end,
        weekPattern = null,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}
