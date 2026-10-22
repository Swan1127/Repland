package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class AutonomousPlanRepairTest {
    private val day = LocalDate.of(2026, 10, 16)
    private val now = day.atTime(8, 0)

    @Test fun unchanged_future_keeps_ids_times_lanes_and_does_not_become_locked() {
        val original = PlannedSegment("original", "a", day, 600, 660, trackId = "personal")
        val result = repair(listOf(task("a", 60)), listOf(original))
        assertEquals(listOf(original), result.segments)
        assertTrue(result.pendingTaskIds.isEmpty())
    }

    @Test fun a_new_task_uses_free_time_without_shuffling_the_existing_plan() {
        val original = PlannedSegment("original", "a", day, 600, 660)
        val result = repair(listOf(task("a", 60), task("b", 30)), listOf(original))
        assertTrue(original in result.segments)
        assertEquals(480, result.segments.single { it.taskId == "b" }.startMinute)
        assertTrue(result.pendingTaskIds.isEmpty())
    }

    @Test fun only_the_soft_course_collision_moves_when_a_new_course_is_added() {
        val stable = PlannedSegment("stable", "a", day, 540, 570)
        val collision = PlannedSegment("collision", "b", day, 600, 630)
        val input = input(listOf(task("a", 30), task("b", 30)), blocked = listOf(block("course", TimeBlockKind.COURSE, 600, 630)))
        val result = AutonomousPlanRepair.repair(input, plan(listOf(stable, collision)), now)
        assertTrue(stable in result.segments)
        assertFalse(result.segments.any { it.id == collision.id })
        assertFalse(result.segments.any { it.startMinute < 630 && it.endMinute > 600 })
        assertTrue(result.pendingTaskIds.isEmpty())
    }

    @Test fun locked_and_required_collisions_remain_visible_and_pending_instead_of_silently_moving() {
        val locked = PlannedSegment("lock", "a", day, 600, 630, isLocked = true)
        val required = PlannedSegment("fixed", "b", day, 630, 660, trackId = "personal")
        val input = input(listOf(task("a", 30), task("b", 30).copy(userPriority = TaskPriority.REQUIRED)),
            blocked = listOf(block("course", TimeBlockKind.COURSE, 600, 660)))
        val result = AutonomousPlanRepair.repair(input, plan(listOf(locked, required)), now)
        assertEquals(listOf(locked, required), result.segments)
        assertEquals(setOf("a", "b"), result.pendingTaskIds.toSet())
        assertTrue(result.unscheduledTasks.isEmpty())
        assertEquals(setOf("lock", "fixed"), AutonomousPlanRepair.conflicts(input, result, now)
            .filter { it.reason == AutonomousPlanConflictReason.HARD_TIME_BLOCK }.map { it.segmentId }.toSet())
        assertFalse(result.segments.single { it.id == "fixed" }.isLocked)
    }

    @Test fun history_does_not_count_as_completed_work_and_closed_future_is_removed() {
        val past = PlannedSegment("past", "a", day.minusDays(1), 600, 660)
        val donePast = PlannedSegment("done-past", "done", day.minusDays(1), 660, 690)
        val doneFuture = PlannedSegment("done-future", "done", day, 660, 690, isLocked = true)
        val result = repair(listOf(task("a", 120).copy(progressPercent = 50), task("done", 30).copy(status = TaskStatus.COMPLETED)),
            listOf(past, donePast, doneFuture))
        assertTrue(past in result.segments)
        assertTrue(donePast in result.segments)
        assertFalse(doneFuture in result.segments)
        assertEquals(60, result.segments.filter { it.taskId == "a" && it.date == day }.sumOf { it.endMinute - it.startMinute })
    }

    @Test fun partial_progress_trims_surplus_future_tail_without_moving_the_remaining_work() {
        val original = PlannedSegment("original", "a", day, 600, 720)
        val extra = PlannedSegment("extra", "a", day, 780, 810)
        val result = repair(listOf(task("a", 120).copy(progressPercent = 50)), listOf(original, extra))
        assertEquals(listOf(original.copy(endMinute = 660)), result.segments)
        assertTrue(result.pendingTaskIds.isEmpty())
    }

    @Test fun an_ongoing_slot_is_preserved_but_elapsed_capacity_is_not_remaining_work() {
        val original = PlannedSegment("started", "a", day, 480, 540)
        val result = AutonomousPlanRepair.repair(input(listOf(task("a", 120).copy(progressPercent = 50))),
            plan(listOf(original)), day.atTime(8, 30), runningTaskId = "a")
        assertTrue(original in result.segments)
        assertEquals(30, result.segments.filter { it.id != "started" }.sumOf { it.endMinute - it.startMinute })
        assertTrue(result.segments.filter { it.id != "started" }.all { it.startMinute >= 540 })
    }

    @Test fun missing_availability_preserves_manual_placements_and_reports_only_real_unallocated_work() {
        val placed = PlannedSegment("manual", "a", day, 600, 660)
        val input = PlanGenerationInput(listOf(task("a", 60), task("b", 45)), emptyList(), emptyList(), null)
        val result = AutonomousPlanRepair.repair(input, plan(listOf(placed)), now)
        assertEquals(listOf(placed), result.segments)
        assertEquals(listOf("b"), result.pendingTaskIds)
        assertEquals(45, result.unscheduledTasks.single().remainingMinutes)
    }

    @Test fun unknown_duration_gets_a_planning_reservation_without_mutating_task_evidence() {
        val unknown = task("a", null)
        val result = repair(listOf(unknown))
        assertEquals(30, result.segments.sumOf { it.endMinute - it.startMinute })
        assertNull(unknown.totalDurationMinutes)
        assertNull(unknown.actualDurationMinutes)
        assertNull(unknown.progressPercent)
    }

    @Test fun intended_day_and_true_deadline_bound_both_retained_and_new_soft_work() {
        val tomorrow = day.plusDays(1)
        val input = PlanGenerationInput(listOf(task("a", 60).copy(scheduledForDate = tomorrow, dueDate = tomorrow)),
            emptyList(), listOf(available("today", day, 480, 720), available("tomorrow", tomorrow, 600, 630),
                available("late", day.plusDays(2), 480, 720)), null)
        val tooEarly = PlannedSegment("early", "a", day, 600, 630)
        val tooLate = PlannedSegment("late", "a", day.plusDays(2), 600, 630)
        val result = AutonomousPlanRepair.repair(input, plan(listOf(tooEarly, tooLate)), now)
        assertEquals(listOf(tomorrow), result.segments.map { it.date })
        assertEquals(30, result.unscheduledTasks.single().remainingMinutes)
        assertEquals(UnscheduledReason.CAPACITY_BEFORE_DUE_DATE, result.unscheduledTasks.single().reason)
    }

    @Test fun the_rolling_horizon_never_invents_capacity_on_day_31_or_before_now() {
        val input = PlanGenerationInput(listOf(task("a", 60).copy(dueDate = null)), emptyList(),
            listOf(available("last", day.plusDays(29), 600, 630), available("outside", day.plusDays(30), 600, 660)), null)
        val result = AutonomousPlanRepair.repair(input, null, day.atTime(8, 0, 1))
        assertEquals(listOf(day.plusDays(29)), result.segments.map { it.date })
        assertEquals(30, result.unscheduledTasks.single().remainingMinutes)
        val todayResult = AutonomousPlanRepair.repair(input(listOf(task("a", 30))), null, day.atTime(8, 0, 1))
        assertEquals(510, todayResult.segments.single().startMinute)
    }

    @Test fun a_requested_evening_window_cannot_leak_into_free_morning_or_another_day() {
        val windows = listOf(AutonomousTimeWindow("a", day, 1080, 1440))
        val input = input(listOf(task("a", 60)), end = 1200)
        val original = PlannedSegment("wrong-window", "a", day, 600, 660)
        val result = AutonomousPlanRepair.repair(input, plan(listOf(original)), now, timeWindows = windows)
        assertEquals(1080, result.segments.single().startMinute)
        assertEquals(1140, result.segments.single().endMinute)
        assertTrue(result.pendingTaskIds.isEmpty())
    }

    @Test fun an_unavailable_exact_start_stays_pending_instead_of_becoming_a_later_guess() {
        val window = AutonomousTimeWindow("a", day, 600, 660, exactStart = true)
        val input = input(listOf(task("a", 30)), blocked = listOf(block("course", TimeBlockKind.COURSE, 600, 630)))
        val result = AutonomousPlanRepair.repair(input, null, now, timeWindows = listOf(window))
        assertTrue(result.segments.isEmpty())
        assertEquals(listOf("a"), result.pendingTaskIds)
        assertEquals(30, result.unscheduledTasks.single().remainingMinutes)
    }

    @Test fun explicit_minute_start_is_exact_and_the_planning_quantum_is_a_duration() {
        val window = AutonomousTimeWindow("a", day, 555, 615, exactStart = true)
        val result = AutonomousPlanRepair.repair(input(listOf(task("a", 45))), null, now, timeWindows = listOf(window))
        assertEquals(555, result.segments.single().startMinute)
        assertEquals(615, result.segments.single().endMinute)
        assertTrue(result.pendingTaskIds.isEmpty())
    }

    @Test fun a_missed_exact_start_is_not_assumed_to_have_happened() {
        val window = AutonomousTimeWindow("a", day, 600, 720, exactStart = true)
        val past = PlannedSegment("past", "a", day, 600, 630)
        val result = AutonomousPlanRepair.repair(input(listOf(task("a", 60))), plan(listOf(past)), day.atTime(11, 0), timeWindows = listOf(window))
        assertEquals(listOf(past), result.segments)
        assertEquals(60, result.unscheduledTasks.single().remainingMinutes)
    }

    @Test fun adjacent_explicit_availability_blocks_form_a_continuous_valid_window() {
        val input = PlanGenerationInput(listOf(task("a", 60)), emptyList(),
            listOf(available("first", day, 555, 585), available("second", day, 585, 615)), null)
        val original = PlannedSegment("manual", "a", day, 555, 615)
        val result = AutonomousPlanRepair.repair(input, plan(listOf(original)), now)
        assertEquals(listOf(original), result.segments)
    }

    @Test fun paused_autonomy_updates_existing_work_without_allocating_other_pending_tasks() {
        val original = PlannedSegment("original", "a", day, 600, 720)
        val doneFuture = PlannedSegment("done", "done", day, 780, 810)
        val input = input(listOf(task("a", 120).copy(progressPercent = 50), task("b", 30),
            task("done", 30).copy(status = TaskStatus.COMPLETED)))
        val result = AutonomousPlanRepair.repair(input, plan(listOf(original, doneFuture)), now, scheduleNew = false)
        assertEquals(listOf(original.copy(endMinute = 660)), result.segments)
        assertEquals(listOf("b"), result.pendingTaskIds)
        assertEquals(30, result.unscheduledTasks.single().remainingMinutes)
    }

    @Test fun arranging_tomorrow_leaves_other_days_intact_and_adds_only_tomorrow_work() {
        val tomorrow = day.plusDays(1)
        val later = day.plusDays(2)
        val original = PlannedSegment("later-original", "a", later, 600, 660, trackId = "personal")
        val closed = PlannedSegment("closed-later", "done", later, 660, 690, isLocked = true)
        val input = PlanGenerationInput(listOf(task("a", 60).copy(dueDate = null), task("b", 30).copy(dueDate = null),
            task("done", 30).copy(status = TaskStatus.CANCELLED)), emptyList(),
            listOf(available("today", day, 480, 720), available("tomorrow", tomorrow, 600, 660)), null)
        val result = AutonomousPlanRepair.repair(input, plan(listOf(original, closed)), now, targetDate = tomorrow)
        assertTrue(original in result.segments)
        assertFalse(closed in result.segments)
        assertEquals(tomorrow, result.segments.single { it.taskId == "b" }.date)
        assertFalse(result.segments.any { it.date == day })
    }

    @Test fun arranging_tonight_keeps_the_morning_plan_and_only_fills_evening_capacity() {
        val morning = PlannedSegment("morning", "a", day, 600, 630)
        val result = AutonomousPlanRepair.repair(input(listOf(task("a", 30), task("b", 30)), end = 1200),
            plan(listOf(morning)), now, targetDate = day, targetStartMinute = 1080, targetEndMinute = 1440)
        assertTrue(morning in result.segments)
        assertEquals(1080, result.segments.single { it.taskId == "b" }.startMinute)
        assertEquals(1110, result.segments.single { it.taskId == "b" }.endMinute)
    }

    @Test fun excluded_tasks_keep_valid_existing_work_without_refilling_while_other_tasks_can_be_scheduled() {
        val existing = PlannedSegment("existing", "a", day, 600, 630)
        val excluded = task("a", 60)
        val other = task("b", 30)
        val result = AutonomousPlanRepair.repair(input(listOf(excluded, other)), plan(listOf(existing)), now,
            excludedFromNewTaskIds = setOf("a"))
        assertEquals(listOf(existing), result.segments.filter { it.taskId == "a" })
        assertEquals(30, result.segments.filter { it.taskId == "b" }.sumOf { it.endMinute - it.startMinute })
        assertEquals(listOf("a"), result.pendingTaskIds)
        assertEquals(UnscheduledTask("a", 30, UnscheduledReason.CAPACITY_BEFORE_DUE_DATE), result.unscheduledTasks.single())
        assertEquals(TaskStatus.NOT_STARTED, excluded.status)
        assertNull(excluded.progressPercent)
    }

    private fun repair(tasks: List<Task>, segments: List<PlannedSegment> = emptyList()) =
        AutonomousPlanRepair.repair(input(tasks), plan(segments), now)

    private fun input(tasks: List<Task>, end: Int = 960, blocked: List<WeeklyTimeBlock> = emptyList()) =
        PlanGenerationInput(tasks, listOf(block("available", TimeBlockKind.AVAILABLE, 480, end)) + blocked, emptyList(), null)

    private fun plan(segments: List<PlannedSegment>) = ConfirmedPlan("current", 1, true, segments)
    private fun task(id: String, duration: Int?) = Task(id, id, id, TaskCategory.COURSE, TaskPriority.MEDIUM,
        1, duration, day, TaskStatus.NOT_STARTED, null, null, null, 0, 1, 1)
    private fun block(id: String, kind: TimeBlockKind, start: Int, end: Int) =
        WeeklyTimeBlock(id, id, kind, day.dayOfWeek, start, end, null, 1, 1)
    private fun available(id: String, date: LocalDate, start: Int, end: Int) =
        DateOverride(id, id, DateOverrideType.AVAILABLE, date, start, end, 1, 1)
}
