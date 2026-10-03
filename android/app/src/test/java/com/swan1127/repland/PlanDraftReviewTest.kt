package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class PlanDraftReviewTest {
    private val segment = PlannedSegment("old", "a", LocalDate.of(2026, 10, 4), 600, 660)
    private fun draft(segments: List<PlannedSegment> = listOf(segment)) = PlanDraft(LocalDateTime.of(2026, 10, 3, 12, 0), segments, emptyList(), emptyList(), listOf("a", "b"), sourceRevision = "source")
    private val current = ConfirmedPlan("plan", 0, true, listOf(segment), listOf("a", "b"))

    @Test fun regenerated_ids_are_not_schedule_changes() {
        assertTrue(PlanDraftReview.changes(draft(listOf(segment.copy(id = "new"))), current, listOf("a", "b")).schedules.isEmpty())
    }
    @Test fun moved_and_removed_placements_are_visible() {
        val moved = segment.copy(startMinute = 660, endMinute = 720)
        val change = PlanDraftReview.changes(draft(listOf(moved)), current, emptyList()).schedules.single()
        assertEquals(listOf(segment), change.before)
        assertEquals(listOf(moved), change.after)
        assertTrue(PlanDraftReview.changes(draft(emptyList()), current, emptyList()).schedules.single().after.isEmpty())
    }
    @Test fun order_only_never_reports_plan_removal() {
        val changes = PlanDraftReview.changes(draft(emptyList()).copy(orderOnly = true, orderedTaskIds = listOf("b", "a")), current, listOf("a", "b"))
        assertTrue(changes.schedules.isEmpty())
        assertEquals(2, changes.order.size)
    }
    @Test fun defer_is_idempotent_and_preserves_source_order_and_current() {
        val source = draft().copy(unscheduledTasks = listOf(UnscheduledTask("a", 30, UnscheduledReason.CAPACITY_IN_ROLLING_WINDOW)))
        val next = PlanDraftReview.defer(source, "a")
        assertTrue(next.segments.isEmpty())
        assertEquals(listOf("a"), next.pendingTaskIds)
        assertEquals(90, next.unscheduledTasks.single().remainingMinutes)
        assertEquals(UnscheduledReason.USER_DEFERRED, next.unscheduledTasks.single().reason)
        assertEquals(source.sourceRevision, next.sourceRevision)
        assertEquals(source.orderedTaskIds, next.orderedTaskIds)
        assertEquals(next, PlanDraftReview.defer(next, "a"))
        assertEquals(listOf(segment), current.segments)
    }
    @Test fun locked_and_protected_placements_cannot_be_deferred() {
        assertTrue(runCatching { PlanDraftReview.defer(draft(listOf(segment.copy(isLocked = true))), "a") }.isFailure)
        assertTrue(runCatching { PlanDraftReview.defer(draft(), "a", setOf("old")) }.isFailure)
        assertTrue(runCatching { PlanDraftReview.defer(draft().copy(orderOnly = true), "a") }.isFailure)
    }
}
