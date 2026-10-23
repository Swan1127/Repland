package com.swan1127.repland.domain.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ScheduleTimeline] turns stored blocks into what the day view draws. Two things here are
 * easy to get subtly wrong and hard to notice: which phase an entry is in at an exact
 * boundary, and which visual lane an entry lands in. A wrong lane stacks two classes on
 * top of each other; a wrong phase tells the user a class is over when it is starting.
 */
class ScheduleTimelineTest {

    private val entry = TimelineEntry(
        id = "e1",
        title = "高等数学",
        kind = TimelineKind.COURSE,
        date = TEST_MONDAY,
        startMinute = 480, // 08:00
        endMinute = 600, // 10:00
    )

    private fun at(hour: Int, minute: Int = 0): LocalDateTime = TEST_MONDAY.atTime(hour, minute)

    // ---------------------------------------------------------------- clock phases

    @Test
    fun `an entry before its start is upcoming and counts down to the start`() {
        val clock = ScheduleTimeline.clock(entry, at(7, 30))
        assertEquals(TimelinePhase.UPCOMING, clock.phase)
        assertEquals(30L, clock.untilStartMinutes)
        assertEquals(0L, clock.elapsedMinutes)
        assertEquals(0L, clock.remainingMinutes)
    }

    @Test
    fun `an entry exactly at its start is active with nothing elapsed`() {
        // The start boundary belongs to ACTIVE, not UPCOMING: `isBefore(start)` is false.
        val clock = ScheduleTimeline.clock(entry, at(8))
        assertEquals(TimelinePhase.ACTIVE, clock.phase)
        assertEquals(0L, clock.elapsedMinutes)
        assertEquals(120L, clock.remainingMinutes)
    }

    @Test
    fun `an entry in the middle is active with elapsed and remaining`() {
        val clock = ScheduleTimeline.clock(entry, at(9))
        assertEquals(TimelinePhase.ACTIVE, clock.phase)
        assertEquals(60L, clock.elapsedMinutes)
        assertEquals(60L, clock.remainingMinutes)
        assertEquals(0L, clock.overtimeMinutes)
    }

    @Test
    fun `an entry exactly at its end is overrun with zero overtime`() {
        // The end boundary belongs to OVERRUN: `isBefore(end)` is false. This is the
        // boundary most likely to be "fixed" to inclusive by mistake.
        val clock = ScheduleTimeline.clock(entry, at(10))
        assertEquals(TimelinePhase.OVERRUN, clock.phase)
        assertEquals(120L, clock.elapsedMinutes)
        assertEquals(0L, clock.overtimeMinutes)
    }

    @Test
    fun `an entry past its end reports overtime`() {
        val clock = ScheduleTimeline.clock(entry, at(10, 30))
        assertEquals(TimelinePhase.OVERRUN, clock.phase)
        assertEquals(150L, clock.elapsedMinutes)
        assertEquals(30L, clock.overtimeMinutes)
    }

    @Test
    fun `a user-finished entry is ended regardless of the wall clock`() {
        assertEquals(TimelinePhase.ENDED, ScheduleTimeline.clock(entry, at(9), userFinished = true).phase)
        // Even before it starts: completion is a user statement, not a time calculation.
        assertEquals(TimelinePhase.ENDED, ScheduleTimeline.clock(entry, at(7), userFinished = true).phase)
    }

    // ---------------------------------------------------------------- entry kinds

    @Test
    fun `an available block is not drawn on the timeline`() {
        val blocks = listOf(weeklyBlock(id = "free", kind = TimeBlockKind.AVAILABLE, startMinute = 480, endMinute = 600))
        assertTrue(entries(weeklyBlocks = blocks).isEmpty())
    }

    @Test
    fun `a course block keeps its own lane track`() {
        val blocks = listOf(
            weeklyBlock(id = "c1", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600, trackId = "math"),
        )
        val only = entries(weeklyBlocks = blocks).single()
        assertEquals(TimelineKind.COURSE, only.kind)
        assertEquals("math", only.trackId)
        assertEquals("weekly:c1", only.id)
    }

    @Test
    fun `a rest block is drawn on the rest track`() {
        val blocks = listOf(weeklyBlock(id = "r1", kind = TimeBlockKind.REST, startMinute = 720, endMinute = 780))
        val only = entries(weeklyBlocks = blocks).single()
        assertEquals(TimelineKind.REST, only.kind)
        assertEquals("rest", only.trackId)
    }

    @Test
    fun `an other block is drawn as a commitment on the fixed track`() {
        val blocks = listOf(weeklyBlock(id = "o1", kind = TimeBlockKind.OTHER, startMinute = 720, endMinute = 780))
        val only = entries(weeklyBlocks = blocks).single()
        assertEquals(TimelineKind.COMMITMENT, only.kind)
        assertEquals("fixed", only.trackId)
    }

