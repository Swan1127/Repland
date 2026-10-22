package com.swan1127.repland.data

import com.swan1127.repland.domain.model.ExecutionLogEventType
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPointCandidatesTest {
    @Test
    fun onlyActualTaskWritingAndMatchingExecutionFeedbackBecomeCandidates() {
        val task = task().copy(completionResult = " task result ")
        val logs = listOf(
            log("task", TaskFeedback(completionResult = " result ", completedContent = " content ")),
            log("other", TaskFeedback(completionResult = "unrelated")),
        )
        assertEquals(listOf("task result", "result", "content"), ReviewPoint.candidatesFrom(task, logs))
    }

    @Test
    fun machineSummaryAndNumericProgressAreNotInventedAsReviewText() {
        val task = task().copy(completionSummary = "automated summary", progressPercent = 100)
        val logs = listOf(log("task", TaskFeedback(actualDurationMinutes = 30, progressPercent = 100)))
        assertEquals(emptyList<String>(), ReviewPoint.candidatesFrom(task, logs))
    }

    @Test
    fun repeatedUserWritingIsTrimmedAndDeduplicated() {
        val task = task().copy(completionResult = " point ")
        val logs = listOf(log("task", TaskFeedback(completionResult = "point", completedContent = " ")))
        assertEquals(listOf("point"), ReviewPoint.candidatesFrom(task, logs))
    }

    @Test
    fun theSameTextOnlyDeduplicatesWithinItsOwnTask() {
        val point = ReviewPoint("id", "task", "title", " point ", LocalDate.of(2026, 10, 22), 0)
        assertFalse(ReviewPoint.shouldSeed(listOf(point), "task", "point"))
        assertTrue(ReviewPoint.shouldSeed(listOf(point), "other", "point"))
        assertFalse(ReviewPoint.shouldSeed(emptyList(), "task", " "))
    }

    private fun task() = Task(
        id = "task",
        description = "actual task",
        displayName = "actual task",
        category = TaskCategory.UNSPECIFIED,
        userPriority = TaskPriority.UNSPECIFIED,
        estimatedDays = null,
        totalDurationMinutes = null,
        dueDate = null,
        status = TaskStatus.COMPLETED,
        completionSummary = null,
        actualDurationMinutes = null,
        progressPercent = null,
        postponeCount = 0,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )

    private fun log(taskId: String, feedback: TaskFeedback) = TaskExecutionLog(
        id = "log-$taskId",
        taskId = taskId,
        eventType = ExecutionLogEventType.FEEDBACK,
        confirmedStatus = TaskStatus.COMPLETED,
        feedback = feedback,
        correctedLogId = null,
        replacementTaskId = null,
        createdAtEpochMillis = 1,
    )
}
