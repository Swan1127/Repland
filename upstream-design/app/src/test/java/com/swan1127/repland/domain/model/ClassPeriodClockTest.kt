package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

/**
 * [ClassPeriodClock] maps the "第 N 节" notation that Chinese timetables use onto clock
 * time. It is the bridge between an imported grid and a real weekly block.
 */
class ClassPeriodClockTest {

    private fun course(
        startPeriod: Int,
        endPeriod: Int,
        dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        title: String = "高等数学",
        weekPattern: String? = null,
    ): ImportedCourse = ImportedCourse(
        id = "c1",
        title = title,
        dayOfWeek = dayOfWeek,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        weekPattern = weekPattern,
    )

    @Test
    fun `period 1 runs 08_00 to 08_45`() {
        val draft = ClassPeriodClock.toWeeklyTimeBlockDraft(course(1, 1))!!

        assertEquals(8 * 60, draft.startMinute)
        assertEquals(8 * 60 + 45, draft.endMinute)
    }

    @Test
    fun `a multi-period course spans from the first start to the last end`() {
        val draft = ClassPeriodClock.toWeeklyTimeBlockDraft(course(3, 4))!!

        assertEquals(10 * 60, draft.startMinute)
        assertEquals(11 * 60 + 35, draft.endMinute)
    }

    @Test
    fun `period 12 is the last supported period`() {
        val draft = ClassPeriodClock.toWeeklyTimeBlockDraft(course(12, 12))!!

        assertEquals(21 * 60 + 50, draft.startMinute)
        assertEquals(22 * 60 + 35, draft.endMinute)
    }

    @Test
    fun `the imported course keeps its title, day and week pattern`() {
        val draft = ClassPeriodClock.toWeeklyTimeBlockDraft(
            course(
                startPeriod = 5,
                endPeriod = 6,
                dayOfWeek = DayOfWeek.THURSDAY,
                title = "大学物理",
                weekPattern = "1-8周",
            ),
        )!!

        assertEquals("大学物理", draft.title)
        assertEquals(DayOfWeek.THURSDAY, draft.dayOfWeek)
        assertEquals(TimeBlockKind.COURSE, draft.kind)
        assertEquals("1-8周", draft.weekPattern)
    }

    @Test
    fun `an unknown period is rejected instead of guessed`() {
        assertNull(ClassPeriodClock.toWeeklyTimeBlockDraft(course(0, 1)))
        assertNull(ClassPeriodClock.toWeeklyTimeBlockDraft(course(13, 13)))
        assertNull(ClassPeriodClock.toWeeklyTimeBlockDraft(course(1, 13)))
    }

    @Test
    fun `an inverted period span is rejected`() {
        assertNull(ClassPeriodClock.toWeeklyTimeBlockDraft(course(5, 2)))
    }
}
