package com.swan1127.repland.data.room

import com.swan1127.repland.domain.model.TEST_MONDAY
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlanWithSegments.toDomain] is the only place a stored plan becomes the domain object the
 * timeline and the generator read. Unlike the flat mappers it also *derives* two things —
 * the segment order and the task order — so a mistake here reorders the user's day rather
 * than merely losing a field.
 */
class PlanWithSegmentsMappingTest {

    // ------------------------------------------------------------ plan header

    @Test
    fun `the plan header carries into the domain model`() {
        val plan = planWithSegments(plan = planEntity(id = "plan-9", createdAtEpochMillis = 4_242L, isCurrent = false))
            .toDomain()

        assertEquals("plan-9", plan.id)
        assertEquals(4_242L, plan.createdAtEpochMillis)
        assertFalse(plan.isCurrent)
    }

    @Test
    fun `a plan with no segments and no order maps to empty lists`() {
        val plan = planWithSegments(segments = emptyList(), taskOrder = emptyList()).toDomain()

        assertEquals(emptyList<Any>(), plan.segments)
        assertEquals(emptyList<String>(), plan.orderedTaskIds)
        assertFalse(plan.hasManualTaskOrder)
    }

    // ------------------------------------------------------------ segment fields

    @Test
    fun `a segment carries every field and reads its date from the epoch day`() {
        val segment = planWithSegments(
            segments = listOf(
                segmentEntity(
                    id = "seg-1",
                    taskId = "task-1",
                    dateEpochDay = LocalDate.of(2026, 3, 4).toEpochDay(),
                    startMinute = 600,
                    endMinute = 660,
                    isLocked = true,
                    trackId = "review",
                ),
            ),
        ).toDomain().segments.single()

        assertEquals("seg-1", segment.id)
        assertEquals("task-1", segment.taskId)
        assertEquals(LocalDate.of(2026, 3, 4), segment.date)
        assertEquals(600, segment.startMinute)
        assertEquals(660, segment.endMinute)
        assertTrue(segment.isLocked)
        assertEquals("review", segment.trackId)
    }

    // ------------------------------------------------------------ segment ordering

    @Test
    fun `segments come back ordered by date and then by start minute`() {
        // The stored order is deliberately scrambled; the domain order is the contract the
        // timeline relies on, so it must not depend on however Room happened to return rows.
        val plan = planWithSegments(
            segments = listOf(
                segmentEntity(id = "d2-early", dateEpochDay = day(2), startMinute = 480, endMinute = 540),
                segmentEntity(id = "d1-late", dateEpochDay = day(1), startMinute = 600, endMinute = 660),
                segmentEntity(id = "d2-late", dateEpochDay = day(2), startMinute = 600, endMinute = 660),
                segmentEntity(id = "d1-early", dateEpochDay = day(1), startMinute = 480, endMinute = 540),
            ),
        ).toDomain()

        assertEquals(
            listOf("d1-early", "d1-late", "d2-early", "d2-late"),
            plan.segments.map { it.id },
        )
    }

    @Test
    fun `two segments that start at the same minute keep their stored order`() {
        // The sort key is (date, startMinute) only, so a tie must fall back to the incoming
        // order — a stable sort. Pinned because switching to an unstable sort would shuffle
        // same-slot parallel lanes on every read.
        val plan = planWithSegments(
            segments = listOf(
                segmentEntity(id = "first", startMinute = 480, endMinute = 540, trackId = "a"),
                segmentEntity(id = "second", startMinute = 480, endMinute = 540, trackId = "b"),
            ),
        ).toDomain()

        assertEquals(listOf("first", "second"), plan.segments.map { it.id })
    }

    @Test
    fun `the segment order does not depend on the end minute`() {
        // A long segment starting first must still come first: ordering is by start, not by
        // duration or by end.
        val plan = planWithSegments(
            segments = listOf(
                segmentEntity(id = "long", startMinute = 480, endMinute = 900),
                segmentEntity(id = "short", startMinute = 600, endMinute = 630),
            ),
        ).toDomain()

        assertEquals(listOf("long", "short"), plan.segments.map { it.id })
    }

    // ------------------------------------------------------------ task order

    @Test
    fun `the task order follows the stored position`() {
        val plan = planWithSegments(
            taskOrder = listOf(
                orderEntity(taskId = "third", position = 2),
                orderEntity(taskId = "first", position = 0),
                orderEntity(taskId = "second", position = 1),
            ),
        ).toDomain()

        assertEquals(listOf("first", "second", "third"), plan.orderedTaskIds)
    }

    @Test
    fun `an empty task order falls back to the tasks named by the segments`() {
        val plan = planWithSegments(
            segments = listOf(
                segmentEntity(id = "s1", taskId = "task-a", startMinute = 480, endMinute = 540),
                segmentEntity(id = "s2", taskId = "task-b", startMinute = 600, endMinute = 660),
            ),
            taskOrder = emptyList(),
        ).toDomain()

        assertEquals(listOf("task-a", "task-b"), plan.orderedTaskIds)
    }

    @Test
    fun `the fallback order deduplicates a task that appears in several segments`() {
        val plan = planWithSegments(
            segments = listOf(
                segmentEntity(id = "s1", taskId = "task-a", startMinute = 480, endMinute = 540),
                segmentEntity(id = "s2", taskId = "task-a", startMinute = 600, endMinute = 660),
                segmentEntity(id = "s3", taskId = "task-b", startMinute = 720, endMinute = 780),
            ),
            taskOrder = emptyList(),
        ).toDomain()

        assertEquals(listOf("task-a", "task-b"), plan.orderedTaskIds)
    }

