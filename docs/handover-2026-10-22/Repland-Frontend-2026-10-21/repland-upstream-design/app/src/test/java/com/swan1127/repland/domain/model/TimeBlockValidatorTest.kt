package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TimeBlockValidator] is the only place that turns a typed "08:30" into a stored minute
 * offset, and the only place that renders one back. A rounding or padding mistake here is
 * invisible until a class shows up at the wrong time, so the round-trip is tested across the
 * whole day rather than at a few sample points.
 */
class TimeBlockValidatorTest {

    // ---------------------------------------------------------------- parse

    @Test
    fun `parseTime reads a two-digit clock time`() {
        assertEquals(510, TimeBlockValidator.parseTime("08:30"))
    }

    @Test
    fun `parseTime reads midnight as zero`() {
        assertEquals(0, TimeBlockValidator.parseTime("00:00"))
    }

    @Test
    fun `parseTime reads the last minute of the day`() {
        assertEquals(1_439, TimeBlockValidator.parseTime("23:59"))
    }

    @Test
    fun `parseTime refuses 24_00 because the model has no such instant`() {
        assertNull(TimeBlockValidator.parseTime("24:00"))
    }

    @Test
    fun `parseTime refuses a single-digit hour`() {
        assertNull(TimeBlockValidator.parseTime("8:30"))
    }

    @Test
    fun `parseTime refuses a minute of sixty`() {
        assertNull(TimeBlockValidator.parseTime("08:60"))
    }

    @Test
    fun `parseTime refuses a trailing seconds part`() {
        assertNull(TimeBlockValidator.parseTime("08:30:00"))
    }

    @Test
    fun `parseTime trims surrounding whitespace`() {
        assertEquals(510, TimeBlockValidator.parseTime("  08:30  "))
    }

    @Test
    fun `parseTime refuses a blank string`() {
        assertNull(TimeBlockValidator.parseTime(""))
        assertNull(TimeBlockValidator.parseTime("   "))
    }

    @Test
    fun `parseTime refuses text that is not a clock time`() {
        assertNull(TimeBlockValidator.parseTime("ab:cd"))
        assertNull(TimeBlockValidator.parseTime("上午八点"))
    }

    // ---------------------------------------------------------------- format

    @Test
    fun `formatTime pads both fields`() {
        assertEquals("00:00", TimeBlockValidator.formatTime(0))
        assertEquals("08:30", TimeBlockValidator.formatTime(510))
        assertEquals("09:05", TimeBlockValidator.formatTime(545))
    }

    @Test
    fun `formatTime renders the last minute of the day`() {
        assertEquals("23:59", TimeBlockValidator.formatTime(1_439))
    }

    @Test
    fun `every minute of the day round-trips through format and parse`() {
        (0 until 24 * 60).forEach { minute ->
            val text = TimeBlockValidator.formatTime(minute)
            assertEquals("round trip failed for $text", minute, TimeBlockValidator.parseTime(text))
        }
    }

    @Test
    fun `formatTime renders the exclusive end of the day as 24_00, which parseTime then refuses`() {
        // A block may legally end at minute 1440 (isValidRange allows end == 1440), and the
        // editor renders that end as "24:00". parseTime cannot read it back because the hour
        // pattern stops at 23. The asymmetry is real and pinned here rather than papered over:
        // 1440 is an end-of-day marker, never a start, so nothing needs to parse it back.
        assertEquals("24:00", TimeBlockValidator.formatTime(24 * 60))
        assertNull(TimeBlockValidator.parseTime("24:00"))
    }

    // ---------------------------------------------------------------- weekly block validity

    @Test
    fun `a weekly block needs a non-blank title`() {
        assertTrue(TimeBlockValidator.isValid(weeklyBlockDraft(title = "高数", startMinute = 480, endMinute = 540)))
        assertFalse(TimeBlockValidator.isValid(weeklyBlockDraft(title = "   ", startMinute = 480, endMinute = 540)))
    }

    @Test
    fun `a weekly block needs its start before its end`() {
        assertFalse(TimeBlockValidator.isValid(weeklyBlockDraft(startMinute = 540, endMinute = 540)))
        assertFalse(TimeBlockValidator.isValid(weeklyBlockDraft(startMinute = 600, endMinute = 540)))
    }

    @Test
    fun `a weekly block may span the whole day`() {
        assertTrue(TimeBlockValidator.isValid(weeklyBlockDraft(startMinute = 0, endMinute = 24 * 60)))
    }

    @Test
    fun `a weekly block cannot start at or past the end of the day`() {
        assertFalse(TimeBlockValidator.isValid(weeklyBlockDraft(startMinute = 24 * 60, endMinute = 24 * 60)))
        assertFalse(TimeBlockValidator.isValid(weeklyBlockDraft(startMinute = -1, endMinute = 60)))
    }

    @Test
    fun `a weekly block cannot end at zero`() {
        assertFalse(TimeBlockValidator.isValid(weeklyBlockDraft(startMinute = 0, endMinute = 0)))
    }

    @Test
    fun `a weekly block cannot end past the end of the day`() {
        assertFalse(TimeBlockValidator.isValid(weeklyBlockDraft(startMinute = 0, endMinute = 24 * 60 + 1)))
    }

    // ---------------------------------------------------------------- date override validity

    @Test
    fun `a date override follows the same title and range rule`() {
        assertTrue(TimeBlockValidator.isValid(dateOverrideDraft(title = "调课", startMinute = 480, endMinute = 540)))
        assertFalse(TimeBlockValidator.isValid(dateOverrideDraft(title = "", startMinute = 480, endMinute = 540)))
        assertFalse(TimeBlockValidator.isValid(dateOverrideDraft(title = "调课", startMinute = 540, endMinute = 480)))
    }

    // ---------------------------------------------------------------- builders

    private fun weeklyBlockDraft(
        title: String = "课",
        startMinute: Int,
        endMinute: Int,
    ): WeeklyTimeBlockDraft = WeeklyTimeBlockDraft(
        title = title,
        kind = TimeBlockKind.COURSE,
        dayOfWeek = TEST_MONDAY.dayOfWeek,
        startMinute = startMinute,
        endMinute = endMinute,
    )

    private fun dateOverrideDraft(
        title: String = "覆盖",
        startMinute: Int,
        endMinute: Int,
    ): DateOverrideDraft = DateOverrideDraft(
        title = title,
        type = DateOverrideType.BLOCKED,
        date = TEST_MONDAY,
        startMinute = startMinute,
        endMinute = endMinute,
    )
}
