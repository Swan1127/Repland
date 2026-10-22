package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the observable contract of [PlanGenerator] — the "transparent, fixed-rule scheduler"
 * that runs before any AI suggestion exists. These tests exist so that loosening the
 * hard-coded 08:00-22:00 day window becomes a deliberate, visible change.
 */
class PlanGeneratorTest {

    private fun generate(input: PlanGenerationInput): PlanDraft = PlanGenerator.generate(
        tasks = input.tasks,
        weeklyBlocks = input.weeklyBlocks,
        dateOverrides = input.dateOverrides,
        semesterFirstWeekMonday = input.semesterFirstWeekMonday,
        lockedSegments = input.lockedSegments,
        categoryPreferences = input.categoryPreferences,
        manualTaskOrder = input.manualTaskOrder,
        clock = TEST_CLOCK,
    )

    @Test
    fun `no declared availability leaves every active task pending`() {
        val draft = generate(planInput(tasks = listOf(task("t1", durationMinutes = 60))))

        assertTrue("no slots can exist without a declared window", draft.segments.isEmpty())
        assertEquals(listOf("t1"), draft.pendingTaskIds)
        assertTrue(draft.unscheduledTasks.isEmpty())
    }

    @Test
    fun `a 60-minute task is placed inside the declared Monday window`() {
        val draft = generate(
            planInput(
                tasks = listOf(task("t1", durationMinutes = 60)),
                weeklyBlocks = listOf(weeklyBlock(startMinute = 9 * 60, endMinute = 12 * 60)),
            ),
        )

        assertEquals(1, draft.segments.size)
        val placed = draft.segments.single()
        assertEquals(TEST_MONDAY, placed.date)
        assertEquals(9 * 60, placed.startMinute)
        assertEquals(10 * 60, placed.endMinute)
        assertTrue(draft.pendingTaskIds.isEmpty())
        assertTrue(draft.unscheduledTasks.isEmpty())
    }

    @Test
    fun `the generator never places work before 08_00 even when availability starts at midnight`() {
        val input = planInput(
            tasks = listOf(task("t1", durationMinutes = 60)),
            weeklyBlocks = listOf(weeklyBlock(startMinute = 0, endMinute = 8 * 60)),
        )

        val draft = generate(input)

        assertTrue(
            "a 00:00-08:00 window is inside the user's availability but outside the day window",
            draft.segments.isEmpty(),
        )
        assertEquals(1, draft.unscheduledTasks.size)
        assertEquals(60, draft.unscheduledTasks.single().remainingMinutes)
    }

    @Test
    fun `the advisor validator accepts a midnight slot that the local generator refuses`() {
        val input = planInput(
            tasks = listOf(task("t1", durationMinutes = 60)),
            weeklyBlocks = listOf(weeklyBlock(startMinute = 0, endMinute = 8 * 60)),
        )
        val proposal = AiProposedSegment(
            taskReference = "t1",
            date = TEST_MONDAY,
            startMinute = 0,
            endMinute = 60,
        )

        // Documents a real asymmetry: PlanningConstraintValidator allows 00:00-24:00
        // (Plan.kt:62-63) while PlanGenerator clamps to 08:00-22:00 (Plan.kt:252-253).
        // If one side is ever changed, this test fails on purpose.
        assertTrue(PlanningConstraintValidator.supportsProposal(proposal, input))
        assertTrue(generate(input).segments.isEmpty())
    }

    @Test
    fun `availability past 22_00 is truncated at 22_00`() {
        // The due date pins the search to a single day, so the assertion is about the
        // 22:00 clamp and not about how the horizon spreads work across weeks.
        val draft = generate(
            planInput(
                tasks = listOf(task("t1", durationMinutes = 240, dueDate = TEST_MONDAY)),
                weeklyBlocks = listOf(weeklyBlock(startMinute = 20 * 60, endMinute = 23 * 60)),
            ),
        )

        assertEquals(1, draft.segments.size)
        val placed = draft.segments.single()
        assertEquals(20 * 60, placed.startMinute)
        assertEquals(22 * 60, placed.endMinute)
        assertEquals(1, draft.unscheduledTasks.size)
        assertEquals(120, draft.unscheduledTasks.single().remainingMinutes)
    }

