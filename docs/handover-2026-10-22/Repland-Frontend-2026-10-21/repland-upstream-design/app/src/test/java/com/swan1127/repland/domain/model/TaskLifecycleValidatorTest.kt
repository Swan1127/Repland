package com.swan1127.repland.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TaskLifecycleValidator] is the transition table that keeps status changes at the
 * user-operation boundary. It has no AI entry point on purpose, so this table is the
 * whole contract — and it is worth pinning exhaustively.
 */
class TaskLifecycleValidatorTest {

    private fun allows(from: TaskStatus, to: TaskStatus): Boolean =
        TaskLifecycleValidator.isValidStatusConfirmation(from, to)

    @Test
    fun `a not-started task may move to in-progress, completed, postponed or cancelled`() {
        assertTrue(allows(TaskStatus.NOT_STARTED, TaskStatus.IN_PROGRESS))
        assertTrue(allows(TaskStatus.NOT_STARTED, TaskStatus.COMPLETED))
        assertTrue(allows(TaskStatus.NOT_STARTED, TaskStatus.POSTPONED))
        assertTrue(allows(TaskStatus.NOT_STARTED, TaskStatus.CANCELLED))
        assertFalse(allows(TaskStatus.NOT_STARTED, TaskStatus.NOT_STARTED))
        assertFalse(allows(TaskStatus.NOT_STARTED, TaskStatus.REPLACED))
    }

    @Test
    fun `an in-progress task may not return to not-started`() {
        assertTrue(allows(TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED))
        assertTrue(allows(TaskStatus.IN_PROGRESS, TaskStatus.POSTPONED))
        assertTrue(allows(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED))
        assertFalse(allows(TaskStatus.IN_PROGRESS, TaskStatus.NOT_STARTED))
        assertFalse(allows(TaskStatus.IN_PROGRESS, TaskStatus.REPLACED))
    }

    @Test
    fun `postponing again is a new event even though the visible state is unchanged`() {
        assertTrue(allows(TaskStatus.POSTPONED, TaskStatus.POSTPONED))
        assertTrue(allows(TaskStatus.POSTPONED, TaskStatus.IN_PROGRESS))
        assertTrue(allows(TaskStatus.POSTPONED, TaskStatus.COMPLETED))
        assertTrue(allows(TaskStatus.POSTPONED, TaskStatus.CANCELLED))
        assertFalse(allows(TaskStatus.POSTPONED, TaskStatus.NOT_STARTED))
    }

    @Test
    fun `a finished task can only be restored, never advanced`() {
        assertTrue(allows(TaskStatus.COMPLETED, TaskStatus.NOT_STARTED))
        assertFalse(allows(TaskStatus.COMPLETED, TaskStatus.IN_PROGRESS))
        assertFalse(allows(TaskStatus.COMPLETED, TaskStatus.COMPLETED))
        assertTrue(allows(TaskStatus.CANCELLED, TaskStatus.NOT_STARTED))
        assertFalse(allows(TaskStatus.CANCELLED, TaskStatus.IN_PROGRESS))
    }

    @Test
    fun `a replaced task is terminal`() {
        TaskStatus.entries.forEach { target ->
            assertFalse("REPLACED must not move to $target", allows(TaskStatus.REPLACED, target))
        }
    }

    @Test
    fun `feedback bounds are enforced`() {
        assertTrue(TaskLifecycleValidator.isValidFeedback(feedback()))
        assertTrue(TaskLifecycleValidator.isValidFeedback(feedback(actualDurationMinutes = 1)))
        assertTrue(TaskLifecycleValidator.isValidFeedback(feedback(actualDurationMinutes = 1_440)))
        assertFalse(TaskLifecycleValidator.isValidFeedback(feedback(actualDurationMinutes = 0)))
        assertFalse(TaskLifecycleValidator.isValidFeedback(feedback(actualDurationMinutes = 1_441)))
        assertTrue(TaskLifecycleValidator.isValidFeedback(feedback(progressPercent = 0)))
        assertTrue(TaskLifecycleValidator.isValidFeedback(feedback(progressPercent = 100)))
        assertFalse(TaskLifecycleValidator.isValidFeedback(feedback(progressPercent = 101)))
        assertFalse(TaskLifecycleValidator.isValidFeedback(feedback(progressPercent = -1)))
    }

    @Test
    fun `an empty feedback carries nothing`() {
        assertFalse(TaskLifecycleValidator.hasFeedback(feedback()))
        assertTrue(TaskLifecycleValidator.hasFeedback(feedback(actualDurationMinutes = 5)))
        assertTrue(TaskLifecycleValidator.hasFeedback(feedback(progressPercent = 10)))
        assertTrue(TaskLifecycleValidator.hasFeedback(feedback(completedContent = "notes")))
        assertTrue(TaskLifecycleValidator.hasFeedback(feedback(postponeReason = "sick")))
        assertFalse("whitespace is not feedback", TaskLifecycleValidator.hasFeedback(feedback(completedContent = "   ")))
    }

    @Test
    fun `completion accepts a blank progress or exactly 100`() {
        assertTrue(TaskLifecycleValidator.isValidCompletion(feedback()))
        assertTrue(TaskLifecycleValidator.isValidCompletion(feedback(progressPercent = 100)))
        assertFalse(TaskLifecycleValidator.isValidCompletion(feedback(progressPercent = 99)))
        assertFalse(TaskLifecycleValidator.isValidCompletion(feedback(progressPercent = 0)))
    }

    @Test
    fun `partial completion needs both a 1-99 progress and recorded content`() {
        assertTrue(
            TaskLifecycleValidator.isValidPartialCompletion(
                feedback(progressPercent = 40, completedContent = "half the report"),
            ),
        )
        assertFalse(
            "progress alone is not a partial completion",
            TaskLifecycleValidator.isValidPartialCompletion(feedback(progressPercent = 40)),
        )
        assertFalse(
            "content alone is not a partial completion",
            TaskLifecycleValidator.isValidPartialCompletion(feedback(completedContent = "notes")),
        )
        assertFalse(
            "100 is a completion, not a partial one",
            TaskLifecycleValidator.isValidPartialCompletion(
                feedback(progressPercent = 100, completedContent = "all of it"),
            ),
        )
        assertFalse(
            "0 is not a partial completion",
            TaskLifecycleValidator.isValidPartialCompletion(
                feedback(progressPercent = 0, completedContent = "nothing yet"),
            ),
        )
    }
}
