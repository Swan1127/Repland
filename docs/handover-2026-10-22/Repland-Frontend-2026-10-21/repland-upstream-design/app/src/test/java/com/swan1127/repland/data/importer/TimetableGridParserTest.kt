package com.swan1127.repland.data.importer

import com.swan1127.repland.domain.model.CourseWeekPattern
import com.swan1127.repland.domain.model.ImportedCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * [TimetableGridParser] is the single recognition path for both the clipboard and the
 * .xlsx import. Its governing rule is "reject rather than guess": a wrong timetable
 * silently corrupts every later plan, which is worse than an error.
 */
class TimetableGridParserTest {

    private val header = listOf("节次", "星期一", "星期二", "星期三")

    @Test
    fun `tab separated text becomes trimmed rows and blank lines are dropped`() {
        val rows = TimetableGridParser.parseTabSeparated("a\tb\n\n  c \t d  \ne\n")

        assertEquals(listOf(listOf("a", "b"), listOf("c", "d"), listOf("e")), rows)
    }

    @Test
    fun `a weekday header plus a period column produces courses`() {
        val grid = listOf(
            header,
            listOf("1-2", "高等数学(1-16周)", "", "英语"),
            listOf("3-4", "", "实验", ""),
        )

        val courses = TimetableGridParser.parse(grid)

        assertEquals(3, courses.size)
        assertEquals(listOf("grid-1", "grid-2", "grid-3"), courses.map { it.id })

        val math = courses[0]
        assertEquals("高等数学", math.title)
        assertEquals(DayOfWeek.MONDAY, math.dayOfWeek)
        assertEquals(1, math.startPeriod)
        assertEquals(2, math.endPeriod)
        assertEquals("1-16周", math.weekPattern)

        val english = courses[1]
        assertEquals("英语", english.title)
        assertEquals(DayOfWeek.WEDNESDAY, english.dayOfWeek)
        assertEquals(1, english.startPeriod)
        assertNull(english.weekPattern)

        val lab = courses[2]
        assertEquals("实验", lab.title)
        assertEquals(DayOfWeek.TUESDAY, lab.dayOfWeek)
        assertEquals(3, lab.startPeriod)
        assertEquals(4, lab.endPeriod)
    }

    @Test
    fun `a grid without a weekday header is rejected rather than guessed`() {
        val grid = listOf(
            listOf("节次", "课程一", "课程二"),
            listOf("1", "数学", "英语"),
        )

        assertTrue(TimetableGridParser.parse(grid).isEmpty())
    }

    @Test
    fun `a header naming only two weekdays is rejected`() {
        val grid = listOf(
            listOf("节次", "星期一", "星期二"),
            listOf("1", "数学", "英语"),
        )

        assertTrue(TimetableGridParser.parse(grid).isEmpty())
    }

    @Test
    fun `the header is found even when it is not the first row`() {
        val grid = listOf(
            listOf("我的课表"),
            header,
            listOf("1", "数学", "", "英语"),
        )

        val courses = TimetableGridParser.parse(grid)

        assertEquals(listOf("数学", "英语"), courses.map { it.title })
    }

    @Test
    fun `a period outside 1 to 12 is rejected`() {
        val grid = listOf(
            header,
            listOf("13", "数学", "", ""),
            listOf("0", "英语", "", ""),
        )

        assertTrue(TimetableGridParser.parse(grid).isEmpty())
    }

    @Test
    fun `a row without a readable period is skipped`() {
        val grid = listOf(
            header,
            listOf("上午", "数学", "", ""),
            listOf("1", "英语", "", ""),
        )

        assertEquals(listOf("英语"), TimetableGridParser.parse(grid).map { it.title })
    }

    @Test
    fun `a single period becomes a one-period span`() {
        val grid = listOf(header, listOf("第3节", "数学", "", ""))

        val course = TimetableGridParser.parse(grid).single()

        assertEquals(3, course.startPeriod)
        assertEquals(3, course.endPeriod)
    }

    @Test
    fun `an inverted span falls back to the first number instead of the range`() {
        val grid = listOf(header, listOf("4-2", "数学", "", ""))

        val course = TimetableGridParser.parse(grid).single()

        // "4-2" fails the start<=end guard, so the single-number fallback reads "4".
        assertEquals(4, course.startPeriod)
        assertEquals(4, course.endPeriod)
    }

    @Test
    fun `sunday is recognised from its several common spellings`() {
        listOf("星期日", "星期天", "周日", "周天", "礼拜日").forEach { token ->
            val grid = listOf(
                listOf("节次", "星期一", "星期二", token),
                listOf("1", "", "", "自习"),
            )

            val course = TimetableGridParser.parse(grid).single()

            assertEquals("token $token", DayOfWeek.SUNDAY, course.dayOfWeek)
            assertEquals("token $token", "自习", course.title)
        }
    }

    @Test
    fun `a course name without a week pattern keeps the raw name`() {
        val grid = listOf(header, listOf("1", "体育", "", ""))

        val course = TimetableGridParser.parse(grid).single()

        assertEquals("体育", course.title)
        assertNull(course.weekPattern)
    }

