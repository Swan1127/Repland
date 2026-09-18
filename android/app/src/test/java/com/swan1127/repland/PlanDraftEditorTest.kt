package com.swan1127.repland

import com.swan1127.repland.domain.model.PlanDraft
import com.swan1127.repland.domain.model.PlanDraftEditor
import com.swan1127.repland.domain.model.PlannedSegment
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanDraftEditorTest {
    private val date = LocalDate.of(2026, 9, 18)

    @Test
    fun `draft task order can be moved without changing scheduled times`() {
        val draft = draft(
            segments = listOf(segment("a", "task-a", 9 * 60, 10 * 60)),
            order = listOf("task-a", "task-b"),
        )

        val edited = PlanDraftEditor.moveTask(draft, "task-b", -1)

        assertEquals(listOf("task-b", "task-a"), edited.orderedTaskIds)
        assertEquals(draft.segments, edited.segments)
    }

    @Test
    fun `segment can be moved and a collision is explicitly detectable`() {
        val first = segment("a", "task-a", 9 * 60, 10 * 60)
        val second = segment("b", "task-b", 10 * 60, 11 * 60)
        val draft = draft(listOf(first, second))
        val moved = first.copy(date = date.plusDays(1), startMinute = 10 * 60, endMinute = 11 * 60)

        val edited = PlanDraftEditor.moveSegment(draft, moved)

        assertEquals(moved, edited.segments.single { it.id == "a" })
        assertFalse(PlanDraftEditor.overlapsAnotherSegment(edited, moved))
        assertTrue(PlanDraftEditor.overlapsAnotherSegment(draft, first.copy(startMinute = 10 * 60, endMinute = 11 * 60)))
    }

    @Test
    fun `segment split merge and lock remain draft-only operations`() {
        val draft = draft(listOf(segment("a", "task-a", 9 * 60, 11 * 60)))

        val split = PlanDraftEditor.splitSegment(draft, "a")
        val locked = PlanDraftEditor.setSegmentLocked(split, "a", true)

        assertEquals(2, split.segments.size)
        assertTrue(locked.segments.single { it.id == "a" }.isLocked)
        assertFalse(PlanDraftEditor.canMergeWithAdjacentSegment(locked, "a"))
        assertEquals(1, draft.segments.size)
    }

    @Test
    fun `adjacent segments with the same lock state can merge`() {
        val draft = draft(
            listOf(
                segment("a", "task-a", 9 * 60, 10 * 60),
                segment("b", "task-a", 10 * 60, 11 * 60),
            ),
        )

        val merged = PlanDraftEditor.mergeWithAdjacentSegment(draft, "a")

        assertEquals(1, merged.segments.size)
        assertEquals(9 * 60, merged.segments.single().startMinute)
        assertEquals(11 * 60, merged.segments.single().endMinute)
    }

    private fun draft(
        segments: List<PlannedSegment>,
        order: List<String> = segments.map(PlannedSegment::taskId).distinct(),
    ) = PlanDraft(
        generatedAt = LocalDateTime.of(2026, 9, 17, 9, 0),
        segments = segments,
        pendingTaskIds = emptyList(),
        unscheduledTasks = emptyList(),
        orderedTaskIds = order,
    )

    private fun segment(id: String, taskId: String, start: Int, end: Int) = PlannedSegment(
        id = id,
        taskId = taskId,
        date = date,
        startMinute = start,
        endMinute = end,
    )
}
