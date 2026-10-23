package com.swan1127.repland.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CourseWeekPattern] parses the week notation Chinese university timetables use.
 *
 * The governing rule is fail-safe: an unfamiliar notation must stay ACTIVE, because a
 * mis-parsed pattern would make a real class silently disappear from the planner.
 */
class CourseWeekPatternTest {

    private val firstWeekMonday = TEST_MONDAY

    private fun applies(pattern: String?, week: Int, firstWeek: java.time.LocalDate? = firstWeekMonday): Boolean =
        CourseWeekPattern.appliesOn(pattern, firstWeekMonday.plusWeeks((week - 1).toLong()), firstWeek)

    @Test
    fun `a blank pattern always applies`() {
        assertTrue(applies(null, week = 1))
        assertTrue(applies("   ", week = 12))
    }

    @Test
    fun `without a semester start every pattern applies`() {
        assertTrue(applies("1-16周", week = 1, firstWeek = null))
        assertTrue(applies("1-16周", week = 30, firstWeek = null))
    }

    @Test
    fun `a week range limits the block to those weeks`() {
        assertTrue(applies("1-16周", week = 1))
        assertTrue(applies("1-16周", week = 16))
        assertFalse(applies("1-16周", week = 17))
    }

    @Test
    fun `an odd-week marker skips the even weeks`() {
        assertTrue(applies("1-16周(单)", week = 1))
        assertFalse(applies("1-16周(单)", week = 2))
        assertTrue(applies("1-16周(单)", week = 3))
    }

    @Test
    fun `an even-week marker skips the odd weeks`() {
        assertFalse(applies("1-16周(双)", week = 1))
        assertTrue(applies("1-16周(双)", week = 2))
    }

    @Test
    fun `the combined odd-and-even form keeps both parities`() {
        assertTrue(applies("1-16周单双", week = 1))
        assertTrue(applies("1-16周单双", week = 2))
        assertFalse(applies("1-16周单双", week = 17))
    }

    @Test
    fun `comma-separated ranges are both honoured`() {
        assertTrue(applies("1-8周,10-16周", week = 5))
        assertFalse(applies("1-8周,10-16周", week = 9))
        assertTrue(applies("1-8周,10-16周", week = 10))
    }

    @Test
    fun `a full-width parenthesis and an en dash are understood`() {
        assertTrue(applies("第1～16周（单）", week = 1))
        assertFalse(applies("第1～16周（单）", week = 2))
    }

    @Test
    fun `a date before the first week never applies`() {
        assertFalse(
            CourseWeekPattern.appliesOn(
                "1-16周",
                firstWeekMonday.minusWeeks(1),
                firstWeekMonday,
            ),
        )
    }

    @Test
    fun `an unparseable pattern stays active so a class never silently disappears`() {
        assertTrue(applies("待定", week = 1))
        assertTrue(applies("abc", week = 40))
        assertTrue(applies("单双", week = 40))
    }

    // ---------------------------------------------------------------- two notations at once

    /**
     * [CourseWeekPattern.weeksMayOverlap] answers a different question from `appliesOn`: not "is
     * this pattern on in week N" but "can these two patterns ever be on together". The timetable
     * conflict rule reads it to tell a genuine clash from two classes that share an hour on
     * alternating weeks — which is a normal timetable, and the case a naive rule gets wrong.
     */
    private fun overlap(first: String?, second: String?) =
        CourseWeekPattern.weeksMayOverlap(first, second)

    @Test
    fun `a blank notation overlaps everything`() {
        assertTrue(overlap(null, "1-16周"))
        assertTrue(overlap("1-16周", null))
        assertTrue(overlap("  ", "1-16周"))
    }

    @Test
    fun `an unparseable notation overlaps everything`() {
        assertTrue(overlap("待定", "1-16周"))
        assertTrue(overlap("1-16周", "abc"))
    }

    @Test
    fun `two identical ranges overlap`() {
        assertTrue(overlap("1-16周", "1-16周"))
    }

    @Test
    fun `two disjoint ranges do not overlap`() {
        assertFalse(overlap("1-8周", "9-16周"))
    }

    @Test
    fun `ranges sharing a single week overlap`() {
        assertTrue(overlap("1-8周", "8-16周"))
    }

    @Test
    fun `an odd-week class and an even-week class over the same range never meet`() {
        assertFalse(overlap("1-16周(单)", "1-16周(双)"))
    }

    @Test
    fun `an odd-week class meets a class that runs every week`() {
        assertTrue(overlap("1-16周(单)", "1-16周"))
    }

    @Test
    fun `two odd-week classes meet when their ranges share an odd week`() {
        assertTrue(overlap("1-16周(单)", "3-16周(单)"))
    }

    @Test
    fun `two odd-week classes do not meet when only an even week is shared`() {
        // Both ranges cover week 2 and nothing else; week 2 is even, so neither is ever on.
        assertFalse(overlap("2-2周(单)", "2-2周(单)"))
    }

    @Test
    fun `a two-week window still cannot make an odd and an even class meet`() {
        // The tempting mistake, pinned. The overlap covers week 1 (odd) and week 2 (even), so a rule
        // that reasoned about "adjacent weeks" would say yes — but the odd-week class is on in week 1
        // and the even-week one in week 2, and never in the same week.
        assertFalse(overlap("1-2周(单)", "1-2周(双)"))
    }

    @Test
    fun `a single week cannot be both odd and even`() {
        assertFalse(overlap("1-1周(单)", "1-1周(双)"))
    }

    @Test
    fun `adjacent single weeks of opposite parity never meet`() {
        assertFalse(overlap("1-1周(单)", "2-2周(双)"))
    }

    @Test
    fun `comma-separated ranges are compared as a set`() {
        assertFalse(overlap("1-4周,9-12周", "5-8周"))
        assertTrue(overlap("1-4周,9-12周", "11-14周"))
    }
}
