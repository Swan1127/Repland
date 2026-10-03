package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TodayFocusTest {
    private val date = LocalDate.of(2026, 10, 3)
    private val now = date.atTime(10, 0)
    private fun entry(id: String = "s", start: Int = 600, end: Int = 630, closed: Boolean = false) =
        TimelineEntry("segment:$id", id, TimelineKind.TASK, date, start, end, "task", closed)

    @Test fun selects_current_before_future_and_ignores_closed_or_ended() {
        val current = entry()
        assertEquals(current, TodayFocus.next(listOf(entry("future", 720, 750), entry("past", 540, 600), entry("closed", 590, 640, true), current), now))
        assertNull(TodayFocus.next(listOf(entry("past", 540, 600)), now))
    }
    @Test fun only_real_active_task_segments_can_start() {
        assertEquals("s", TodayFocus.startSegment(entry()))
        assertNull(TodayFocus.startSegment(entry(closed = true)))
        assertNull(TodayFocus.startSegment(entry().copy(kind = TimelineKind.COURSE, taskId = null)))
        assertNull(TodayFocus.startSegment(entry().copy(id = "weekly:s")))
        assertNull(TodayFocus.startSegment(null))
    }
    @Test fun pending_counts_tasks_not_slots_and_excludes_execution_and_closed() {
        val slots = listOf(entry("past1", 500, 530), entry("past2", 540, 600), entry("future"), entry("closed", 400, 430, true))
        assertEquals(1, TodayFocus.pending(slots, now, null).size)
        assertTrue(TodayFocus.pending(slots, now, "task").isEmpty())
        assertEquals(false, slots.first().taskClosed)
    }
    @Test fun future_day_is_upcoming_even_when_its_hour_is_earlier() {
        val tomorrow = entry(start = 480, end = 510).copy(date = date.plusDays(1))
        assertEquals(tomorrow, TodayFocus.next(listOf(tomorrow), now))
        assertTrue(TodayFocus.pending(listOf(tomorrow), now, null).isEmpty())
    }
}
