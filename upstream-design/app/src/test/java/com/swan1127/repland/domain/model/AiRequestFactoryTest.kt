package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AiRequestFactory] is the last gate before data leaves the device. Its whole point is
 * that the request is *minimised*: only the selected task, only the last few feedback
 * rows, and never a row the user has already corrected. A regression here does not throw
 * — it quietly sends more (or staler) data than the user consented to.
 */
class AiRequestFactoryTest {

    // ---------------------------------------------------------------- request types

    @Test
    fun `each single-task factory produces its own request type with the local reference`() {
        val t1 = task("t1")
        val understanding = AiRequestFactory.taskUnderstanding(t1, emptyList(), null)
        assertEquals("current-task", understanding.task.reference)

        assertEquals("current-task", AiRequestFactory.difficultyAndDuration(t1, emptyList(), null).task.reference)
        assertEquals("current-task", AiRequestFactory.taskBreakdown(t1, emptyList(), null).task.reference)
        assertEquals(
            "current-task",
            AiRequestFactory.sortingExplanation(t1, emptyList(), null, emptyList()).task.reference,
        )
    }

    @Test
    fun `the request id is unique per request`() {
        val first = AiRequestFactory.taskUnderstanding(task("t1"), emptyList(), null)
        val second = AiRequestFactory.taskUnderstanding(task("t1"), emptyList(), null)
        assertTrue(first.requestId != second.requestId)
        assertEquals(AI_ADVISOR_CONTRACT_VERSION, first.contractVersion)
    }

    // ---------------------------------------------------------------- task context

    @Test
    fun `the task context mirrors the task without inventing fields`() {
        val due = TEST_MONDAY.plusDays(3)
        val scheduled = TEST_MONDAY.plusDays(1)
        val source = task(
            "t1",
            durationMinutes = 90,
            dueDate = due,
            status = TaskStatus.IN_PROGRESS,
            priority = TaskPriority.HIGH,
            category = TaskCategory.OFFICE,
            scheduledForDate = scheduled,
        )
        val context = AiRequestFactory.taskUnderstanding(source, emptyList(), null).task
        assertEquals("Task t1", context.title)
        assertEquals("description of t1", context.description)
        assertEquals(TaskCategory.OFFICE, context.category)
        assertEquals(TaskPriority.HIGH, context.initialPriority)
        assertEquals(TaskStatus.IN_PROGRESS, context.currentStatus)
        assertEquals(1, context.estimatedDays)
        assertEquals(90, context.expectedDurationMinutes)
        assertEquals(due, context.dueDate)
        assertEquals(scheduled, context.scheduledForDate)
    }

    @Test
    fun `only the selected task's confirmed segments are sent`() {
        val plan = confirmedPlan(
            segments = listOf(
                segment("s1", taskId = "t1", startMinute = 480, endMinute = 540, isLocked = true),
                segment("s2", taskId = "t2", startMinute = 600, endMinute = 660),
            ),
        )
        val context = AiRequestFactory.taskUnderstanding(task("t1"), emptyList(), plan).task
        assertEquals(
            listOf(AiSegmentContext(TEST_MONDAY, 480, 540, isLocked = true)),
            context.confirmedSegments,
        )
    }

    @Test
    fun `a request without a confirmed plan carries no segments`() {
        assertTrue(AiRequestFactory.taskUnderstanding(task("t1"), emptyList(), null).task.confirmedSegments.isEmpty())
    }

    // ---------------------------------------------------------------- effective feedback

    @Test
    fun `feedback rows for other tasks are not sent`() {
        val logs = listOf(
            executionLog("l1", "t1", feedback = feedback(actualDurationMinutes = 30)),
            executionLog("l2", "t2", feedback = feedback(actualDurationMinutes = 45)),
        )
        val context = AiRequestFactory.taskUnderstanding(task("t1"), logs, null).task
        assertEquals(listOf(AiFeedbackContext(30, null, null, null, null)), context.recentFeedback)
    }

    @Test
    fun `feedback is sent newest first`() {
        // The stored list is oldest-first; the request reverses it.
        val logs = listOf(
            executionLog("l1", "t1", feedback = feedback(actualDurationMinutes = 30), createdAtEpochMillis = 10L),
            executionLog("l2", "t1", feedback = feedback(actualDurationMinutes = 45), createdAtEpochMillis = 20L),
        )
        val context = AiRequestFactory.taskUnderstanding(task("t1"), logs, null).task
        assertEquals(
            listOf(AiFeedbackContext(45, null, null, null, null), AiFeedbackContext(30, null, null, null, null)),
            context.recentFeedback,
        )
    }

    @Test
    fun `a corrected feedback row is not sent, only its correction`() {
        val logs = listOf(
            executionLog("l1", "t1", feedback = feedback(actualDurationMinutes = 30)),
            executionLog(
                "l2", "t1",
                eventType = ExecutionLogEventType.CORRECTION,
                feedback = feedback(actualDurationMinutes = 45),
                correctedLogId = "l1",
            ),
        )
        val context = AiRequestFactory.taskUnderstanding(task("t1"), logs, null).task
        assertEquals(listOf(AiFeedbackContext(45, null, null, null, null)), context.recentFeedback)
    }