    @Test
    fun `a block for another weekday is not drawn`() {
        val blocks = listOf(
            weeklyBlock(id = "tue", kind = TimeBlockKind.COURSE, dayOfWeek = DayOfWeek.TUESDAY, startMinute = 480, endMinute = 600),
        )
        assertTrue(entries(weeklyBlocks = blocks).isEmpty())
    }

    @Test
    fun `a week pattern that excludes the date hides the block`() {
        // Same weekday, one week later: the block is week 1 only.
        val blocks = listOf(
            weeklyBlock(
                id = "w1", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 600,
                weekPattern = "1",
            ),
        )
        assertEquals(1, entries(weeklyBlocks = blocks, semesterFirstWeekMonday = TEST_MONDAY).size)
        assertTrue(
            entries(
                date = TEST_MONDAY.plusWeeks(1),
                weeklyBlocks = blocks,
                semesterFirstWeekMonday = TEST_MONDAY,
            ).isEmpty(),
        )
    }

    // ---------------------------------------------------------------- overrides

    @Test
    fun `a blocked override is drawn as a commitment`() {
        val overrides = listOf(
            dateOverride(id = "ov1", type = DateOverrideType.BLOCKED, date = TEST_MONDAY, startMinute = 780, endMinute = 840),
        )
        val only = entries(dateOverrides = overrides).single()
        assertEquals(TimelineKind.COMMITMENT, only.kind)
        assertEquals("fixed", only.trackId)
        assertEquals("override:ov1", only.id)
    }

    @Test
    fun `an available override is not drawn`() {
        val overrides = listOf(
            dateOverride(id = "ov1", type = DateOverrideType.AVAILABLE, date = TEST_MONDAY, startMinute = 780, endMinute = 840),
        )
        assertTrue(entries(dateOverrides = overrides).isEmpty())
    }

    @Test
    fun `an override for another date is not drawn`() {
        val overrides = listOf(
            dateOverride(id = "ov1", type = DateOverrideType.BLOCKED, date = TEST_MONDAY.plusDays(1), startMinute = 780, endMinute = 840),
        )
        assertTrue(entries(dateOverrides = overrides).isEmpty())
    }

    // ---------------------------------------------------------------- planned segments

    @Test
    fun `a segment whose task no longer exists is dropped`() {
        val segments = listOf(segment(id = "s1", taskId = "ghost", startMinute = 480, endMinute = 540))
        assertTrue(entries(segments = segments, tasks = listOf(task("t1"))).isEmpty())
    }

    @Test
    fun `a segment becomes a task entry carrying its task title and open state`() {
        val segments = listOf(segment(id = "s1", taskId = "t1", startMinute = 480, endMinute = 540))
        val only = entries(segments = segments, tasks = listOf(task("t1"))).single()
        assertEquals(TimelineKind.TASK, only.kind)
        assertEquals("segment:s1", only.id)
        assertEquals("Task t1", only.title)
        assertEquals("t1", only.taskId)
        assertEquals(false, only.taskClosed)
    }

    @Test
    fun `a segment for a closed task is marked closed`() {
        val segments = listOf(segment(id = "s1", taskId = "t1", startMinute = 480, endMinute = 540))
        val only = entries(segments = segments, tasks = listOf(task("t1", status = TaskStatus.COMPLETED))).single()
        assertEquals(true, only.taskClosed)
    }

    @Test
    fun `a task description that differs from its name becomes the entry note`() {
        val segments = listOf(segment(id = "s1", taskId = "t1", startMinute = 480, endMinute = 540))
        val only = entries(segments = segments, tasks = listOf(task("t1"))).single()
        assertEquals("description of t1", only.note)
    }

    @Test
    fun `a task description equal to its name produces no note`() {
        val repeated = task("t1").copy(description = "Task t1")
        val segments = listOf(segment(id = "s1", taskId = "t1", startMinute = 480, endMinute = 540))
        assertNull(entries(segments = segments, tasks = listOf(repeated)).single().note)
    }

    @Test
    fun `a segment for another date is not drawn`() {
        val segments = listOf(
            segment(id = "s1", taskId = "t1", date = TEST_MONDAY.plusDays(1), startMinute = 480, endMinute = 540),
        )
        assertTrue(entries(segments = segments, tasks = listOf(task("t1"))).isEmpty())
    }

    // ---------------------------------------------------------------- ordering and lanes

    @Test
    fun `entries are ordered by start time`() {
        val blocks = listOf(
            weeklyBlock(id = "late", kind = TimeBlockKind.COURSE, startMinute = 780, endMinute = 840),
            weeklyBlock(id = "early", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 540),
        )
        assertEquals(listOf("weekly:early", "weekly:late"), entries(weeklyBlocks = blocks).map(TimelineEntry::id))
    }

