package com.swan1127.repland.data.export

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The exported PNG is drawn on a `Canvas`, and `Bitmap.createBitmap` is an Android stub that throws
 * under a plain JVM test — so the *drawing* cannot be covered here and this class does not pretend
 * otherwise.
 *
 * What it covers is every decision that used to carry a hard-coded language: which words the header
 * date is printed in, which words a weekday heading is printed in, and what the subtitle says. Those
 * four lines were the defect; they are now plain functions in `PlanImageText.kt`, which is why they
 * are reachable at all.
 *
 * Both languages are asserted side by side, so a regression to a single hard-coded locale fails on
 * one of the two rather than passing quietly.
 *
 * The `\$` in the fixtures below is **not** a typo: `%1$d` inside a Kotlin string would be read as a
 * template referring to a variable named `d`, so the dollar has to be escaped to survive as a
 * literal — which is exactly what `String.format` needs to see.
 */
class PlanImageExporterTest {

    private val chinese = PlanImageTexts(
        datePattern = "M月d日 EEEE",
        locale = Locale.CHINA,
        weekTitle = "每周课表",
        emptyPlan = "今天没有具体安排",
        emptyTimetable = "还没有固定时段",
        segmentCount = "共 %1\$d 段安排",
        blockCount = "共 %1\$d 段固定时段",
        missingTask = "已删除的任务",
    )

    private val english = PlanImageTexts(
        datePattern = "EEEE, MMM d",
        locale = Locale.US,
        weekTitle = "Weekly timetable",
        emptyPlan = "Nothing scheduled today",
        emptyTimetable = "No fixed blocks yet",
        segmentCount = "%1\$d scheduled blocks",
        blockCount = "%1\$d fixed blocks",
        missingTask = "Deleted task",
    )

    // ------------------------------------------------------------ the date

    @Test
    fun `the header date is printed in the image's language`() {
        val wednesday = LocalDate.of(2026, 10, 7)
        assertEquals("10月7日 星期三", planHeaderDate(wednesday, chinese))
        assertEquals("Wednesday, Oct 7", planHeaderDate(wednesday, english))
    }

    @Test
    fun `the header date ignores the machine's default locale`() {
        // The old code passed Locale.CHINA explicitly, so the machine's locale was irrelevant —
        // and so was the user's. This pins the opposite: the locale that decides is the one in
        // the texts, not whatever the JVM happens to be set to.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.CHINA)
            assertEquals("Wednesday, Oct 7", planHeaderDate(LocalDate.of(2026, 10, 7), english))
            Locale.setDefault(Locale.US)
            assertEquals("10月7日 星期三", planHeaderDate(LocalDate.of(2026, 10, 7), chinese))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `the two languages do not produce the same header`() {
        // Cheap but load-bearing: if the pattern were still the Chinese literal, the two calls
        // would agree and every other assertion here could be satisfied by accident.
        val date = LocalDate.of(2026, 10, 7)
        assertNotEquals(planHeaderDate(date, chinese), planHeaderDate(date, english))
    }

    // ------------------------------------------------------------ the weekday headings

    @Test
    fun `weekday headings are printed in the image's language`() {
        assertEquals("星期一", weekdayName(DayOfWeek.MONDAY, chinese))
        assertEquals("Monday", weekdayName(DayOfWeek.MONDAY, english))
    }

    @Test
    fun `every weekday has a heading in both languages`() {
        // Covers the whole week so a locale that silently falls back to the ISO name ("MONDAY")
        // on some day of the week cannot slip through.
        DayOfWeek.entries.forEach { day ->
            val titleCase = day.name.lowercase().replaceFirstChar { it.uppercase() }
            assertEquals(titleCase, weekdayName(day, english))
        }
        assertEquals("星期日", weekdayName(DayOfWeek.SUNDAY, chinese))
    }

    // ------------------------------------------------------------ the subtitles

    @Test
    fun `an empty plan says so instead of counting zero`() {
        assertEquals("今天没有具体安排", planSubtitle(0, chinese))
        assertEquals("Nothing scheduled today", planSubtitle(0, english))
    }

    @Test
    fun `a plan with segments carries the count`() {
        assertEquals("共 3 段安排", planSubtitle(3, chinese))
        assertEquals("3 scheduled blocks", planSubtitle(3, english))
    }

    @Test
    fun `an empty timetable says so instead of counting zero`() {
        assertEquals("还没有固定时段", timetableSubtitle(0, chinese))
        assertEquals("No fixed blocks yet", timetableSubtitle(0, english))
    }

    @Test
    fun `a timetable with blocks carries the count`() {
        assertEquals("共 2 段固定时段", timetableSubtitle(2, chinese))
        assertEquals("2 fixed blocks", timetableSubtitle(2, english))
    }

    @Test
    fun `neither subtitle leaves a format placeholder behind`() {
        listOf(0, 1, 12).forEach { count ->
            assertFalse(planSubtitle(count, english).contains('%'))
            assertFalse(timetableSubtitle(count, english).contains('%'))
            assertFalse(planSubtitle(count, chinese).contains('%'))
            assertFalse(timetableSubtitle(count, chinese).contains('%'))
        }
    }
}
