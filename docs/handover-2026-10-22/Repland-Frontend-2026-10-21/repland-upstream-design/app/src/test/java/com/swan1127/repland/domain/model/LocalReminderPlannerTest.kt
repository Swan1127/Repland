package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * [LocalReminderPlanner] turns a *confirmed* plan into local alarm candidates.
 *
 * The decision this suite pins: SEGMENT_START fires at the segment's exact start minute.
 * It is a "start now" nudge, not a "get ready" warning, so there is deliberately no lead
 * time. Changing that is a product decision, and this test makes it a visible one.
 */
class LocalReminderPlannerTest {

    private fun plan(
        confirmedPlan: ConfirmedPlan?,
        tasks: List<Task> = emptyList(),
    ): List<LocalReminder> = LocalReminderPlanner.plan(confirmedPlan, tasks, TEST_CLOCK)

    @Test
    fun `no confirmed plan yields no reminders`() {
        assertTrue(plan(null, listOf(task("t1"))).isEmpty())
    }

    @Test
    fun `a segment starting later today produces one start reminder at its exact start`() {
        val reminders = plan(
            confirmedPlan(segments = listOf(segment("s1", taskId = "t1", startMinute = 9 * 60, endMinute = 10 * 60))),
            tasks = listOf(task("t1")),
        )

        val start = reminders.single { it.kind == ReminderKind.SEGMENT_START }
        assertEquals(Instant.parse("2026-03-02T09:00:00Z"), start.triggerAt)
        assertEquals("plan-1:s1", start.key)
        assertEquals("t1", start.taskId)
        assertEquals("Task t1", start.taskName)
    }

    @Test
    fun `a segment that already started produces no start reminder`() {
        // 06:00 is before the fixed clock's 07:00, so the moment has passed.
        val reminders = plan(
            confirmedPlan(segments = listOf(segment("s1", taskId = "t1", startMinute = 6 * 60, endMinute = 7 * 60))),
            tasks = listOf(task("t1")),
        )

        assertTrue(reminders.none { it.kind == ReminderKind.SEGMENT_START })
    }

    @Test
    fun `a segment whose task is no longer active produces no start reminder`() {
        val reminders = plan(
            confirmedPlan(segments = listOf(segment("s1", taskId = "t1", startMinute = 9 * 60, endMinute = 10 * 60))),
            tasks = listOf(task("t1", status = TaskStatus.COMPLETED)),
        )

        assertTrue(reminders.none { it.kind == ReminderKind.SEGMENT_START })
    }

    @Test
    fun `a segment whose task is unknown produces no start reminder`() {
        val reminders = plan(
            confirmedPlan(segments = listOf(segment("s1", taskId = "ghost", startMinute = 9 * 60, endMinute = 10 * 60))),
            tasks = listOf(task("t1")),
        )

        assertTrue(reminders.none { it.kind == ReminderKind.SEGMENT_START })
    }

    @Test
    fun `a deadline warning fires at 20_00 on the day before the due date`() {
        val dueDate = TEST_MONDAY.plusDays(3)

        val reminders = plan(
            confirmedPlan(),
            tasks = listOf(task("t1", dueDate = dueDate)),
        )

        val deadline = reminders.single { it.kind == ReminderKind.DEADLINE }
        assertEquals(Instant.parse("2026-03-04T20:00:00Z"), deadline.triggerAt)
        assertEquals(dueDate, deadline.dueDate)
        assertEquals("t1", deadline.key)
    }

    @Test
    fun `a deadline whose warning moment has already passed produces no reminder`() {
        val reminders = plan(
            confirmedPlan(),
            tasks = listOf(task("t1", dueDate = TEST_MONDAY)),
        )

        assertTrue(reminders.none { it.kind == ReminderKind.DEADLINE })
    }

    @Test
    fun `a task without a due date produces no deadline reminder`() {
        val reminders = plan(confirmedPlan(), tasks = listOf(task("t1", dueDate = null)))

        assertTrue(reminders.none { it.kind == ReminderKind.DEADLINE })
    }

    @Test
    fun `the daily review reminder always exists and lands at 20_30`() {
        val reminders = plan(confirmedPlan())

        val review = reminders.single()
        assertEquals(ReminderKind.DAILY_REVIEW, review.kind)
        assertEquals("daily-review", review.key)
        assertEquals(Instant.parse("2026-03-02T20:30:00Z"), review.triggerAt)
    }

    @Test
    fun `once the review time has passed the daily reminder rolls to tomorrow`() {
        val lateClock = java.time.Clock.fixed(
            Instant.parse("2026-03-02T21:00:00Z"),
            java.time.ZoneOffset.UTC,
        )

        val reminders = LocalReminderPlanner.plan(confirmedPlan(), emptyList(), lateClock)

        val review = reminders.single()
        assertEquals(Instant.parse("2026-03-03T20:30:00Z"), review.triggerAt)
    }

    @Test
    fun `reminders are returned in trigger order`() {
        val reminders = plan(
            confirmedPlan(segments = listOf(segment("s1", taskId = "t1", startMinute = 22 * 60, endMinute = 23 * 60))),
            tasks = listOf(
                task("t1"),
                task("t2", dueDate = TEST_MONDAY.plusDays(1)),
            ),
        )

        // 20:00 deadline warning, 20:30 daily review, 22:00 segment start.
        assertEquals(
            listOf(ReminderKind.DEADLINE, ReminderKind.DAILY_REVIEW, ReminderKind.SEGMENT_START),
            reminders.map { it.kind },
        )
        assertEquals(reminders.map { it.triggerAt }, reminders.map { it.triggerAt }.sorted())
    }
}