    @Test
    fun `a locked segment is preserved and its minutes count against the task`() {
        val locked = segment(
            id = "locked-1",
            taskId = "t1",
            startMinute = 9 * 60,
            endMinute = 10 * 60,
            isLocked = true,
        )

        val draft = generate(
            planInput(
                tasks = listOf(task("t1", durationMinutes = 60)),
                weeklyBlocks = listOf(weeklyBlock(startMinute = 9 * 60, endMinute = 12 * 60)),
                lockedSegments = listOf(locked),
            ),
        )

        assertEquals(listOf(locked), draft.segments)
        assertTrue("the lock already covers the full requirement", draft.unscheduledTasks.isEmpty())
    }

    @Test
    fun `a task without a duration stays pending and is never placed`() {
        val draft = generate(
            planInput(
                tasks = listOf(task("t1", durationMinutes = null)),
                weeklyBlocks = listOf(weeklyBlock(startMinute = 9 * 60, endMinute = 12 * 60)),
            ),
        )

        assertTrue(draft.segments.isEmpty())
        assertEquals(listOf("t1"), draft.pendingTaskIds)
    }

    @Test
    fun `a completed task is never scheduled`() {
        val draft = generate(
            planInput(
                tasks = listOf(task("t1", durationMinutes = 60, status = TaskStatus.COMPLETED)),
                weeklyBlocks = listOf(weeklyBlock(startMinute = 9 * 60, endMinute = 12 * 60)),
            ),
        )

        assertTrue(draft.segments.isEmpty())
        assertTrue(draft.pendingTaskIds.isEmpty())
        assertTrue(draft.orderedTaskIds.isEmpty())
    }

    @Test
    fun `confirmed progress reduces the scheduled amount`() {
        val draft = generate(
            planInput(
                tasks = listOf(task("t1", durationMinutes = 120, progressPercent = 50)),
                weeklyBlocks = listOf(weeklyBlock(startMinute = 9 * 60, endMinute = 12 * 60)),
            ),
        )

        assertEquals(60, draft.segments.sumOf { it.endMinute - it.startMinute })
    }

    @Test
    fun `a raw 100 percent feedback value is not treated as completion`() {
        // remainingWorkMinutes() only honours progress in 1..99; 100 is feedback, not a
        // status transition, so the full duration stays scheduled.
        val draft = generate(
            planInput(
                tasks = listOf(task("t1", durationMinutes = 120, progressPercent = 100)),
                weeklyBlocks = listOf(weeklyBlock(startMinute = 9 * 60, endMinute = 12 * 60)),
            ),
        )

        assertEquals(120, draft.segments.sumOf { it.endMinute - it.startMinute })
    }

    @Test
    fun `a course block inside the window pushes work out of the overlapping slot`() {
        val draft = generate(
            planInput(
                tasks = listOf(task("t1", durationMinutes = 60)),
                weeklyBlocks = listOf(
                    weeklyBlock(startMinute = 9 * 60, endMinute = 12 * 60),
                    weeklyBlock(
                        id = "course",
                        kind = TimeBlockKind.COURSE,
                        startMinute = 9 * 60,
                        endMinute = 10 * 60,
                    ),
                ),
            ),
        )

        assertEquals(1, draft.segments.size)
        assertEquals(10 * 60, draft.segments.single().startMinute)
        assertEquals(11 * 60, draft.segments.single().endMinute)
    }

    @Test
    fun `the scheduler stops at the due date horizon`() {
        val dueSoon = TEST_MONDAY
        val draft = generate(
            planInput(
                tasks = listOf(task("t1", durationMinutes = 120, dueDate = dueSoon)),
                weeklyBlocks = listOf(weeklyBlock(startMinute = 9 * 60, endMinute = 12 * 60)),
            ),
        )

        assertEquals(1, draft.segments.size)
        assertEquals(dueSoon, draft.segments.single().date)
        assertEquals(120, draft.segments.sumOf { it.endMinute - it.startMinute })
    }
}
