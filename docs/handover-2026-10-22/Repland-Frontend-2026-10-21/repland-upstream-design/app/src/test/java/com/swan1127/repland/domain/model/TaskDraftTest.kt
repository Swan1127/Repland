package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the two small objects in `Task.kt`: [TaskName], which derives the one-line label a
 * task is shown by, and [TaskDraftValidator], which decides whether a draft may be saved.
 *
 * `TaskName.fromDescription` is a truncation, so it is exactly the kind of code where an
 * off-by-one is invisible in review and obvious to the user: one character too many or too
 * few in a list row.
 */
class TaskDraftTest {

    // ---------------------------------------------------------------- TaskName

    @Test
    fun `a short single line is returned unchanged`() {
        assertEquals("复习高数", TaskName.fromDescription("复习高数"))
    }

    @Test
    fun `only the first line becomes the name`() {
        assertEquals("复习高数", TaskName.fromDescription("复习高数\n然后做习题"))
    }

    @Test
    fun `a carriage return also ends the line`() {
        assertEquals("复习高数", TaskName.fromDescription("复习高数\r\n然后做习题"))
    }

    @Test
    fun `surrounding whitespace is trimmed away`() {
        assertEquals("复习高数", TaskName.fromDescription("   复习高数   "))
    }

    @Test
    fun `a leading blank line does not become the name`() {
        // trim() runs before the first line is taken, so the leading newline disappears.
        assertEquals("复习高数", TaskName.fromDescription("\n\n复习高数"))
    }

    @Test
    fun `a description of exactly the limit is kept whole`() {
        val twentyFour = "a".repeat(24)
        assertEquals(twentyFour, TaskName.fromDescription(twentyFour))
    }

    @Test
    fun `one character past the limit is truncated and marked with an ellipsis`() {
        val name = TaskName.fromDescription("a".repeat(25))
        assertEquals("a".repeat(24) + "…", name)
        // The ellipsis makes the result one character longer than the limit, which is the
        // point: the marker is what tells the user the name was cut.
        assertEquals(25, name.length)
    }

    @Test
    fun `a blank description yields an empty name`() {
        assertEquals("", TaskName.fromDescription(""))
        assertEquals("", TaskName.fromDescription("   "))
        assertEquals("", TaskName.fromDescription("\n\n"))
    }

    @Test
    fun `the limit counts characters not bytes`() {
        // 24 Chinese characters are 24 characters; a byte-based limit would cut this in half.
        val chinese = "学".repeat(24)
        assertEquals(chinese, TaskName.fromDescription(chinese))
    }

    // ---------------------------------------------------------------- TaskDraftValidator

    @Test
    fun `a minimal draft with no duration is valid`() {
        assertTrue(TaskDraftValidator.isValid(draft(displayName = "复习", totalDurationMinutes = null)))
    }

    @Test
    fun `a blank display name is rejected`() {
        assertFalse(TaskDraftValidator.isValid(draft(displayName = "")))
        assertFalse(TaskDraftValidator.isValid(draft(displayName = "   ")))
    }

    @Test
    fun `the estimated day count must be between one and thirty`() {
        assertTrue(TaskDraftValidator.isValid(draft(estimatedDays = 1)))
        assertTrue(TaskDraftValidator.isValid(draft(estimatedDays = 30)))
        assertFalse(TaskDraftValidator.isValid(draft(estimatedDays = 0)))
        assertFalse(TaskDraftValidator.isValid(draft(estimatedDays = 31)))
        assertFalse(TaskDraftValidator.isValid(draft(estimatedDays = -1)))
    }

    @Test
    fun `the duration must be between one and fourteen hundred forty minutes`() {
        assertTrue(TaskDraftValidator.isValid(draft(totalDurationMinutes = 1)))
        assertTrue(TaskDraftValidator.isValid(draft(totalDurationMinutes = 1_440)))
        assertFalse(TaskDraftValidator.isValid(draft(totalDurationMinutes = 0)))
        assertFalse(TaskDraftValidator.isValid(draft(totalDurationMinutes = 1_441)))
        assertFalse(TaskDraftValidator.isValid(draft(totalDurationMinutes = -60)))
    }

    @Test
    fun `the description is not validated, only the display name`() {
        // Pinned deliberately: a draft may be saved with an empty description because the
        // description is optional context, while the name is what the list shows.
        assertTrue(TaskDraftValidator.isValid(draft(description = "")))
    }

    @Test
    fun `a due date and a planned day are both optional`() {
        assertTrue(TaskDraftValidator.isValid(draft(dueDate = null, scheduledForDate = null)))
        assertTrue(TaskDraftValidator.isValid(draft(dueDate = TEST_MONDAY, scheduledForDate = TEST_MONDAY)))
    }

    // ---------------------------------------------------------------- builder

    private fun draft(
        displayName: String = "复习",
        description: String = "描述",
        estimatedDays: Int = 1,
        totalDurationMinutes: Int? = 60,
        dueDate: java.time.LocalDate? = null,
        scheduledForDate: java.time.LocalDate? = null,
    ): TaskDraft = TaskDraft(
        displayName = displayName,
        description = description,
        category = TaskCategory.COURSE,
        userPriority = TaskPriority.MEDIUM,
        estimatedDays = estimatedDays,
        totalDurationMinutes = totalDurationMinutes,
        dueDate = dueDate,
        scheduledForDate = scheduledForDate,
    )
}
