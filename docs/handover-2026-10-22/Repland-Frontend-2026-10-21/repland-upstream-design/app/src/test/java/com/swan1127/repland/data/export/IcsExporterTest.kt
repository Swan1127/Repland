package com.swan1127.repland.data.export

import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.confirmedPlan
import com.swan1127.repland.domain.model.segment
import com.swan1127.repland.domain.model.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * [IcsExporter] is a minimal RFC 5545 writer.
 *
 * The single most important thing to pin here is the *absence* of a trailing `Z`: every time in
 * this app is a local wall-clock time, and declaring it UTC would shift every imported event by
 * the UTC offset — a bug that looks like a calendar problem but is a format problem.
 */
class IcsExporterTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 3, 2, 7, 0)
    private val tasks: List<Task> = listOf(task("t1"))

    private val mondaySegment = segment(
        id = "s1",
        taskId = "t1",
        startMinute = 9 * 60,
        endMinute = 10 * 60,
    )

    private fun build(
        segments: List<com.swan1127.repland.domain.model.PlannedSegment> = listOf(mondaySegment),
        tasks: List<Task> = this.tasks,
        date: java.time.LocalDate? = null,
    ): String = IcsExporter.build(
        plan = confirmedPlan(segments = segments),
        tasks = tasks,
        date = date,
        now = now,
    )

    private fun occurrences(text: String, needle: String): Int =
        text.split(needle).size - 1

    @Test
    fun `a plan-less export is still a well-formed empty calendar`() {
        val ics = IcsExporter.build(null, emptyList(), now = now)

        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertFalse(ics.contains("BEGIN:VEVENT"))
    }

    @Test
    fun `times are written as floating local time with no trailing Z`() {
        val ics = build()

        assertTrue(ics.contains("DTSTART:20260302T090000\r\n"))
        assertTrue(ics.contains("DTEND:20260302T100000\r\n"))
        assertTrue(ics.contains("DTSTAMP:20260302T070000\r\n"))
        // No "Z" anywhere: a UTC marker here is the classic off-by-one-hour import bug.
        assertFalse("the calendar must not declare UTC", ics.contains("Z"))
    }

    @Test
    fun `the task name becomes the summary and the span becomes the description`() {
        val ics = build()

        assertTrue(ics.contains("SUMMARY:Task t1\r\n"))
        assertTrue(ics.contains("DESCRIPTION:Repland 计划段落 · 60 分钟\r\n"))
    }

    @Test
    fun `semicolons, commas and newlines are escaped per RFC 5545`() {
        val messy = task("t1").copy(displayName = "读书; 笔记, 复习\n第二章")

        val ics = build(tasks = listOf(messy))

        assertTrue(ics.contains("SUMMARY:读书\\; 笔记\\, 复习\\n第二章\r\n"))
    }

    @Test
    fun `a backslash is escaped before the other escapes so it is not doubled`() {
        val messy = task("t1").copy(displayName = "a\\b")

        val ics = build(tasks = listOf(messy))

        assertTrue(ics.contains("SUMMARY:a\\\\b\r\n"))
    }

    @Test
    fun `a locked segment is tagged and an unlocked one is not`() {
        val locked = build(
            segments = listOf(
                segment("s1", taskId = "t1", startMinute = 9 * 60, endMinute = 10 * 60, isLocked = true),
            ),
        )
        val unlocked = build(
            segments = listOf(
                segment("s2", taskId = "t1", startMinute = 9 * 60, endMinute = 10 * 60, isLocked = false),
            ),
        )

        assertTrue(locked.contains("CATEGORIES:已锁定\r\n"))
        assertFalse(unlocked.contains("CATEGORIES:已锁定\r\n"))
    }

    @Test
    fun `the uid is stable across exports so a re-import updates instead of duplicating`() {
        val first = build()
        val later = IcsExporter.build(
            plan = confirmedPlan(segments = listOf(mondaySegment)),
            tasks = tasks,
            now = now.plusHours(5),
        )

        assertTrue(first.contains("UID:repland-s1@local\r\n"))
        assertTrue(later.contains("UID:repland-s1@local\r\n"))
        assertFalse("only the stamp should move", first == later)
    }

    @Test
    fun `a segment without an id still gets a deterministic uid`() {
        val ics = build(
            segments = listOf(segment("", taskId = "t1", startMinute = 9 * 60, endMinute = 10 * 60)),
        )

        assertTrue(ics.contains("UID:repland-t1-2026-03-02-540@local\r\n"))
    }

    @Test
    fun `a date filter exports only that day`() {
        val tuesday = segment(
            id = "s2",
            taskId = "t1",
            date = com.swan1127.repland.domain.model.TEST_MONDAY.plusDays(1),
            startMinute = 14 * 60,
            endMinute = 15 * 60,
        )

        val ics = build(segments = listOf(mondaySegment, tuesday), date = com.swan1127.repland.domain.model.TEST_MONDAY)

        assertEquals(1, occurrences(ics, "BEGIN:VEVENT"))
        assertTrue(ics.contains("DTSTART:20260302T090000\r\n"))
        assertFalse(ics.contains("DTSTART:20260303T140000\r\n"))
    }

    @Test
    fun `segments are exported in chronological order regardless of input order`() {
        val late = segment("s2", taskId = "t1", startMinute = 14 * 60, endMinute = 15 * 60)
        val early = segment("s3", taskId = "t1", startMinute = 9 * 60, endMinute = 10 * 60)

        val ics = build(segments = listOf(late, early))

        assertTrue(
            ics.indexOf("DTSTART:20260302T090000") < ics.indexOf("DTSTART:20260302T140000"),
        )
    }

    @Test
    fun `a segment whose task is unknown is labelled rather than dropped`() {
        val ics = build(
            segments = listOf(segment("s1", taskId = "ghost", startMinute = 9 * 60, endMinute = 10 * 60)),
            tasks = emptyList(),
        )

        assertTrue(ics.contains("SUMMARY:未命名任务\r\n"))
        assertEquals(1, occurrences(ics, "BEGIN:VEVENT"))
    }

    @Test
    fun `the file name reflects the export scope`() {
        assertEquals("repland-plan.ics", IcsExporter.fileName(null))
        assertEquals("repland-2026-03-02.ics", IcsExporter.fileName(com.swan1127.repland.domain.model.TEST_MONDAY))
    }
}