    @Test
    fun `a row with no confirmed feedback is not sent`() {
        val logs = listOf(
            executionLog("l1", "t1", eventType = ExecutionLogEventType.STATUS_CHANGE),
            executionLog("l2", "t1", feedback = feedback(progressPercent = 50)),
        )
        val context = AiRequestFactory.taskUnderstanding(task("t1"), logs, null).task
        assertEquals(listOf(AiFeedbackContext(null, 50, null, null, null)), context.recentFeedback)
    }

    @Test
    fun `only the five most recent feedback rows are sent`() {
        val logs = (1..6).map { index ->
            executionLog("l$index", "t1", feedback = feedback(actualDurationMinutes = index))
        }
        val context = AiRequestFactory.taskUnderstanding(task("t1"), logs, null).task
        assertEquals(5, context.recentFeedback.size)
        // Newest first, so l6 leads and l1 (the oldest) is the one dropped.
        assertEquals(6, context.recentFeedback.first().actualDurationMinutes)
        assertEquals(2, context.recentFeedback.last().actualDurationMinutes)
    }

    // ---------------------------------------------------------------- sorting explanation

    @Test
    fun `sorting reasons drop blanks and are capped at twelve`() {
        val reasons = listOf("", "  ") + (1..20).map { "reason $it" }
        val request = AiRequestFactory.sortingExplanation(task("t1"), emptyList(), null, reasons)
        assertEquals(12, request.localRankingReasons.size)
        assertEquals("reason 1", request.localRankingReasons.first())
    }

    // ---------------------------------------------------------------- replan

    @Test
    fun `affected tasks are aliased in the order they were given`() {
        val request = AiRequestFactory.replan(
            affectedTasks = listOf(task("a"), task("b")),
            executionLogs = emptyList(),
            currentPlan = null,
            constraintSummary = emptyList(),
            hardConstraints = emptyList(),
            protectedManualTaskIds = emptyList(),
        )
        assertEquals(listOf("affected-task-1", "affected-task-2"), request.affectedTasks.map(AiTaskContext::reference))
    }

    @Test
    fun `a duplicated task id is only sent once`() {
        val request = AiRequestFactory.replan(
            affectedTasks = listOf(task("a"), task("a")),
            executionLogs = emptyList(),
            currentPlan = null,
            constraintSummary = emptyList(),
            hardConstraints = emptyList(),
            protectedManualTaskIds = emptyList(),
        )
        assertEquals(1, request.affectedTasks.size)
    }

    @Test
    fun `at most fifty affected tasks are sent`() {
        val request = AiRequestFactory.replan(
            affectedTasks = (1..55).map { task("t$it") },
            executionLogs = emptyList(),
            currentPlan = null,
            constraintSummary = emptyList(),
            hardConstraints = emptyList(),
            protectedManualTaskIds = emptyList(),
        )
        assertEquals(50, request.affectedTasks.size)
        assertEquals("affected-task-50", request.affectedTasks.last().reference)
    }

    @Test
    fun `a protected manual order is mapped to the aliases that were actually sent`() {
        val request = AiRequestFactory.replan(
            affectedTasks = listOf(task("a"), task("b")),
            executionLogs = emptyList(),
            currentPlan = null,
            constraintSummary = emptyList(),
            hardConstraints = emptyList(),
            protectedManualTaskIds = listOf("b", "a", "not-affected"),
        )
        assertEquals(listOf("affected-task-2", "affected-task-1"), request.protectedManualOrderReferences)
    }

    @Test
    fun `constraint summaries drop blanks and are capped at twenty`() {
        val request = AiRequestFactory.replan(
            affectedTasks = listOf(task("a")),
            executionLogs = emptyList(),
            currentPlan = null,
            constraintSummary = listOf("", "  ") + (1..30).map { "constraint $it" },
            hardConstraints = emptyList(),
            protectedManualTaskIds = emptyList(),
        )
        assertEquals(20, request.constraintSummary.size)
        assertEquals("constraint 1", request.constraintSummary.first())
    }

    @Test
    fun `hard constraints are de-duplicated and capped at two hundred forty`() {
        val duplicate = AiHardConstraintContext(TEST_MONDAY, 480, 540)
        val many = (0 until 245).map { AiHardConstraintContext(TEST_MONDAY.plusDays(it.toLong()), 480, 540) }
        val request = AiRequestFactory.replan(
            affectedTasks = listOf(task("a")),
            executionLogs = emptyList(),
            currentPlan = null,
            constraintSummary = emptyList(),
            hardConstraints = listOf(duplicate, duplicate) + many,
            protectedManualTaskIds = emptyList(),
        )
        assertEquals(240, request.hardConstraints.size)
    }

    // ---------------------------------------------------------------- daily summary

    @Test
    fun `a daily summary sends at most five confirmed feedback rows`() {
        val logs = (1..8).map { index ->
            executionLog("l$index", "t1", feedback = feedback(actualDurationMinutes = index))
        }
        val request = AiRequestFactory.dailySummary(TEST_MONDAY, logs)
        assertEquals(TEST_MONDAY, request.day)
        assertEquals(5, request.confirmedFeedback.size)
        assertEquals(8, request.confirmedFeedback.first().actualDurationMinutes)
    }
}
