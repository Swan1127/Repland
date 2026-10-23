package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlanDraftEditor] is the only place a user's draft is mutated before it is accepted.
 * Every operation here must be a pure function of the draft it is handed.
 */
class PlanDraftEditorTest {

    @Test
    fun `moveTask clamps at the edges instead of wrapping`() {
        val draft = planDraft(orderedTaskIds = listOf("a", "b", "c"))

        assertEquals(listOf("a", "b", "c"), PlanDraftEditor.moveTask(draft, "a", -1).orderedTaskIds)
        assertEquals(listOf("b", "c", "a"), PlanDraftEditor.moveTask(draft, "a", 99).orderedTaskIds)
    }

    @Test
    fun `moveTask marks the draft as manually ordered`() {
        val draft = planDraft(orderedTaskIds = listOf("a", "b"))

        assertTrue(PlanDraftEditor.moveTask(draft, "b", -1).hasManualTaskOrder)
    }

    @Test
    fun `moveTask ignores an id that is not in the order`() {
        val draft = planDraft(orderedTaskIds = listOf("a", "b"))

        assertEquals(draft, PlanDraftEditor.moveTask(draft, "zzz", 1))
    }

    @Test
    fun `splitSegment refuses a segment shorter than one hour`() {
        val draft = planDraft(
            segments = listOf(segment("s1", startMinute = 9 * 60, endMinute = 9 * 60 + 30)),
        )

        assertFalse(PlanDraftEditor.canSplit(draft.segments.single()))
        assertThrows(IllegalArgumentException::class.java) {
            PlanDraftEditor.splitSegment(draft, "s1")
        }
    }

    @Test
    fun `splitSegment cuts at the midpoint and keeps the total span`() {
        val draft = planDraft(
            segments = listOf(segment("s1", startMinute = 9 * 60, endMinute = 11 * 60)),
        )

        val split = PlanDraftEditor.splitSegment(draft, "s1")

        assertEquals(2, split.segments.size)
        val ordered = split.segments.sortedBy { it.startMinute }
        assertEquals(9 * 60, ordered[0].startMinute)
        assertEquals(10 * 60, ordered[0].endMinute)
        assertEquals(10 * 60, ordered[1].startMinute)
        assertEquals(11 * 60, ordered[1].endMinute)
        assertEquals(120, split.segments.sumOf { it.endMinute - it.startMinute })
        assertEquals("the two halves must not share an id", 2, split.segments.map { it.id }.toSet().size)
    }

    @Test
    fun `mergeWithAdjacentSegment joins two touching segments of the same task`() {
        val draft = planDraft(
            segments = listOf(
                segment("s1", startMinute = 9 * 60, endMinute = 10 * 60),
                segment("s2", startMinute = 10 * 60, endMinute = 11 * 60),
            ),
        )

        val merged = PlanDraftEditor.mergeWithAdjacentSegment(draft, "s1")

        assertEquals(1, merged.segments.size)
        assertEquals(9 * 60, merged.segments.single().startMinute)
        assertEquals(11 * 60, merged.segments.single().endMinute)
    }

    @Test
    fun `mergeWithAdjacentSegment refuses to join across rhythm lanes`() {
        val draft = planDraft(
            segments = listOf(
                segment("s1", startMinute = 9 * 60, endMinute = 10 * 60, trackId = "focus"),
                segment("s2", startMinute = 10 * 60, endMinute = 11 * 60, trackId = "review"),
            ),
        )

        assertFalse(PlanDraftEditor.canMergeWithAdjacentSegment(draft, "s1"))
        assertEquals(2, PlanDraftEditor.mergeWithAdjacentSegment(draft, "s1").segments.size)
    }

    @Test
    fun `mergeWithAdjacentSegment leaves a gap alone`() {
        val draft = planDraft(
            segments = listOf(
                segment("s1", startMinute = 9 * 60, endMinute = 10 * 60),
                segment("s2", startMinute = 11 * 60, endMinute = 12 * 60),
            ),
        )

        assertFalse(PlanDraftEditor.canMergeWithAdjacentSegment(draft, "s1"))
    }

    @Test
    fun `isValidSegment enforces 30-minute alignment`() {
        assertTrue(
            PlanDraftEditor.isValidSegment(segment("s1", startMinute = 9 * 60, endMinute = 10 * 60)),
        )
        assertFalse(
            PlanDraftEditor.isValidSegment(
                segment("s2", startMinute = 9 * 60 + 15, endMinute = 10 * 60),
            ),
        )
        assertFalse(
            "an inverted range is never valid",
            PlanDraftEditor.isValidSegment(segment("s3", startMinute = 10 * 60, endMinute = 9 * 60)),
        )
    }

    @Test
    fun `moveSegment rejects a segment that is not part of the draft`() {
        val draft = planDraft(
            segments = listOf(segment("s1", startMinute = 9 * 60, endMinute = 10 * 60)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            PlanDraftEditor.moveSegment(
                draft,
                segment("s9", startMinute = 11 * 60, endMinute = 12 * 60),
            )
        }
    }

    @Test
    fun `moveSegment rejects an unaligned range`() {
        val draft = planDraft(
            segments = listOf(segment("s1", startMinute = 9 * 60, endMinute = 10 * 60)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            PlanDraftEditor.moveSegment(
                draft,
                segment("s1", startMinute = 9 * 60 + 10, endMinute = 10 * 60),
            )
        }
    }

    @Test
    fun `overlapsAnotherSegment only compares within the same day and lane`() {
        val draft = planDraft(
            segments = listOf(segment("s1", startMinute = 9 * 60, endMinute = 10 * 60)),
        )

        assertTrue(
            PlanDraftEditor.overlapsAnotherSegment(
                draft,
                segment("s2", startMinute = 9 * 60 + 30, endMinute = 10 * 60 + 30),
            ),
        )
        assertFalse(
            "touching end-to-start is not an overlap",
            PlanDraftEditor.overlapsAnotherSegment(
                draft,
                segment("s3", startMinute = 10 * 60, endMinute = 11 * 60),
            ),
        )
        assertFalse(
            "a different lane may share the same wall-clock time",
            PlanDraftEditor.overlapsAnotherSegment(
                draft,
                segment("s4", startMinute = 9 * 60, endMinute = 10 * 60, trackId = "review"),
            ),
        )
    }

    @Test
    fun `setSegmentLocked only touches the requested segment`() {
        val draft = planDraft(
            segments = listOf(
                segment("s1", startMinute = 9 * 60, endMinute = 10 * 60),
                segment("s2", startMinute = 10 * 60, endMinute = 11 * 60),
            ),
        )

        val locked = PlanDraftEditor.setSegmentLocked(draft, "s1", true)

        assertTrue(locked.segments.first { it.id == "s1" }.isLocked)
        assertFalse(locked.segments.first { it.id == "s2" }.isLocked)
    }
}
