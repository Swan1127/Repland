package com.swan1127.repland

import com.swan1127.repland.domain.model.ExecutionLogEventType
import com.swan1127.repland.domain.model.ProfileEvidenceGenerator
import com.swan1127.repland.domain.model.ProfileEvidenceScope
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileEvidenceGeneratorTest {
    @Test
    fun uses_confirmed_feedback_and_uses_correction_instead_of_the_corrected_log() {
        val task = task(id = "course")
        val original = log(
            id = "original",
            taskId = task.id,
            feedback = TaskFeedback(actualDurationMinutes = 20, progressPercent = 30, completedContent = "读两页"),
        )
        val correction = log(
            id = "correction",
            taskId = task.id,
            eventType = ExecutionLogEventType.CORRECTION,
            feedback = TaskFeedback(actualDurationMinutes = 35, progressPercent = 40, completedContent = "读三页"),
            correctedLogId = original.id,
        )

        val evidence = ProfileEvidenceGenerator.generate(listOf(task), listOf(original, correction))

        assertEquals(2, evidence.size)
        assertTrue(evidence.all { it.sourceLogIds.contains(correction.id) })
        assertFalse(evidence.any { it.sourceLogIds.contains(original.id) })
        assertTrue(evidence.any { it.scope == ProfileEvidenceScope.LEARNING })
        assertTrue(evidence.first { it.scope == ProfileEvidenceScope.GENERAL }.conclusion.contains("35 分钟"))
    }

    @Test
    fun non_course_feedback_does_not_create_learning_evidence() {
        val task = task(id = "office", category = TaskCategory.OFFICE)
        val evidence = ProfileEvidenceGenerator.generate(
            listOf(task),
            listOf(log(id = "feedback", taskId = task.id, feedback = TaskFeedback(completionResult = "已办完"))),
        )

        assertTrue(evidence.none { it.scope == ProfileEvidenceScope.LEARNING })
    }

    private fun task(id: String, category: TaskCategory = TaskCategory.COURSE) = Task(
        id = id,
        description = "说明",
        displayName = "任务 $id",
        category = category,
        userPriority = TaskPriority.MEDIUM,
        estimatedDays = 1,
        totalDurationMinutes = 60,
        dueDate = null,
        status = TaskStatus.IN_PROGRESS,
        completionSummary = null,
        actualDurationMinutes = null,
        progressPercent = null,
        postponeCount = 0,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )

    private fun log(
        id: String,
        taskId: String,
        eventType: ExecutionLogEventType = ExecutionLogEventType.FEEDBACK,
        feedback: TaskFeedback,
        correctedLogId: String? = null,
    ) = TaskExecutionLog(
        id = id,
        taskId = taskId,
        eventType = eventType,
        confirmedStatus = TaskStatus.IN_PROGRESS,
        feedback = feedback,
        correctedLogId = correctedLogId,
        replacementTaskId = null,
        createdAtEpochMillis = 1,
    )
}
