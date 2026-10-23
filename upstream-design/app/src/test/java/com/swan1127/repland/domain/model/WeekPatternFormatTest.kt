package com.swan1127.repland.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekPatternFormatTest {
    @Test
    fun `a full disjoint notation is separated without leaving ranges in the title`() {
        assertEquals(
            "示例课程A" to "1-2周,4-7周,10-18周",
            WeekPatternFormat.split("示例课程A(1-2周,4-7周,10-18周)"),
        )
    }

    @Test
    fun `odd and even suffixes remain part of the week pattern`() {
        listOf("3-9周(单)", "4-10周（双）", "1-16周单双").forEach { pattern ->
            assertEquals("示例课程B" to pattern, WeekPatternFormat.split("示例课程B($pattern)"))
        }
    }

    @Test
    fun `an isolated teaching week survives separation`() {
        assertEquals("示例课程C" to "10周", WeekPatternFormat.split("示例课程C(10周)"))
    }

    @Test
    fun `parentheses that belong to the course title are preserved`() {
        assertEquals("示例课程（高级）" to "3-14周", WeekPatternFormat.split("示例课程（高级）(3-14周)"))
        assertEquals("英语(高级)" to "3-14周", WeekPatternFormat.split("英语(高级)（3-14周）"))
    }

    @Test
    fun `separated parity and disjoint patterns keep the planners original meaning`() {
        val monday = LocalDate.of(2026, 8, 31)
        val odd = WeekPatternFormat.split("实验(3-9周(单))").second
        val disjoint = WeekPatternFormat.split("物理(1-2周,4-7周,10-18周)").second
        assertTrue(CourseWeekPattern.appliesOn(odd, monday.plusWeeks(2), monday))
        assertFalse(CourseWeekPattern.appliesOn(odd, monday.plusWeeks(3), monday))
        assertFalse(CourseWeekPattern.appliesOn(disjoint, monday.plusWeeks(2), monday))
        assertTrue(CourseWeekPattern.appliesOn(disjoint, monday.plusWeeks(3), monday))
    }

    @Test
    fun `legacy whitespace and tilde normalization remains stable`() {
        assertEquals("数学" to "1-16周", WeekPatternFormat.split("数学(1 ~ 16 周)"))
    }

    @Test
    fun `unbounded or invalid notation is left in the title instead of inferred`() {
        listOf("选修(单周)", "数学(0-2周)", "实验(9-3周)", "No week notation").forEach { title ->
            assertEquals(title to null, WeekPatternFormat.split(title))
        }
    }
}