    @Test
    fun `the fallback task order follows the raw row order while the segments themselves are sorted`() {
        // FINDING, pinned rather than papered over. `segments` is sorted by (date, startMinute),
        // but the orderedTaskIds fallback is built from the RAW entity list (the `ifEmpty` reads
        // the `segments` parameter, not the sorted list built above it), so the two derived
        // fields can disagree. Room makes no ordering promise for a @Relation, so for a plan
        // with no stored order this derived task order is not reproducible.
        //
        // Blast radius, checked: the UI only forwards orderedTaskIds when hasManualTaskOrder is
        // true, and that implies a non-empty stored order, so the fallback never fires on the
        // planner path. LocalDataJsonExporter publishes orderedTaskIds unconditionally, which is
        // where the non-determinism is actually observable.
        val plan = planWithSegments(
            segments = listOf(
                segmentEntity(id = "later", taskId = "task-later", dateEpochDay = day(1), startMinute = 600, endMinute = 660),
                segmentEntity(id = "earlier", taskId = "task-earlier", dateEpochDay = day(1), startMinute = 480, endMinute = 540),
            ),
            taskOrder = emptyList(),
        ).toDomain()

        // The segments are ordered by time...
        assertEquals(listOf("earlier", "later"), plan.segments.map { it.id })
        // ...but the derived task order does not come from them.
        assertEquals(listOf("task-later", "task-earlier"), plan.orderedTaskIds)
    }

    @Test
    fun `a stored task order is used even when it does not name every segmented task`() {
        // The fallback only fires on an empty order. A partial order is the user's explicit
        // choice and must not be silently completed from the segments.
        val plan = planWithSegments(
            segments = listOf(
                segmentEntity(id = "s1", taskId = "task-a", startMinute = 480, endMinute = 540),
                segmentEntity(id = "s2", taskId = "task-b", startMinute = 600, endMinute = 660),
            ),
            taskOrder = listOf(orderEntity(taskId = "task-b", position = 0)),
        ).toDomain()

        assertEquals(listOf("task-b"), plan.orderedTaskIds)
    }

    // ------------------------------------------------------------ manual order flag

    @Test
    fun `the manual order flag is true when any stored row is manual`() {
        val plan = planWithSegments(
            taskOrder = listOf(
                orderEntity(taskId = "a", position = 0, isManual = false),
                orderEntity(taskId = "b", position = 1, isManual = true),
            ),
        ).toDomain()

        assertTrue(plan.hasManualTaskOrder)
    }

    @Test
    fun `the manual order flag is false when the stored rows are all derived`() {
        val plan = planWithSegments(
            taskOrder = listOf(
                orderEntity(taskId = "a", position = 0, isManual = false),
                orderEntity(taskId = "b", position = 1, isManual = false),
            ),
        ).toDomain()

        assertFalse(plan.hasManualTaskOrder)
    }

    @Test
    fun `the manual order flag is false for the segment-derived fallback`() {
        // No stored order means the order was derived, so it cannot be a manual one — even
        // though orderedTaskIds is non-empty.
        val plan = planWithSegments(
            segments = listOf(segmentEntity(id = "s1", taskId = "task-a", startMinute = 480, endMinute = 540)),
            taskOrder = emptyList(),
        ).toDomain()

        assertEquals(listOf("task-a"), plan.orderedTaskIds)
        assertFalse(plan.hasManualTaskOrder)
    }

    @Test
    fun `the manual order flag is independent of the order being complete`() {
        // One manual row among several is enough: the flag answers "did the user touch the
        // order", not "is every task ordered manually".
        val plan = planWithSegments(
            taskOrder = listOf(
                orderEntity(taskId = "a", position = 0, isManual = true),
                orderEntity(taskId = "b", position = 1, isManual = false),
                orderEntity(taskId = "c", position = 2, isManual = false),
            ),
        ).toDomain()

        assertTrue(plan.hasManualTaskOrder)
        assertEquals(listOf("a", "b", "c"), plan.orderedTaskIds)
    }

    // ------------------------------------------------------------ builders

    private fun day(offset: Long): Long = TEST_MONDAY.plusDays(offset).toEpochDay()

    private fun planEntity(
        id: String = "plan-1",
        createdAtEpochMillis: Long = 1_000L,
        isCurrent: Boolean = true,
    ): PlanEntity = PlanEntity(id = id, createdAtEpochMillis = createdAtEpochMillis, isCurrent = isCurrent)

    private fun segmentEntity(
        id: String,
        taskId: String = "task-1",
        dateEpochDay: Long = TEST_MONDAY.toEpochDay(),
        startMinute: Int,
        endMinute: Int,
        isLocked: Boolean = false,
        trackId: String = "focus",
    ): PlanSegmentEntity = PlanSegmentEntity(
        id = id,
        planId = "plan-1",
        taskId = taskId,
        dateEpochDay = dateEpochDay,
        startMinute = startMinute,
        endMinute = endMinute,
        isLocked = isLocked,
        trackId = trackId,
    )

    private fun orderEntity(
        taskId: String,
        position: Int,
        isManual: Boolean = false,
    ): PlanTaskOrderEntity = PlanTaskOrderEntity(
        planId = "plan-1",
        taskId = taskId,
        position = position,
        isManual = isManual,
    )

    private fun planWithSegments(
        plan: PlanEntity = planEntity(),
        segments: List<PlanSegmentEntity> = emptyList(),
        taskOrder: List<PlanTaskOrderEntity> = emptyList(),
    ): PlanWithSegments = PlanWithSegments(plan = plan, segments = segments, taskOrder = taskOrder)
}
