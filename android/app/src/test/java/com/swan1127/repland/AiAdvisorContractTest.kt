package com.swan1127.repland

import com.swan1127.repland.domain.model.AI_ADVISOR_CONTRACT_VERSION
import com.swan1127.repland.domain.model.AiAdviceValidation
import com.swan1127.repland.domain.model.AiAdviceValidator
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.AiAdvisorResult
import com.swan1127.repland.domain.model.AiFeedbackContext
import com.swan1127.repland.domain.model.AiHardConstraintContext
import com.swan1127.repland.domain.model.AiPlanDraftAdvice
import com.swan1127.repland.domain.model.AiProposedSegment
import com.swan1127.repland.domain.model.AiReplanRequest
import com.swan1127.repland.domain.model.AiRequestFactory
import com.swan1127.repland.domain.model.AiTaskContext
import com.swan1127.repland.domain.model.AiSegmentContext
import com.swan1127.repland.domain.model.AiTaskUnderstandingAdvice
import com.swan1127.repland.domain.model.ExecutionLogEventType
import com.swan1127.repland.domain.model.NoOpAiAdvisor
import com.swan1127.repland.domain.model.ConfirmedPlan
import com.swan1127.repland.domain.model.PlannedSegment
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskExecutionLog
import com.swan1127.repland.domain.model.TaskFeedback
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAdvisorContractTest {
    @Test
    fun task_request_is_scoped_to_the_current_task_and_at_most_five_feedback_records() {
        val task = task("secret-local-task-id")
        val logs = (1..6).map { index ->
            TaskExecutionLog(
                id = "log-$index",
                taskId = task.id,
                eventType = ExecutionLogEventType.FEEDBACK,
                confirmedStatus = TaskStatus.IN_PROGRESS,
                feedback = TaskFeedback(progressPercent = index, completedContent = "内容$index"),
                correctedLogId = null,
                replacementTaskId = null,
                createdAtEpochMillis = index.toLong(),
            )
        }
        val unrelatedLog = TaskExecutionLog(
            id = "other-log",
            taskId = "other",
            eventType = ExecutionLogEventType.FEEDBACK,
            confirmedStatus = TaskStatus.IN_PROGRESS,
            feedback = TaskFeedback(progressPercent = 90, completedContent = "other"),
            correctedLogId = null,
            replacementTaskId = null,
            createdAtEpochMillis = 99,
        )
        val request = AiRequestFactory.taskUnderstanding(
            task = task,
            executionLogs = logs + unrelatedLog,
            currentPlan = ConfirmedPlan(
                id = "plan",
                createdAtEpochMillis = 1,
                isCurrent = true,
                segments = listOf(
                    PlannedSegment(taskId = task.id, date = LocalDate.of(2026, 9, 20), startMinute = 540, endMinute = 600),
                    PlannedSegment(taskId = "other", date = LocalDate.of(2026, 9, 20), startMinute = 600, endMinute = 660),
                ),
            ),
        )

        assertEquals(task.displayName, request.task.title)
        assertEquals(5, request.task.recentFeedback.size)
        assertEquals(6, request.task.recentFeedback.first().progressPercent)
        assertEquals(1, request.task.confirmedSegments.size)
        assertFalse(request.toString().contains(task.id))
        assertFalse(request.toString().contains("other"))
    }

    @Test
    fun validator_accepts_only_matching_typed_responses_and_rejects_unrelated_plan_tasks() {
        val context = taskContext("任务 A")
        val request = AiReplanRequest(affectedTasks = listOf(context), constraintSummary = listOf("仅使用可用时间"))
        val unrelatedPlan = AiPlanDraftAdvice(
            contractVersion = AI_ADVISOR_CONTRACT_VERSION,
            requestId = request.requestId,
            orderedTaskReferences = listOf("任务 B"),
            proposedSegments = listOf(
                AiProposedSegment("任务 B", LocalDate.of(2026, 9, 20), 540, 600),
            ),
            explanation = "建议移动",
        )
        val mismatchedResponse = AiTaskUnderstandingAdvice(
            contractVersion = AI_ADVISOR_CONTRACT_VERSION,
            requestId = request.requestId,
            summary = "不应匹配",
        )

        assertTrue(AiAdviceValidator.validate(request, unrelatedPlan) is AiAdviceValidation.Invalid)
        assertTrue(AiAdviceValidator.validate(request, mismatchedResponse) is AiAdviceValidation.Invalid)
    }

    @Test
    fun validator_accepts_a_matching_bounded_task_advice_but_rejects_wrong_request_id() {
        val request = AiRequestFactory.taskUnderstanding(task("current"), emptyList(), null)
        val validResponse = AiTaskUnderstandingAdvice(
            contractVersion = AI_ADVISOR_CONTRACT_VERSION,
            requestId = request.requestId,
            summary = "可先整理章节，再完成练习。",
        )
        val wrongRequest = validResponse.copy(requestId = "another-request")

        assertTrue(AiAdviceValidator.validate(request, validResponse) is AiAdviceValidation.Valid)
        assertTrue(AiAdviceValidator.validate(request, wrongRequest) is AiAdviceValidation.Invalid)
    }

    @Test
    fun request_uses_a_correction_instead_of_the_corrected_feedback() {
        val task = task("current")
        val original = TaskExecutionLog(
            id = "original",
            taskId = task.id,
            eventType = ExecutionLogEventType.FEEDBACK,
            confirmedStatus = TaskStatus.IN_PROGRESS,
            feedback = TaskFeedback(progressPercent = 20, completedContent = "两页"),
            correctedLogId = null,
            replacementTaskId = null,
            createdAtEpochMillis = 1,
        )
        val correction = original.copy(
            id = "correction",
            eventType = ExecutionLogEventType.CORRECTION,
            feedback = TaskFeedback(progressPercent = 30, completedContent = "三页"),
            correctedLogId = original.id,
            createdAtEpochMillis = 2,
        )

        val request = AiRequestFactory.taskUnderstanding(task, listOf(original, correction), null)

        assertEquals(1, request.task.recentFeedback.size)
        assertEquals(30, request.task.recentFeedback.single().progressPercent)
        assertEquals("三页", request.task.recentFeedback.single().completedContent)
    }

    @Test
    fun validator_rejects_plan_segments_that_overlap_hard_or_user_locked_time() {
        val lockedTask = taskContext("任务 A").copy(
            confirmedSegments = listOf(
                AiSegmentContext(LocalDate.of(2026, 9, 20), 540, 600, isLocked = true),
            ),
        )
        val request = AiReplanRequest(
            affectedTasks = listOf(lockedTask),
            constraintSummary = emptyList(),
            hardConstraints = listOf(AiHardConstraintContext(LocalDate.of(2026, 9, 20), 660, 720)),
        )
        val movesLock = AiPlanDraftAdvice(
            contractVersion = AI_ADVISOR_CONTRACT_VERSION,
            requestId = request.requestId,
            orderedTaskReferences = listOf("task-a"),
            proposedSegments = listOf(AiProposedSegment("task-a", LocalDate.of(2026, 9, 20), 570, 630)),
            explanation = "移动时间段",
        )
        val overlapsConstraint = movesLock.copy(
            proposedSegments = listOf(AiProposedSegment("task-a", LocalDate.of(2026, 9, 20), 660, 720)),
        )

        assertTrue(AiAdviceValidator.validate(request, movesLock) is AiAdviceValidation.Invalid)
        assertTrue(AiAdviceValidator.validate(request, overlapsConstraint) is AiAdviceValidation.Invalid)
    }

    @Test
    fun default_advisor_makes_no_network_request_and_reports_unconfigured_service() = runBlocking {
        val request = AiRequestFactory.taskUnderstanding(task("current"), emptyList(), null)

        val result = NoOpAiAdvisor.request(request)

        assertTrue(result is AiAdvisorResult.Unavailable)
        assertEquals(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED, (result as AiAdvisorResult.Unavailable).reason)
    }

    private fun task(id: String) = Task(
        id = id,
        description = "当前任务说明",
        displayName = "当前任务",
        category = TaskCategory.COURSE,
        userPriority = TaskPriority.HIGH,
        estimatedDays = 2,
        totalDurationMinutes = 90,
        dueDate = LocalDate.of(2026, 9, 21),
        status = TaskStatus.IN_PROGRESS,
        completionSummary = null,
        actualDurationMinutes = null,
        progressPercent = null,
        postponeCount = 0,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )

    private fun taskContext(title: String) = AiTaskContext(
        reference = "task-a",
        title = title,
        description = "说明",
        category = TaskCategory.COURSE,
        initialPriority = TaskPriority.HIGH,
        currentStatus = TaskStatus.IN_PROGRESS,
        estimatedDays = 1,
        expectedDurationMinutes = 60,
        dueDate = null,
        recentFeedback = listOf(AiFeedbackContext(progressPercent = 50, actualDurationMinutes = null, completedContent = null, completionResult = null, postponeReason = null)),
        confirmedSegments = emptyList(),
    )
}