    @Test
    fun `two overlapping blocks on one track get different lanes`() {
        val blocks = listOf(
            weeklyBlock(id = "b1", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 540, trackId = "course"),
            weeklyBlock(id = "b2", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 540, trackId = "course"),
        )
        assertEquals(listOf(0, 1), entries(weeklyBlocks = blocks).map(TimelineEntry::lane))
    }

    @Test
    fun `two sequential blocks on one track share a lane`() {
        val blocks = listOf(
            weeklyBlock(id = "b1", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 540, trackId = "course"),
            weeklyBlock(id = "b2", kind = TimeBlockKind.COURSE, startMinute = 600, endMinute = 660, trackId = "course"),
        )
        assertEquals(listOf(0, 0), entries(weeklyBlocks = blocks).map(TimelineEntry::lane))
    }

    @Test
    fun `blocks on different tracks get different lanes even when they do not overlap`() {
        // The lane index follows the first appearance of a track, so a new track never
        // silently reuses the lane of an earlier one.
        val blocks = listOf(
            weeklyBlock(id = "b1", kind = TimeBlockKind.COURSE, startMinute = 480, endMinute = 540, trackId = "course"),
            weeklyBlock(id = "b2", kind = TimeBlockKind.COURSE, startMinute = 600, endMinute = 660, trackId = "lab"),
        )
        val byId = entries(weeklyBlocks = blocks).associateBy(TimelineEntry::id)
        assertEquals(0, byId.getValue("weekly:b1").lane)
        assertEquals(1, byId.getValue("weekly:b2").lane)
    }

    // ---------------------------------------------------------------- block progress

    @Test
    fun `a block that has not started has no elapsed fill`() {
        val clock = ScheduleTimeline.clock(entry, at(7, 30))
        assertEquals(0f, TimelineBlockProgress.elapsedFraction(entry, clock), 1e-6f)
    }

    @Test
    fun `a block exactly at its start has no elapsed fill`() {
        val clock = ScheduleTimeline.clock(entry, at(8))
        assertEquals(0f, TimelineBlockProgress.elapsedFraction(entry, clock), 1e-6f)
    }

    @Test
    fun `halfway through a two hour block is half filled`() {
        val clock = ScheduleTimeline.clock(entry, at(9))
        assertEquals(0.5f, TimelineBlockProgress.elapsedFraction(entry, clock), 1e-6f)
    }

    @Test
    fun `one minute before the end is nearly full but not full`() {
        // The distinction matters: a block that reads as finished while it is still running
        // would contradict the "还剩 1 分钟" line printed on the same card.
        val clock = ScheduleTimeline.clock(entry, at(9, 59))
        assertEquals(119f / 120f, TimelineBlockProgress.elapsedFraction(entry, clock), 1e-6f)
    }

    @Test
    fun `an overrun block is full and never exceeds full`() {
        val clock = ScheduleTimeline.clock(entry, at(23))
        assertEquals(1f, TimelineBlockProgress.elapsedFraction(entry, clock), 1e-6f)
    }

    @Test
    fun `a block the user finished is full even before its scheduled end`() {
        val clock = ScheduleTimeline.clock(entry, at(8, 10), userFinished = true)
        assertEquals(1f, TimelineBlockProgress.elapsedFraction(entry, clock), 1e-6f)
    }

    @Test
    fun `a one minute block is a usable denominator`() {
        // `TimelineEntry` forbids end <= start, so the shortest real block is one minute.
        // Dividing by it must still produce 0 and 1 rather than an infinity or a NaN.
        val tiny = entry.copy(startMinute = 600, endMinute = 601)
        assertEquals(0f, TimelineBlockProgress.elapsedFraction(tiny, ScheduleTimeline.clock(tiny, at(10, 0))), 1e-6f)
        assertEquals(1f, TimelineBlockProgress.elapsedFraction(tiny, ScheduleTimeline.clock(tiny, at(10, 1))), 1e-6f)
    }

    // ---------------------------------------------------------------- helper

    private fun entries(
        date: java.time.LocalDate = TEST_MONDAY,
        weeklyBlocks: List<WeeklyTimeBlock> = emptyList(),
        dateOverrides: List<DateOverride> = emptyList(),
        segments: List<PlannedSegment> = emptyList(),
        tasks: List<Task> = emptyList(),
        semesterFirstWeekMonday: java.time.LocalDate? = null,
    ): List<TimelineEntry> = ScheduleTimeline.entries(
        date = date,
        weeklyBlocks = weeklyBlocks,
        dateOverrides = dateOverrides,
        segments = segments,
        tasks = tasks,
        semesterFirstWeekMonday = semesterFirstWeekMonday,
    )
}
