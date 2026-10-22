package com.swan1127.repland.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDayPlannerTest {

    private val day = LocalDate.of(2026, 10, 21)

    private fun candidate(
        title: String,
        duration: Int?,
        start: Int? = null,
        priority: TaskPriority? = null,
        track: String? = null,
    ) = ArrangementCandidate(
        title = title,
        category = TaskCategory.COURSE,
        durationMinutes = duration,
        timeHint = ArrangementTimeHint(explicitStartMinute = start),
        needsClarification = emptySet(),
        preferredTrackId = track,
        priority = priority,
    )

    private fun busy(
        title: String,
        start: Int,
        end: Int,
        kind: TimelineKind = TimelineKind.COURSE,
        track: String = kind.name.lowercase(),
    ) = TimelineEntry(
        id = title,
        title = title,
        kind = kind,
        date = day,
        startMinute = start,
        endMinute = end,
        trackId = track,
    )

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun `items without a stated time are packed back to back`() {
        val result = ChatDayPlanner.plan(
            candidates = listOf(candidate("A", 60), candidate("B", 60)),
            occupied = emptyList(),
        )

        assertEquals("both placed", 2, result.placements.size)
        assertEquals(at(8), result.placementFor(0)!!.startMinute)
        assertEquals(at(9, 5), result.placementFor(1)!!.startMinute)
        assertTrue("a chosen time is not the user's", !result.placementFor(0)!!.fromUser)
    }

    @Test
    fun `a higher priority claims the earlier slot`() {
        // Deliberately ordered so the low-priority item is seen first.
        val result = ChatDayPlanner.plan(
            candidates = listOf(
                candidate("low", 60, priority = TaskPriority.LOW),
                candidate("must", 60, priority = TaskPriority.REQUIRED),
            ),
            occupied = emptyList(),
        )

        assertEquals("REQUIRED takes 08:00", at(8), result.placementFor(1)!!.startMinute)
        assertEquals("LOW takes what is left", at(9, 5), result.placementFor(0)!!.startMinute)
    }

    @Test
    fun `a class blocks its window`() {
        val result = ChatDayPlanner.plan(
            candidates = listOf(candidate("study", 60)),
            occupied = listOf(busy("高数", at(8), at(10))),
        )

        assertEquals(at(10), result.placementFor(0)!!.startMinute)
    }

    @Test
    fun `a time the user stated survives a class rather than being moved or dropped`() {
        val result = ChatDayPlanner.plan(
            candidates = listOf(candidate("study", 60, start = at(15))),
            occupied = listOf(busy("物理", at(14), at(16))),
        )

        val placement = result.placementFor(0)
        assertEquals("the stated time is kept", at(15), placement?.startMinute)
        assertTrue("and it is attributed to the user", placement!!.fromUser)
    }

    @Test
    fun `two items pinned to the same time land on different lanes`() {
        val result = ChatDayPlanner.plan(
            candidates = listOf(
                candidate("study", 60, start = at(15)),
                candidate("call", 60, start = at(15)),
            ),
            occupied = emptyList(),
        )

        val first = result.placementFor(0)!!
        val second = result.placementFor(1)!!
        assertEquals(at(15), first.startMinute)
        assertEquals(at(15), second.startMinute)
        assertTrue("simultaneous work needs two lanes", first.trackId != second.trackId)
    }

    @Test
    fun `an item with no duration is reported instead of guessed`() {
        val result = ChatDayPlanner.plan(
            candidates = listOf(candidate("vague", duration = null)),
            occupied = emptyList(),
        )

        assertNull(result.placementFor(0))
        assertEquals(
            ChatDayPlanner.UnplacedReason.MISSING_DURATION,
            result.unplaced.single().reason,
        )
    }

    @Test
    fun `a day without room says so instead of overlapping work`() {
        val result = ChatDayPlanner.plan(
            candidates = listOf(candidate("A", 60), candidate("B", 60)),
            occupied = emptyList(),
            dayStartMinute = at(8),
            dayEndMinute = at(9),
        )

        assertEquals(at(8), result.placementFor(0)!!.startMinute)
        assertNull(result.placementFor(1))
        assertEquals(ChatDayPlanner.UnplacedReason.NO_ROOM, result.unplaced.single().reason)
    }

    @Test
    fun `an existing task only blocks its own lane`() {
        val result = ChatDayPlanner.plan(
            candidates = listOf(candidate("study", 60, start = at(15))),
            occupied = listOf(busy("另一件事", at(15), at(16), kind = TimelineKind.TASK, track = "focus")),
        )

        val placement = result.placementFor(0)!!
        assertEquals("the stated time is kept", at(15), placement.startMinute)
        assertEquals("the occupied lane is skipped", "parallel-2", placement.trackId)
    }
}