    @Test
    fun `a stray row naming only two weekdays does not hijack the header`() {
        // The "two weekdays" rejection is guarded twice — once by the >= 3 header scan and
        // once by the dayColumns.size check. This grid is the case where only the header
        // scan saves us: a real 3-day header sits one row below a 2-day decoy row.
        val grid = listOf(
            listOf("星期一", "星期二", "备注"),
            header,
            listOf("1", "数学", "", "英语"),
        )

        val courses = TimetableGridParser.parse(grid)

        assertEquals(listOf("数学", "英语"), courses.map { it.title })
    }

    @Test
    fun `an english weekday header is recognised`() {
        val grid = listOf(
            listOf("Period", "Monday", "Tuesday", "Wednesday"),
            listOf("1-2", "Calculus", "", "English"),
        )

        val courses = TimetableGridParser.parse(grid)

        assertEquals(listOf("Calculus", "English"), courses.map { it.title })
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), courses.map { it.dayOfWeek })
        assertEquals(listOf(1, 1), courses.map { it.startPeriod })
        assertEquals(listOf(2, 2), courses.map { it.endPeriod })
        assertNull(courses[0].weekPattern)
    }

    @Test
    fun `an english week notation stays attached to the title, and the block stays active`() {
        // A known gap, pinned here so it cannot change unnoticed. WeekPatternFormat recognises
        // only the Chinese suffix (周), so "1-16 weeks" is never separated out of the cell: the
        // title keeps it and the block's weekPattern is null.
        //
        // That is a gap rather than a bug because of what a null pattern means downstream —
        // CourseWeekPattern returns true for it, so the block stays active on every week.
        // Over-inclusive beats a class that silently vanishes from the planner.
        //
        // The date is years past any 1-16 range, which is what makes this assertion mean
        // something: had the pattern been extracted, this would be false. Note that
        // CourseWeekPattern itself is notation-agnostic — its range regex is digit-based, so it
        // would read "1-16 weeks" correctly if it ever received it. Only the extractor is
        // Chinese-only, which is why closing this gap is a small change.
        val grid = listOf(
            listOf("Period", "Monday", "Tuesday", "Wednesday"),
            listOf("1-2", "Calculus (1-16 weeks)", "", ""),
        )

        val course = TimetableGridParser.parse(grid).single()

        assertEquals("Calculus (1-16 weeks)", course.title)
        assertNull(course.weekPattern)
        assertTrue(
            "a null week pattern must keep the block active",
            CourseWeekPattern.appliesOn(
                pattern = course.weekPattern,
                date = LocalDate.of(2030, 1, 7),
                firstWeekMonday = LocalDate.of(2026, 9, 7),
            ),
        )
    }

    @Test
    fun `english weekday abbreviations are recognised`() {
        listOf("Mon", "MON", "monday", "Monday").forEach { token ->
            val grid = listOf(
                listOf("Period", token, "Tue", "Wed"),
                listOf("1", "Maths", "", ""),
            )

            val course = TimetableGridParser.parse(grid).single()

            assertEquals("token $token", DayOfWeek.MONDAY, course.dayOfWeek)
            assertEquals("token $token", "Maths", course.title)
        }
    }

    @Test
    fun `a course name containing a weekday substring is not mistaken for a header`() {
        // The whole reason English tokens are matched on word boundaries: "Common", "Satisfy"
        // and "Thunder" each contain a three-letter weekday abbreviation. With a substring match
        // this decoy row would itself qualify as a header (three hits), and — because it sits
        // above the real one and indexOfFirst takes the first qualifying row — it would be chosen
        // over the real header.
        //
        // The grid is laid out so that mistake is *visible in the result* rather than merely
        // possible. The decoy maps its columns to MONDAY/SATURDAY/THURSDAY; the real header maps
        // the same columns to MONDAY/TUESDAY/WEDNESDAY. So asserting the weekdays — not just the
        // titles — is what makes this test able to fail. Asserting titles alone would pass either
        // way, because both readings put a course in the same three columns.
        val grid = listOf(
            listOf("1", "Common Law", "Satisfy the requirement", "Thunder and lightning"),
            listOf("Period", "Monday", "Tuesday", "Wednesday"),
            listOf("3", "Statistics", "Physics", "Chemistry"),
        )

        val courses = TimetableGridParser.parse(grid)

        assertEquals(listOf("Statistics", "Physics", "Chemistry"), courses.map { it.title })
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
            courses.map { it.dayOfWeek },
        )
        assertEquals(listOf(3, 3, 3), courses.map { it.startPeriod })
    }

    @Test
    fun `a mixed chinese and english header is recognised`() {
        val grid = listOf(
            listOf("节次", "星期一", "Tuesday", "星期三"),
            listOf("1", "数学", "物理", "化学"),
        )

        val courses = TimetableGridParser.parse(grid)

        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY), courses.map { it.dayOfWeek })
        assertEquals(listOf("数学", "物理", "化学"), courses.map { it.title })
    }

    @Test
    fun `an english grid is still rejected when it names fewer than three weekdays`() {
        val grid = listOf(
            listOf("Period", "Monday", "Tuesday"),
            listOf("1", "Maths", "Physics"),
        )

        assertEquals(emptyList<ImportedCourse>(), TimetableGridParser.parse(grid))
    }
}
