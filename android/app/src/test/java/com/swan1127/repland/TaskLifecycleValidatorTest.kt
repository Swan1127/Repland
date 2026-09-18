package com.swan1127.repland

import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskLifecycleValidator
import com.swan1127.repland.domain.model.TaskStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskLifecycleValidatorTest {
    @Test
    fun `only user lifecycle transitions preserve all supported task states`() {
        assertTrue(TaskLifecycleValidator.isValidStatusConfirmation(
            TaskStatus.NOT_STARTED,
            TaskStatus.IN_PROGRESS,
        ))
        assertTrue(TaskLifecycleValidator.isValidStatusConfirmation(
            TaskStatus.IN_PROGRESS,
            TaskStatus.POSTPONED,
        ))
        assertTrue(TaskLifecycleValidator.isValidStatusConfirmation(
            TaskStatus.POSTPONED,
            TaskStatus.POSTPONED,
        ))
        assertTrue(TaskLifecycleValidator.isValidStatusConfirmation(
            TaskStatus.NOT_STARTED,
            TaskStatus.COMPLETED,
        ))
        assertTrue(TaskLifecycleValidator.isValidStatusConfirmation(
            TaskStatus.IN_PROGRESS,
            TaskStatus.CANCELLED,
        ))
        assertTrue(TaskLifecycleValidator.isValidStatusConfirmation(
            TaskStatus.CANCELLED,
            TaskStatus.NOT_STARTED,
        ))
        assertFalse(TaskLifecycleValidator.isValidStatusConfirmation(
            TaskStatus.REPLACED,
            TaskStatus.NOT_STARTED,
        ))
        assertFalse(TaskLifecycleValidator.isValidStatusConfirmation(
            TaskStatus.COMPLETED,
            TaskStatus.POSTPONED,
        ))
    }

    @Test
    fun `completion and partial feedback enforce their respective confirmations`() {
        assertTrue(TaskLifecycleValidator.isValidCompletion(
            TaskFeedback(completedContent = "完成题目", progressPercent = 100),
        ))
        assertFalse(TaskLifecycleValidator.isValidCompletion(
            TaskFeedback(completedContent = "完成题目", progressPercent = 80),
        ))
        assertFalse(TaskLifecycleValidator.isValidCompletion(TaskFeedback(progressPercent = 100)))

        assertTrue(TaskLifecycleValidator.isValidPartialCompletion(
            TaskFeedback(completedContent = "完成第一章", progressPercent = 40, actualDurationMinutes = 30),
        ))
        assertFalse(TaskLifecycleValidator.isValidPartialCompletion(
            TaskFeedback(completedContent = "完成第一章", progressPercent = 100),
        ))
        assertFalse(TaskLifecycleValidator.isValidPartialCompletion(
            TaskFeedback(completedContent = "完成第一章", progressPercent = 20, actualDurationMinutes = 0),
        ))
    }
}
