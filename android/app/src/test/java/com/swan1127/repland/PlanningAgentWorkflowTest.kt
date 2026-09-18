package com.swan1127.repland

import com.swan1127.repland.domain.model.AI_ADVISOR_CONTRACT_VERSION
import com.swan1127.repland.domain.model.AiAdvisor
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.AiAdvisorRequest
import com.swan1127.repland.domain.model.AiAdvisorResult
import com.swan1127.repland.domain.model.AiPlanDraftAdvice
import com.swan1127.repland.domain.model.AiProposedSegment
import com.swan1127.repland.domain.model.AiTaskUnderstandingAdvice
import com.swan1127.repland.domain.model.CategoryPreferences
import com.swan1127.repland.domain.model.LocalPlanningFallback
import com.swan1127.repland.domain.model.PlanGenerationInput
import com.swan1127.repland.domain.model.PlanGenerator
import com.swan1127.repland.domain.model.PlanningAgent
import com.swan1127.repland.domain.model.PlanningAgentAccess
import com.swan1127.repland.domain.model.PlanningAgentAuditEvent
import com.swan1127.repland.domain.model.PlanningAgentAuditSink
import com.swan1127.repland.domain.model.PlanningAgentState
import com.swan1127.repland.domain.model.PlanningAgentWork
import com.swan1127.repland.domain.model.PlanningAgentWorkflow
import com.swan1127.repland.domain.model.Task
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.model.TaskPriority
import com.swan1127.repland.domain.model.TaskStatus
import com.swan1127.repland.domain.model.TimeBlockKind
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningAgentWorkflowTest {
    @Test
    fun consent_preview_cancel_never_calls_advisor_and_preserves_a_user_dismissal_state() {
        val advisor = RecordingAdvisor()
        val workflow = workflow(advisor)
        val work = PlanningAgentWork.TaskUnderstanding(task(), emptyList(), null)

        workflow.begin(work, PlanningAgentAccess(isEnabled = true, hasExplicitConsent = false))

        assertTrue(workflow.state.value is PlanningAgentState.AwaitingConsent)
        assertEquals(0, advisor.requests.size)

        workflow.resumeAfterConsent(PlanningAgentAccess(isEnabled = true, hasExplicitConsent = true))
        assertTrue(workflow.state.value is PlanningAgentState.PreviewingRequest)
        workflow.dismiss()

        assertTrue(workflow.state.value is PlanningAgentState.UserDismissed)
        assertEquals(0, advisor.requests.size)
    }

    @Test
    fun disabled_agent_immediately_returns_a_local_unconfirmed_replan_draft_without_provider_call() {
        val advisor = RecordingAdvisor()
        val workflow = workflow(advisor)
        workflow.begin(
            replanWork(),
            PlanningAgentAccess(isEnabled = false, hasExplicitConsent = true),
        )

        val state = workflow.state.value as PlanningAgentState.FailedFallback
        assertEquals(AiAdvisorFailureReason.DISABLED, state.reason)
        assertTrue(state.fallback is LocalPlanningFallback.Draft)
        assertEquals(0, advisor.requests.size)
    }

    @Test
    fun unavailable_timeout_and_invalid_output_all_degrade_to_the_local_draft() = runBlocking {
        listOf(
            AiAdvisorResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED),
            AiAdvisorResult.Failed(AiAdvisorFailureReason.TIMEOUT),
        ).forEach { result ->
            val workflow = workflow(RecordingAdvisor(result))
            workflow.begin(replanWork(), enabledAccess)
            workflow.confirmPreview(enabledAccess)
            val fallback = workflow.state.value as PlanningAgentState.FailedFallback
            assertEquals((result as? AiAdvisorResult.Failed)?.reason ?: (result as AiAdvisorResult.Unavailable).reason, fallback.reason)
            assertTrue(fallback.fallback is LocalPlanningFallback.Draft)
        }

        val invalidAdvisor = RecordingAdvisor { request ->
            AiAdvisorResult.Advice(
                AiPlanDraftAdvice(
                    contractVersion = AI_ADVISOR_CONTRACT_VERSION,
                    requestId = request.requestId,
                    orderedTaskReferences = listOf("another-task"),
                    proposedSegments = emptyList(),
                    explanation = "越权建议",
                ),
            )
        }
        val workflow = workflow(invalidAdvisor)
        workflow.begin(replanWork(), enabledAccess)
        workflow.confirmPreview(enabledAccess)
        val fallback = workflow.state.value as PlanningAgentState.FailedFallback
        assertEquals(AiAdvisorFailureReason.INVALID_RESPONSE, fallback.reason)
        assertTrue(fallback.fallback is LocalPlanningFallback.Draft)
    }

    @Test
    fun valid_task_advice_is_shown_but_marking_it_accepted_has_no_write_capability() = runBlocking {
        val advisor = RecordingAdvisor { request ->
            AiAdvisorResult.Advice(
                AiTaskUnderstandingAdvice(
                    contractVersion = AI_ADVISOR_CONTRACT_VERSION,
                    requestId = request.requestId,
                    summary = "先整理资料，再开始第一小节。",
                ),
            )
        }
        val audit = mutableListOf<PlanningAgentAuditEvent>()
        val workflow = workflow(advisor, audit::add)
        workflow.begin(
            PlanningAgentWork.TaskUnderstanding(task(), emptyList(), null),
            enabledAccess,
        )
        workflow.confirmPreview(enabledAccess)

        assertTrue(workflow.state.value is PlanningAgentState.ShowingAdvice)
        workflow.markAccepted()
        assertTrue(workflow.state.value is PlanningAgentState.UserAccepted)
        assertTrue(audit.all { event ->
            event.resultCode.isNotBlank() && event.contractVersion == AI_ADVISOR_CONTRACT_VERSION
        })
        // Audit events intentionally carry no task title, description, feedback, or Room identifiers.
        assertFalse(audit.toString().contains(task().description))
    }

    @Test
    fun all_supported_request_types_are_typed_and_a_proposal_outside_availability_is_rejected() = runBlocking {
        val calls = mutableListOf<AiAdvisorRequest>()
        val advisor = RecordingAdvisor { request ->
            calls += request
            when (request) {
                is com.swan1127.repland.domain.model.AiTaskUnderstandingRequest -> AiAdvisorResult.Advice(
                    AiTaskUnderstandingAdvice(AI_ADVISOR_CONTRACT_VERSION, request.requestId, "任务理解"),
                )

                is com.swan1127.repland.domain.model.AiDifficultyAndDurationRequest -> AiAdvisorResult.Failed(AiAdvisorFailureReason.TIMEOUT)
                is com.swan1127.repland.domain.model.AiTaskBreakdownRequest -> AiAdvisorResult.Failed(AiAdvisorFailureReason.TIMEOUT)
                is com.swan1127.repland.domain.model.AiSortingExplanationRequest -> AiAdvisorResult.Failed(AiAdvisorFailureReason.TIMEOUT)
                is com.swan1127.repland.domain.model.AiDailySummaryRequest -> AiAdvisorResult.Failed(AiAdvisorFailureReason.TIMEOUT)
                is com.swan1127.repland.domain.model.AiReplanRequest -> AiAdvisorResult.Advice(
                    AiPlanDraftAdvice(
                        AI_ADVISOR_CONTRACT_VERSION,
                        request.requestId,
                        orderedTaskReferences = listOf("affected-task-1"),
                        proposedSegments = listOf(
                            AiProposedSegment("affected-task-1", LocalDate.now(), 12 * 60, 13 * 60),
                        ),
                        explanation = "排到可用时间外",
                    ),
                )
            }
        }
        val workItems = listOf(
            PlanningAgentWork.TaskUnderstanding(task(), emptyList(), null),
            PlanningAgentWork.DifficultyAndDuration(task(), emptyList(), null),
            PlanningAgentWork.TaskBreakdown(task(), emptyList(), null),
            PlanningAgentWork.SortingExplanation(task(), emptyList(), null, listOf("本地截止日期理由")),
            replanWork(),
            PlanningAgentWork.DailySummary(LocalDate.now(), emptyList()),
        )

        workItems.forEach { work ->
            val workflow = workflow(advisor)
            workflow.begin(work, enabledAccess)
            workflow.confirmPreview(enabledAccess)
        }

        assertEquals(6, calls.size)
        assertTrue(calls.any { it is com.swan1127.repland.domain.model.AiReplanRequest })
        // The replan response used a valid 30-minute shape but an unavailable slot, so local validation rejects it.
        val workflow = workflow(advisor)
        workflow.begin(replanWork(), enabledAccess)
        workflow.confirmPreview(enabledAccess)
        assertEquals(AiAdvisorFailureReason.INVALID_RESPONSE, (workflow.state.value as PlanningAgentState.FailedFallback).reason)
    }

    @Test
    fun cancelling_an_inflight_request_leaves_the_user_dismissed_state_and_never_applies_advice() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val neverReturns = CompletableDeferred<AiAdvisorResult>()
        val workflow = workflow(object : AiAdvisor {
            override suspend fun request(request: AiAdvisorRequest): AiAdvisorResult {
                started.complete(Unit)
                return neverReturns.await()
            }
        })
        workflow.begin(PlanningAgentWork.TaskUnderstanding(task(), emptyList(), null), enabledAccess)

        val requestJob = async { workflow.confirmPreview(enabledAccess) }
        started.await()
        workflow.dismiss()
        requestJob.cancelAndJoin()

        assertTrue(workflow.state.value is PlanningAgentState.UserDismissed)
    }

    private fun workflow(
        advisor: AiAdvisor,
        auditSink: PlanningAgentAuditSink = PlanningAgentAuditSink { },
    ): PlanningAgentWorkflow = PlanningAgentWorkflow(
        PlanningAgent(advisor, PlanGenerator),
        auditSink,
    )

    private fun replanWork(): PlanningAgentWork.Replan {
        val task = task()
        val today = LocalDate.now()
        return PlanningAgentWork.Replan(
            affectedTasks = listOf(task),
            executionLogs = emptyList(),
            currentPlan = null,
            localPlanInput = PlanGenerationInput(
                tasks = listOf(task),
                weeklyBlocks = listOf(
                    WeeklyTimeBlock(
                        id = "available",
                        title = "可用",
                        kind = TimeBlockKind.AVAILABLE,
                        dayOfWeek = today.dayOfWeek,
                        startMinute = 9 * 60,
                        endMinute = 11 * 60,
                        weekPattern = null,
                        createdAtEpochMillis = 1,
                        updatedAtEpochMillis = 1,
                    ),
                ),
                dateOverrides = emptyList(),
                semesterFirstWeekMonday = null,
                categoryPreferences = CategoryPreferences.defaults,
            ),
            constraintSummary = listOf("仅使用已确认可用时间"),
        )
    }

    private fun task() = Task(
        id = "task-id-never-leaves-device",
        description = "完成离散数学练习的第一章",
        displayName = "离散数学练习",
        category = TaskCategory.COURSE,
        userPriority = TaskPriority.HIGH,
        estimatedDays = 1,
        totalDurationMinutes = 60,
        dueDate = LocalDate.now().plusDays(1),
        status = TaskStatus.NOT_STARTED,
        completionSummary = null,
        actualDurationMinutes = null,
        progressPercent = null,
        postponeCount = 0,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )

    private class RecordingAdvisor(
        private val resultFor: (AiAdvisorRequest) -> AiAdvisorResult = {
            AiAdvisorResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
        },
    ) : AiAdvisor {
        constructor(result: AiAdvisorResult) : this({ result })

        val requests = mutableListOf<AiAdvisorRequest>()

        override suspend fun request(request: AiAdvisorRequest): AiAdvisorResult {
            requests += request
            return resultFor(request)
        }
    }

    private companion object {
        val enabledAccess = PlanningAgentAccess(isEnabled = true, hasExplicitConsent = true)
    }
}
