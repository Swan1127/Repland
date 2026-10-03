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
    fun `an available override cannot silently cancel a recurring course`() {
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
            listOf(9 * 60),
            plan.segments.map(PlannedSegment::startMinute),
        )
        assertFalse(plan.segments.any { it.startMinute == 8 * 60 + 30 })
        assertFalse(plan.segments.any { it.startMinute == 8 * 60 })
        assertEquals(30, plan.unscheduledTasks.single().remainingMinutes)
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

    @Test fun replan_preserves_past_without_deducting_it_twice_from_confirmed_remaining_work() {
        val past = PlannedSegment("past", "task", monday.minusDays(1), 540, 600)
        val future = PlannedSegment("old-future", "task", monday, 540, 600)
        val completedPast = PlannedSegment("finished-history", "done", monday.minusDays(1), 600, 630)
        val completedFuture = PlannedSegment("finished-future", "done", monday, 600, 630, isLocked = true)
        val input = com.swan1127.repland.domain.model.PlanGenerationInput(
            listOf(task(120).copy(progressPercent = 50), task().copy(id = "done", status = TaskStatus.COMPLETED)),
            listOf(block(TimeBlockKind.AVAILABLE, 480, 660)), emptyList(), null,
            manualTaskOrder = listOf("task"))
        val current = com.swan1127.repland.domain.model.ConfirmedPlan("p", 1, true,
            listOf(past, future, completedPast, completedFuture))
        val result = PlanGenerator.generateRemaining(input, current, clock)
        assertTrue(past in result.segments); assertTrue(completedPast in result.segments)
        assertFalse(completedFuture in result.segments)
        assertEquals(60, result.segments.filter { it.date == monday && it.taskId == "task" }.sumOf { it.endMinute - it.startMinute })
        assertEquals(listOf("task"), result.orderedTaskIds)
    }

    @Test fun started_segment_is_preserved_without_turning_it_into_a_user_lock() {
        val started = PlannedSegment("started", "task", monday, 480, 540)
        val atHalfPast = Clock.fixed(Instant.parse("2026-09-14T08:30:00Z"), ZoneOffset.UTC)
        val input = com.swan1127.repland.domain.model.PlanGenerationInput(listOf(task(120)),
            listOf(block(TimeBlockKind.AVAILABLE, 480, 660)), emptyList(), null)
        val current = com.swan1127.repland.domain.model.ConfirmedPlan("p", 1, true, listOf(started))
        val result = PlanGenerator.generateRemaining(input, current, atHalfPast)
        assertTrue(started in result.segments)
        assertFalse(result.segments.first { it.id == "started" }.isLocked)
        assertFalse(result.segments.filter { it.id != "started" }.any { it.startMinute < 540 })
    }

    @Test fun missing_availability_keeps_existing_manual_placements_instead_of_silently_clearing_them() {
        val placed = PlannedSegment("manual", "task", monday, 540, 600)
        val input = com.swan1127.repland.domain.model.PlanGenerationInput(listOf(task(60)), emptyList(), emptyList(), null)
        val current = com.swan1127.repland.domain.model.ConfirmedPlan("p", 1, true, listOf(placed))
        val result = PlanGenerator.generateRemaining(input, current, clock)
        assertEquals(listOf(placed), result.segments)
        assertEquals(listOf("task"), result.orderedTaskIds)
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
