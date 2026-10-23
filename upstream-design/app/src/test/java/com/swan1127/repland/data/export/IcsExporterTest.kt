package com.swan1127.repland.data.export

import com.swan1127.repland.data.importer.IcsImporter
import com.swan1127.repland.data.importer.IcsTiming
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.confirmedPlan
import com.swan1127.repland.domain.model.segment
import com.swan1127.repland.domain.model.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * [IcsExporter] is a minimal RFC 5545 writer.
 *
 * DTSTART/DTEND remain floating, while DTSTAMP must carry the true UTC instant. UTF-8 folding
 * is checked independently of round-trip parsing so the reader cannot hide invalid long lines.
 */
class IcsExporterTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 3, 2, 7, 0)
    private val zone = ZoneId.of("Asia/Shanghai")
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
        zoneId = zone,
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
    fun `event times stay floating while DTSTAMP converts the local instant to UTC`() {
        val ics = build()

        assertTrue(ics.contains("DTSTART:20260302T090000\r\n"))
        assertTrue(ics.contains("DTEND:20260302T100000\r\n"))
        assertTrue(ics.contains("DTSTAMP:20260301T230000Z\r\n"))
        val eventTimes = ics.split("\r\n").filter { it.startsWith("DTSTART:") || it.startsWith("DTEND:") }
        assertEquals(2, eventTimes.size)
        assertTrue("event wall times must not be relabelled UTC", eventTimes.none { it.endsWith("Z") })
    }

    @Test
    fun `DTSTAMP conversion honours the supplied zone and its daylight saving offset`() {
        val ics = IcsExporter.build(
            plan = confirmedPlan(segments = listOf(mondaySegment)),
            tasks = tasks,
            now = LocalDateTime.of(2026, 7, 1, 12, 0),
            zoneId = ZoneId.of("America/New_York"),
        )

        assertTrue(ics.contains("DTSTAMP:20260701T160000Z\r\n"))
        assertTrue(ics.contains("DTSTART:20260302T090000\r\n"))
    }

    @Test
    fun `a 75 octet line stays intact and a 76 octet line folds with a continuation space`() {
        val exact = build(tasks = listOf(task("t1").copy(displayName = "a".repeat(67))))
        val over = build(tasks = listOf(task("t1").copy(displayName = "a".repeat(68))))

        assertTrue(exact.contains("SUMMARY:${"a".repeat(67)}\r\n"))
        assertTrue(over.contains("SUMMARY:${"a".repeat(67)}\r\n a\r\n"))
        assertTrue(over.split("\r\n").all { it.toByteArray(Charsets.UTF_8).size <= 75 })
    }

    @Test
    fun `long Chinese titles and UIDs fold within 75 UTF8 octets and round trip without splitting characters`() {
        val title = "复习《计算机组成原理》📘：数据通路、缓存;问题,笔记\\原文\n下一段".repeat(12)
        val id = "segment-${"课程📘".repeat(30)}"
        val ics = build(segments = listOf(mondaySegment.copy(id = id)), tasks = listOf(task("t1").copy(displayName = title)))
        val physicalLines = ics.split("\r\n")

        assertTrue("long UTF-8 properties must be folded", ics.contains("\r\n "))
        assertTrue("continuation whitespace counts towards the octet limit",
            physicalLines.all { it.toByteArray(Charsets.UTF_8).size <= 75 })
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertFalse("folding must not insert empty content lines", ics.contains("\r\n\r\n"))
        val written = ics.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)
        assertEquals("surrogate pairs must survive actual UTF-8 encoding", ics, written)
        val imported = IcsImporter.parse(written, zone).single()
        assertEquals(title, imported.summary)
        assertEquals("repland-$id@local", imported.uid)
        assertTrue(imported.fromRepland)
        val timing = imported.timing as IcsTiming.Timed
        assertEquals(mondaySegment.date.atTime(9, 0), timing.start)
        assertEquals(mondaySegment.date.atTime(10, 0), timing.end)
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
            zoneId = zone,
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
