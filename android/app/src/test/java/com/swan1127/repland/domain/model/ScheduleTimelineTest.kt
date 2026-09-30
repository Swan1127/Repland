package com.swan1127.repland.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTimelineTest {
    private val day = LocalDate.of(2026, 9, 25)

    @Test fun clockDistinguishesPlanTimeFromRealCompletion() {
        val entry = TimelineEntry("one", "学习", TimelineKind.TASK, day, 9 * 60, 10 * 60)
        assertEquals(TimelinePhase.UPCOMING, ScheduleTimeline.clock(entry, day.atTime(8, 30)).phase)
        assertEquals(30, ScheduleTimeline.clock(entry, day.atTime(8, 30)).untilStartMinutes)
        assertEquals(TimelinePhase.ACTIVE, ScheduleTimeline.clock(entry, day.atTime(9, 20)).phase)
        assertEquals(20, ScheduleTimeline.clock(entry, day.atTime(9, 20)).elapsedMinutes)
        assertEquals(40, ScheduleTimeline.clock(entry, day.atTime(9, 20)).remainingMinutes)
        assertEquals(TimelinePhase.OVERRUN, ScheduleTimeline.clock(entry, day.atTime(10, 15)).phase)
        assertEquals(15, ScheduleTimeline.clock(entry, day.atTime(10, 15)).overtimeMinutes)
        assertEquals(TimelinePhase.ENDED, ScheduleTimeline.clock(entry, day.atTime(9, 20), userFinished = true).phase)
    }

    @Test fun overlappingCourseAndWorkUseSeparateLanes() {
        val course = WeeklyTimeBlock("course", "高数", TimeBlockKind.COURSE, DayOfWeek.FRIDAY,
            9 * 60, 10 * 60, null, 0, 0)
        val task = Task("task", "复习", "复习", TaskCategory.COURSE, TaskPriority.MEDIUM,
            1, 60, null, TaskStatus.NOT_STARTED, null, null, null, 0, 0, 0)
        val segment = PlannedSegment("segment", "task", day, 9 * 60 + 30, 10 * 60 + 30)
        val entries = ScheduleTimeline.entries(day, listOf(course), emptyList(), listOf(segment), listOf(task), null)
        assertEquals(2, entries.size)
        assertEquals(0, entries[0].lane)
        assertEquals(1, entries[1].lane)
    }

    @Test fun overlappingItemsOnTheSameNamedTrackAreAlsoSeparated() {
        val first = Task("one", "论文资料", "", TaskCategory.COURSE, TaskPriority.MEDIUM,
            1, 60, null, TaskStatus.NOT_STARTED, null, null, null, 0, 0, 0)
        val second = Task("two", "英语练习", "", TaskCategory.COURSE, TaskPriority.MEDIUM,
            1, 60, null, TaskStatus.NOT_STARTED, null, null, null, 0, 0, 0)
        val entries = ScheduleTimeline.entries(
            date = day,
            weeklyBlocks = emptyList(),
            dateOverrides = emptyList(),
            segments = listOf(
                PlannedSegment("first", "one", day, 9 * 60, 10 * 60, trackId = "focus"),
                PlannedSegment("second", "two", day, 9 * 60 + 15, 10 * 60 + 15, trackId = "focus"),
            ),
            tasks = listOf(first, second),
            semesterFirstWeekMonday = null,
        )
        assertEquals(2, entries.map(TimelineEntry::lane).distinct().size)
    }

    @Test fun offWeekCourseIsNotShown() {
        val course = WeeklyTimeBlock("course", "高数", TimeBlockKind.COURSE, DayOfWeek.FRIDAY,
            9 * 60, 10 * 60, "第1周", 0, 0)
        val entries = ScheduleTimeline.entries(day, listOf(course), emptyList(), emptyList(), emptyList(),
            LocalDate.of(2026, 9, 7))
        assertEquals(0, entries.size)
    }

    @Test fun closedTaskIsNotPresentedAsStillOverrunning() {
        val task = Task("task", "复习", "复习", TaskCategory.COURSE, TaskPriority.MEDIUM,
            1, 60, null, TaskStatus.COMPLETED, null, null, null, 0, 0, 0)
        val entry = ScheduleTimeline.entries(day, emptyList(), emptyList(),
            listOf(PlannedSegment("segment", "task", day, 9 * 60, 10 * 60)), listOf(task), null).single()
        assertEquals(true, entry.taskClosed)
        assertEquals(TimelinePhase.ENDED,
            ScheduleTimeline.clock(entry, day.atTime(10, 15), userFinished = entry.taskClosed).phase)
    }

    @Test fun timelineEntriesCarryUserNotesWithoutChangingCompletionState() {
        val course = WeeklyTimeBlock(
            id = "course",
            title = "高数",
            kind = TimeBlockKind.COURSE,
            dayOfWeek = DayOfWeek.FRIDAY,
            startMinute = 9 * 60,
            endMinute = 10 * 60,
            weekPattern = null,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
            note = "带习题册，A301",
        )
        val task = Task("task", "先整理错题，再做一套练习", "错题复盘", TaskCategory.COURSE,
            TaskPriority.MEDIUM, 1, 60, null, TaskStatus.NOT_STARTED, null, null, null, 0, 0, 0)
        val entries = ScheduleTimeline.entries(
            date = day,
            weeklyBlocks = listOf(course),
            dateOverrides = emptyList(),
            segments = listOf(PlannedSegment("segment", "task", day, 10 * 60, 11 * 60)),
            tasks = listOf(task),
            semesterFirstWeekMonday = null,
        )
        assertEquals("带习题册，A301", entries.first { it.id == "weekly:course" }.note)
        assertEquals("先整理错题，再做一套练习", entries.first { it.id == "segment:segment" }.note)
        assertEquals(false, entries.first { it.id == "segment:segment" }.taskClosed)
    }
}
