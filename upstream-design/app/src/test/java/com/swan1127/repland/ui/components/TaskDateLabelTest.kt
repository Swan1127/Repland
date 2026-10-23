package com.swan1127.repland.ui.components

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [taskDateLabel] is the only place a task's day becomes words. It is pure and takes `today` as a
 * parameter, so every case below is driven with an explicit today — the label must never depend on
 * when the test happens to run.
 *
 * The interesting part is not "今天"/"明天"; it is the fallback formatting, where the year is
 * included only when it differs from today's. An off-by-one there shows the wrong day.
 *
 * **The words and patterns are written out here rather than read from `strings.xml`.** That is
 * deliberate: a test that loaded the resource file would still pass if the Chinese file happened to
 * contain the English pattern — which is exactly the defect this class exists to catch. The file
 * itself is covered separately by [com.swan1127.repland.i18n.I18nResourceTest], which checks that
 * `values/` and `values-en/` agree key for key.
 */
class TaskDateLabelTest {

    private val today = LocalDate.of(2026, 3, 2)

    private val chinese = TaskDateLabels(
        today = "今天",
        tomorrow = "明天",
        monthDay = "M月d日",
        yearMonthDay = "yyyy年M月d日",
    )

    private val english = TaskDateLabels(
        today = "Today",
        tomorrow = "Tomorrow",
        monthDay = "MMM d",
        yearMonthDay = "MMM d, yyyy",
    )

    private fun zh(date: LocalDate, reference: LocalDate = today): String =
        taskDateLabel(date, reference, chinese, Locale.CHINA)

    private fun en(date: LocalDate, reference: LocalDate = today): String =
        taskDateLabel(date, reference, english, Locale.US)

    @Test
    fun `today is labelled today`() {
        assertEquals("今天", zh(today))
    }

    @Test
    fun `tomorrow is labelled tomorrow`() {
        assertEquals("明天", zh(today.plusDays(1)))
    }

    @Test
    fun `the day after tomorrow falls back to the explicit date`() {
        assertEquals("3月4日", zh(today.plusDays(2)))
    }

    @Test
    fun `yesterday falls back to the explicit date because there is no yesterday label`() {
        // Pinned as a decision rather than an oversight: only today and tomorrow get words,
        // so a past date always reads as an explicit day.
        assertEquals("3月1日", zh(today.minusDays(1)))
    }

    @Test
    fun `the label is driven by the passed today not the wall clock`() {
        // A fixed far-past "today" must still produce 今天 for its own day, proving the
        // default argument is not being consulted.
        val longAgo = LocalDate.of(1999, 12, 31)
        assertEquals("今天", zh(longAgo, longAgo))
        assertEquals("明天", zh(LocalDate.of(2000, 1, 1), longAgo))
    }

    @Test
    fun `tomorrow across a month boundary is still tomorrow`() {
        val endOfMonth = LocalDate.of(2026, 3, 31)
        assertEquals("明天", zh(LocalDate.of(2026, 4, 1), endOfMonth))
    }

    @Test
    fun `tomorrow across a year boundary is still tomorrow`() {
        val endOfYear = LocalDate.of(2026, 12, 31)
        assertEquals("明天", zh(LocalDate.of(2027, 1, 1), endOfYear))
    }

    @Test
    fun `a leap day is tomorrow when the day before is February twenty-eighth`() {
        assertEquals("明天", zh(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 2, 28)))
    }

    // ------------------------------------------------------------ the fallback format

    @Test
    fun `a same-year date omits the year`() {
        assertEquals("4月15日", zh(LocalDate.of(2026, 4, 15)))
    }

    @Test
    fun `a different-year date includes the year`() {
        assertEquals("2027年1月5日", zh(LocalDate.of(2027, 1, 5)))
    }

    @Test
    fun `the year is included exactly when it differs from today's year`() {
        // December of this year omits the year; January of the next includes it, even though
        // the two are only weeks apart. Pinned so nobody "fixes" it into a proximity rule.
        assertEquals("12月20日", zh(LocalDate.of(2026, 12, 20)))
        assertEquals("2027年1月3日", zh(LocalDate.of(2027, 1, 3)))
    }

    @Test
    fun `a past year includes the year too`() {
        assertEquals("2025年3月2日", zh(LocalDate.of(2025, 3, 2)))
    }

    @Test
    fun `the day and month are not zero padded`() {
        assertEquals("3月4日", zh(LocalDate.of(2026, 3, 4)))
        assertEquals("2027年1月9日", zh(LocalDate.of(2027, 1, 9)))
    }

    @Test
    fun `the same day number in another month is not labelled today`() {
        // Guards against a comparison that only looks at dayOfMonth.
        assertNotEquals("今天", zh(LocalDate.of(2026, 4, 2)))
        assertEquals("4月2日", zh(LocalDate.of(2026, 4, 2)))
    }

    @Test
    fun `the same day and month in another year is not labelled today`() {
        assertNotEquals("今天", zh(LocalDate.of(2027, 3, 2)))
        assertEquals("2027年3月2日", zh(LocalDate.of(2027, 3, 2)))
    }

    // ------------------------------------------------------------ the language

    @Test
    fun `the same day reads differently in each language`() {
        // The defect this guards: the label used to be built from a pattern with 月/日 written into
        // it and a hard-coded Chinese locale, so an English build still printed 3月4日. Both halves
        // are exercised here — the words, and the pattern.
        val date = LocalDate.of(2026, 3, 4)
        assertEquals("3月4日", zh(date))
        assertEquals("Mar 4", en(date))
    }

    @Test
    fun `the relative words come from the injected labels`() {
        assertEquals("Today", en(today))
        assertEquals("Tomorrow", en(today.plusDays(1)))
    }

    @Test
    fun `a date in another year uses the year pattern of the language`() {
        assertEquals("2027年1月5日", zh(LocalDate.of(2027, 1, 5)))
        assertEquals("Jan 5, 2027", en(LocalDate.of(2027, 1, 5)))
    }

    @Test
    fun `an empty label set yields empty strings rather than falling back to Chinese`() {
        // Proves nothing is hard-coded behind the parameters: if the words were baked in, the
        // relative branches would still produce 今天/明天 here.
        val blank = TaskDateLabels(today = "", tomorrow = "", monthDay = "", yearMonthDay = "")
        assertEquals("", taskDateLabel(today, today, blank, Locale.US))
        assertEquals("", taskDateLabel(today.plusDays(1), today, blank, Locale.US))
    }
}
