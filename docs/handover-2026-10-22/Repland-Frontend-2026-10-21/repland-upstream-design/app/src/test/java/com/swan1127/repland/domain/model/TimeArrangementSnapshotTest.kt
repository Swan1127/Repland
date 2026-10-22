package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * [TimeArrangementSnapshot] is the unit undo/redo captures and restores.
 *
 * Its doc records that an earlier version of this project kept a hand-written "inverse table"
 * per operation, that table drifted out of sync with the snapshot, and undo silently stopped
 * restoring part of the state — nobody noticed until a test compared the two lists. These tests
 * are that comparison.
 */
class TimeArrangementSnapshotTest {

    private val block = WeeklyTimeBlock(
        id = "b1",
        title = "高等数学",
        kind = TimeBlockKind.COURSE,
        dayOfWeek = DayOfWeek.WEDNESDAY,
        startMinute = 10 * 60,
        endMinute = 11 * 60 + 35,
        weekPattern = "1-16周",
        createdAtEpochMillis = 111L,
        updatedAtEpochMillis = 222L,
        trackId = "course-b",
        note = "带教材",
    )

    private val override = DateOverride(
        id = "o1",
        title = "调休补课",
        type = DateOverrideType.AVAILABLE,
        date = TEST_MONDAY,
        startMinute = 8 * 60,
        endMinute = 12 * 60,
        createdAtEpochMillis = 333L,
        updatedAtEpochMillis = 444L,
        note = "补上周五的课",
    )

    @Test
    fun `an empty snapshot reports itself empty`() {
        assertTrue(TimeArrangementSnapshot.Empty.isEmpty)
        assertTrue(TimeArrangementSnapshot(emptyList(), emptyList()).isEmpty)
        assertFalse(TimeArrangementSnapshot(listOf(block.toDraft()), emptyList()).isEmpty)
        assertFalse(TimeArrangementSnapshot(emptyList(), listOf(override.toDraft())).isEmpty)
    }

    @Test
    fun `a stored block round-trips into a draft without losing a single field`() {
        val draft = block.toDraft()

        assertEquals("b1", draft.id)
        assertEquals("高等数学", draft.title)
        assertEquals(TimeBlockKind.COURSE, draft.kind)
        assertEquals(DayOfWeek.WEDNESDAY, draft.dayOfWeek)
        assertEquals(10 * 60, draft.startMinute)
        assertEquals(11 * 60 + 35, draft.endMinute)
        assertEquals("1-16周", draft.weekPattern)
        assertEquals("course-b", draft.trackId)
        assertEquals("带教材", draft.note)
        // The row must come back stamped with the moment it was created, not "now".
        assertEquals(111L, draft.createdAtEpochMillis)
    }

    @Test
    fun `a stored override round-trips into a draft without losing a single field`() {
        val draft = override.toDraft()

        assertEquals("o1", draft.id)
        assertEquals("调休补课", draft.title)
        assertEquals(DateOverrideType.AVAILABLE, draft.type)
        assertEquals(TEST_MONDAY, draft.date)
        assertEquals(8 * 60, draft.startMinute)
        assertEquals(12 * 60, draft.endMinute)
        assertEquals("补上周五的课", draft.note)
        assertEquals(333L, draft.createdAtEpochMillis)
    }

    @Test
    fun `a restored block keeps its identity so a redo can still address the same row`() {
        val restored = block.toDraft()

        // Minting a fresh id here would break redo -> undo, which addresses rows by id.
        assertEquals(block.id, restored.id)
    }

    @Test
    fun `the draft shape carries every stored field a restore must replay`() {
        // Guard against the documented drift: if a field is added to the stored type and not to
        // the draft, a restore silently stops replaying it. updatedAtEpochMillis is the one
        // deliberate exception -- it is bookkeeping written at restore time, not user state.
        assertEquals(
            "a field was added to WeeklyTimeBlock; decide whether a restore must replay it",
            setOf("updatedAtEpochMillis"),
            storedFields(WeeklyTimeBlock::class.java) - storedFields(WeeklyTimeBlockDraft::class.java),
        )
        assertEquals(
            "a field was added to DateOverride; decide whether a restore must replay it",
            setOf("updatedAtEpochMillis"),
            storedFields(DateOverride::class.java) - storedFields(DateOverrideDraft::class.java),
        )
    }

    @Test
    fun `the draft invents no field the stored row does not have`() {
        // Parentheses matter: `setA - setB.isEmpty()` parses as `setA - (setB.isEmpty())`.
        assertTrue(
            (storedFields(WeeklyTimeBlockDraft::class.java) - storedFields(WeeklyTimeBlock::class.java)).isEmpty(),
        )
        assertTrue(
            (storedFields(DateOverrideDraft::class.java) - storedFields(DateOverride::class.java)).isEmpty(),
        )
    }

    private fun storedFields(type: Class<*>): Set<String> =
        type.declaredFields
            .filterNot { it.isSynthetic }
            .map { it.name }
            .toSet()
}
