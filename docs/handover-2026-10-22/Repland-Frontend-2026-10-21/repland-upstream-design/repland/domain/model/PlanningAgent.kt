package com.swan1127.repland.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The finite set of planning operations exposed by the MVP.  This deliberately
 * has no free-form prompt or task-status command.
 */
enum class PlanningAgentRequestType {
    TASK_UNDERSTANDING,
    DIFFICULTY_AND_DURATION,
    TASK_BREAKDOWN,
    SORTING_EXPLANATION,
    REPLAN,
    DAILY_SUMMARY,
}

data class PlanningAgentAccess(
    val isEnabled: Boolean,
    val hasExplicitConsent: Boolean,
)

/** Inputs remain local until [PlanningAgentState.PreviewingRequest] is explicitly confirmed. */
sealed interface PlanningAgentWork {
    val type: PlanningAgentRequestType

    data class TaskUnderstanding(
        val task: Task,
        val executionLogs: List<TaskExecutionLog>,
        val currentPlan: ConfirmedPlan?,
    ) : PlanningAgentWork {
        override val type = PlanningAgentRequestType.TASK_UNDERSTANDING
    }

    data class DifficultyAndDuration(
        val task: Task,
        val executionLogs: List<TaskExecutionLog>,
        val currentPlan: ConfirmedPlan?,
    ) : PlanningAgentWork {
        override val type = PlanningAgentRequestType.DIFFICULTY_AND_DURATION
    }

    data class TaskBreakdown(
        val task: Task,
        val executionLogs: List<TaskExecutionLog>,
        val currentPlan: ConfirmedPlan?,
    ) : PlanningAgentWork {
        override val type = PlanningAgentRequestType.TASK_BREAKDOWN
    }

    data class SortingExplanation(
        val task: Task,
        val executionLogs: List<TaskExecutionLog>,
        val currentPlan: ConfirmedPlan?,
        val localRankingReasons: List<String>,
    ) : PlanningAgentWork {
        override val type = PlanningAgentRequestType.SORTING_EXPLANATION
    }

    /** Only directly affected active tasks enter the AI request; local planning still sees all tasks. */
    data class Replan(
        val affectedTasks: List<Task>,
        val executionLogs: List<TaskExecutionLog>,
        val currentPlan: ConfirmedPlan?,
        val localPlanInput: PlanGenerationInput,
        val constraintSummary: List<String>,
    ) : PlanningAgentWork {
        override val type = PlanningAgentRequestType.REPLAN
    }

    data class DailySummary(
        val day: LocalDate,
        val confirmedExecutionLogs: List<TaskExecutionLog>,
    ) : PlanningAgentWork {
        override val type = PlanningAgentRequestType.DAILY_SUMMARY
    }
}

sealed interface LocalPlanningFallback {
    val explanation: String

    data class Advice(override val explanation: String) : LocalPlanningFallback

    /** Still an unconfirmed draft; the workflow has no repository write capability. */
    data class Draft(
        val draft: PlanDraft,
        override val explanation: String,
    ) : LocalPlanningFallback
}

data class PlanningAgentAuditEvent(
    val requestType: PlanningAgentRequestType,
    val contractVersion: String,
    val resultCode: String,
    val elapsedMillis: Long,
    val localValidation: String,
)

/** Implementations must not persist raw task text, feedback, or request payloads. */
fun interface PlanningAgentAuditSink {
    fun record(event: PlanningAgentAuditEvent)
}

object NoOpPlanningAgentAuditSink : PlanningAgentAuditSink {
    override fun record(event: PlanningAgentAuditEvent) = Unit
}

/** The observable local state machine for one bounded planning request. */
sealed interface PlanningAgentState {
    data object Idle : PlanningAgentState

    data class PreparingContext(val requestType: PlanningAgentRequestType) : PlanningAgentState

    data class AwaitingConsent(val requestType: PlanningAgentRequestType) : PlanningAgentState

    data class PreviewingRequest(
        val requestType: PlanningAgentRequestType,
        val request: AiAdvisorRequest,
    ) : PlanningAgentState

    data class RequestingAdvice(val requestType: PlanningAgentRequestType) : PlanningAgentState

    data class ValidatingAdvice(val requestType: PlanningAgentRequestType) : PlanningAgentState

    data class ShowingAdvice(
        val requestType: PlanningAgentRequestType,
        val response: AiAdvisorResponse,
    ) : PlanningAgentState

    data class ShowingDraft(
        val requestType: PlanningAgentRequestType,
        val draft: PlanDraft,
        val explanation: String,
        val isFallback: Boolean,
    ) : PlanningAgentState

    data class UserAccepted(val requestType: PlanningAgentRequestType) : PlanningAgentState

    data class UserDismissed(val requestType: PlanningAgentRequestType) : PlanningAgentState

    data class FailedFallback(
        val requestType: PlanningAgentRequestType,
        val reason: AiAdvisorFailureReason,
        val fallback: LocalPlanningFallback,
    ) : PlanningAgentState
}

internal data class PreparedPlanningAgentWork(
    val work: PlanningAgentWork,
    val request: AiAdvisorRequest,
    val fallback: LocalPlanningFallback,
)

/**
 * Domain-only orchestration: it creates minimized, typed requests, delegates to a
 * replaceable advisor, validates output locally, and can only produce presentation
 * data.  It has no reference to Room repositories, reminders, or task lifecycle APIs.
 */
class PlanningAgent(
    private val advisor: AiAdvisor,
    private val localPlanGenerator: PlanDraftGenerator,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    internal fun prepare(work: PlanningAgentWork): PreparedPlanningAgentWork {
        val request = when (work) {
            is PlanningAgentWork.TaskUnderstanding -> AiRequestFactory.taskUnderstanding(
                work.task, work.executionLogs, work.currentPlan,
            )

            is PlanningAgentWork.DifficultyAndDuration -> AiRequestFactory.difficultyAndDuration(
                work.task, work.executionLogs, work.currentPlan,
            )

            is PlanningAgentWork.TaskBreakdown -> AiRequestFactory.taskBreakdown(
                work.task, work.executionLogs, work.currentPlan,
            )

            is PlanningAgentWork.SortingExplanation -> AiRequestFactory.sortingExplanation(
                work.task, work.executionLogs, work.currentPlan, work.localRankingReasons,
            )

            is PlanningAgentWork.Replan -> AiRequestFactory.replan(
                affectedTasks = work.affectedTasks.filter { it.status.isActive },
                executionLogs = work.executionLogs,
                currentPlan = work.currentPlan,
                constraintSummary = work.constraintSummary,
                hardConstraints = relevantHardConstraints(work),
                protectedManualTaskIds = work.currentPlan
                    ?.takeIf(ConfirmedPlan::hasManualTaskOrder)
                    ?.orderedTaskIds
                    .orEmpty(),
            )

            is PlanningAgentWork.DailySummary -> AiRequestFactory.dailySummary(
                work.day, work.confirmedExecutionLogs,
            )
        }
        return PreparedPlanningAgentWork(work, request, localFallback(work))
    }

    internal suspend fun request(prepared: PreparedPlanningAgentWork): AgentRequestResult {
        val startedAt = nowMillis()
        val providerResult = try {
            advisor.request(prepared.request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            AiAdvisorResult.Failed(AiAdvisorFailureReason.TRANSPORT_FAILURE)
        }
        val elapsed = (nowMillis() - startedAt).coerceAtLeast(0)
        return when (providerResult) {
            is AiAdvisorResult.Advice -> AgentRequestResult.Response(
                response = providerResult.response,
                elapsedMillis = elapsed,
            )

            is AiAdvisorResult.Unavailable -> AgentRequestResult.Failure(providerResult.reason, elapsed)
            is AiAdvisorResult.Failed -> AgentRequestResult.Failure(providerResult.reason, elapsed)
        }
    }

    internal fun validate(
        prepared: PreparedPlanningAgentWork,
        response: AiAdvisorResponse,
    ): AgentValidationResult {
        val contractValidation = AiAdviceValidator.validate(prepared.request, response)
        if (contractValidation is AiAdviceValidation.Invalid) {
            return AgentValidationResult.Invalid(contractValidation.reason)
        }
        if (response is AiPlanDraftAdvice && prepared.work is PlanningAgentWork.Replan) {
            val draft = response.toSafeDraft(prepared.work, localPlanGenerator)
                ?: return AgentValidationResult.Invalid("Plan proposal conflicts with local availability or protected work")
            return AgentValidationResult.Draft(draft, response.explanation)
        }
        return AgentValidationResult.Advice(response)
    }

    internal fun fallback(prepared: PreparedPlanningAgentWork): LocalPlanningFallback = prepared.fallback

    private fun localFallback(work: PlanningAgentWork): LocalPlanningFallback = when (work) {
        is PlanningAgentWork.Replan -> LocalPlanningFallback.Draft(
            draft = localPlanGenerator.generate(work.localPlanInput),
            explanation = "AI 建议不可用，已生成可编辑、未确认的本地确定性规划草案。",
        )

        is PlanningAgentWork.SortingExplanation -> LocalPlanningFallback.Advice(
            explanation = work.localRankingReasons.takeIf(List<String>::isNotEmpty)
                ?.joinToString(separator = "；")
                ?: "AI 建议不可用；排序继续使用本地优先级、截止日期和延期记录。",
        )

        is PlanningAgentWork.DailySummary -> LocalPlanningFallback.Advice(
            explanation = "AI 建议不可用；每日回顾仍只基于你已确认的执行反馈，不会推断任务结果。",
        )

        else -> LocalPlanningFallback.Advice(
            explanation = "AI 建议不可用；你仍可编辑任务信息并使用本地确定性规划。",
        )
    }

    private fun relevantHardConstraints(work: PlanningAgentWork.Replan): List<AiHardConstraintContext> {
        val affectedIds = work.affectedTasks.mapTo(mutableSetOf(), Task::id)
        val tasksById = work.localPlanInput.tasks.associateBy(Task::id)
        val preservedPlanSegments = work.currentPlan?.segments.orEmpty().filter { segment ->
            segment.taskId !in affectedIds ||
                segment.isLocked ||
                tasksById[segment.taskId]?.userPriority == TaskPriority.REQUIRED
        }.map { segment ->
            AiHardConstraintContext(segment.date, segment.startMinute, segment.endMinute)
        }
        val start = LocalDate.now()
        val calendarBlocks = buildList {
            repeat(30) { offset ->
                val date = start.plusDays(offset.toLong())
                work.localPlanInput.weeklyBlocks
                    .filter { block ->
                        block.kind != TimeBlockKind.AVAILABLE &&
                            block.dayOfWeek == date.dayOfWeek &&
                            CourseWeekPattern.appliesOn(
                                block.weekPattern,
                                date,
                                work.localPlanInput.semesterFirstWeekMonday,
                            )
                    }
                    .forEach { block -> add(AiHardConstraintContext(date, block.startMinute, block.endMinute)) }
                work.localPlanInput.dateOverrides
                    .filter { override -> override.date == date && override.type == DateOverrideType.BLOCKED }
                    .forEach { override ->
                        add(AiHardConstraintContext(date, override.startMinute, override.endMinute))
                    }
            }
        }
        return (preservedPlanSegments + calendarBlocks).distinct().take(240)
    }
}

sealed interface AgentRequestResult {
    data class Response(val response: AiAdvisorResponse, val elapsedMillis: Long) : AgentRequestResult

    data class Failure(val reason: AiAdvisorFailureReason, val elapsedMillis: Long) : AgentRequestResult
}

sealed interface AgentValidationResult {
    data class Advice(val response: AiAdvisorResponse) : AgentValidationResult

    data class Draft(val draft: PlanDraft, val explanation: String) : AgentValidationResult

    data class Invalid(val reason: String) : AgentValidationResult
}

/**
 * Stateful coordinator intentionally separated from Compose.  The only action that
 * leaves this workflow is an explicit user confirmation handled by the existing
 * plan repository flow; [markAccepted] itself performs no write.
 */
class PlanningAgentWorkflow(
    private val planningAgent: PlanningAgent,
    private val auditSink: PlanningAgentAuditSink = NoOpPlanningAgentAuditSink,
) {
    private val mutableState = MutableStateFlow<PlanningAgentState>(PlanningAgentState.Idle)
    val state: StateFlow<PlanningAgentState> = mutableState.asStateFlow()
    private var prepared: PreparedPlanningAgentWork? = null

    fun begin(work: PlanningAgentWork, access: PlanningAgentAccess) {
        mutableState.value = PlanningAgentState.PreparingContext(work.type)
        val next = planningAgent.prepare(work)
        prepared = next
        when {
            !access.hasExplicitConsent -> mutableState.value = PlanningAgentState.AwaitingConsent(work.type)
            !access.isEnabled -> fail(next, AiAdvisorFailureReason.DISABLED, 0L, "not_requested")
            else -> mutableState.value = PlanningAgentState.PreviewingRequest(work.type, next.request)
        }
    }

    fun resumeAfterConsent(access: PlanningAgentAccess) {
        val current = prepared ?: return
        when {
            !access.hasExplicitConsent -> mutableState.value = PlanningAgentState.AwaitingConsent(current.work.type)
            !access.isEnabled -> fail(current, AiAdvisorFailureReason.DISABLED, 0L, "not_requested")
            else -> mutableState.value = PlanningAgentState.PreviewingRequest(current.work.type, current.request)
        }
    }

    suspend fun confirmPreview(access: PlanningAgentAccess) {
        val current = prepared ?: return
        if (!access.hasExplicitConsent) {
            mutableState.value = PlanningAgentState.AwaitingConsent(current.work.type)
            return
        }
        if (!access.isEnabled) {
            fail(current, AiAdvisorFailureReason.DISABLED, 0L, "not_requested")
            return
        }
        mutableState.value = PlanningAgentState.RequestingAdvice(current.work.type)
        val requestResult = planningAgent.request(current)
        // A provider adapter is replaceable; do not allow one that ignores coroutine
        // cancellation to publish advice after the user has dismissed the request.
        currentCoroutineContext().ensureActive()
        when (requestResult) {
            is AgentRequestResult.Failure -> fail(
                current,
                requestResult.reason,
                requestResult.elapsedMillis,
                "not_run",
            )

            is AgentRequestResult.Response -> {
                mutableState.value = PlanningAgentState.ValidatingAdvice(current.work.type)
                when (val validation = planningAgent.validate(current, requestResult.response)) {
                    is AgentValidationResult.Advice -> {
                        audit(current, "advice", requestResult.elapsedMillis, "valid")
                        mutableState.value = PlanningAgentState.ShowingAdvice(current.work.type, validation.response)
                    }

                    is AgentValidationResult.Draft -> {
                        audit(current, "draft", requestResult.elapsedMillis, "valid")
                        mutableState.value = PlanningAgentState.ShowingDraft(
                            current.work.type,
                            validation.draft,
                            validation.explanation,
                            isFallback = false,
                        )
                    }

                    is AgentValidationResult.Invalid -> fail(
                        current,
                        AiAdvisorFailureReason.INVALID_RESPONSE,
                        requestResult.elapsedMillis,
                        validation.reason,
                    )
                }
            }
        }
    }

    /** Cancelling never invokes the provider and never replaces a current plan. */
    fun dismiss() {
        val current = prepared ?: return
        audit(current, "dismissed", 0L, "not_run")
        mutableState.value = PlanningAgentState.UserDismissed(current.work.type)
    }

    /** Turning the feature off ends any pending request in a deterministic local state. */
    fun disable() {
        prepared?.let { current ->
            fail(current, AiAdvisorFailureReason.DISABLED, 0L, "not_requested")
        }
    }

    /** This is an acknowledgement only; the PlanRepository remains the sole writer. */
    fun markAccepted() {
        val current = prepared ?: return
        audit(current, "accepted", 0L, "not_run")
        mutableState.value = PlanningAgentState.UserAccepted(current.work.type)
    }

    private fun fail(
        current: PreparedPlanningAgentWork,
        reason: AiAdvisorFailureReason,
        elapsedMillis: Long,
        localValidation: String,
    ) {
        audit(current, "fallback:${reason.name}", elapsedMillis, localValidation)
        mutableState.value = PlanningAgentState.FailedFallback(
            current.work.type,
            reason,
            planningAgent.fallback(current),
        )
    }

    private fun audit(
        current: PreparedPlanningAgentWork,
        resultCode: String,
        elapsedMillis: Long,
        localValidation: String,
    ) {
        auditSink.record(
            PlanningAgentAuditEvent(
                requestType = current.work.type,
                contractVersion = current.request.contractVersion,
                resultCode = resultCode,
                elapsedMillis = elapsedMillis,
                localValidation = localValidation,
            ),
        )
    }
}

private fun AiPlanDraftAdvice.toSafeDraft(
    work: PlanningAgentWork.Replan,
    localPlanGenerator: PlanDraftGenerator,
): PlanDraft? {
    if (proposedSegments.any { !PlanningConstraintValidator.supportsProposal(it, work.localPlanInput) }) {
        return null
    }
    val baseline = localPlanGenerator.generate(work.localPlanInput)
    // The factory aliases affected tasks in this same stable active-task order.
    val activeAffected = work.affectedTasks.filter { it.status.isActive }.distinctBy(Task::id)
    val orderedReferences = activeAffected.mapIndexed { index, task -> "affected-task-${index + 1}" to task.id }.toMap()
    val proposalTaskIds = proposedSegments.mapNotNull { orderedReferences[it.taskReference] }.toSet()
    val tasksById = work.localPlanInput.tasks.associateBy(Task::id)
    val proposalMinutesByTask = proposedSegments.groupingBy { orderedReferences.getValue(it.taskReference) }
        .fold(0) { total, proposal -> total + proposal.endMinute - proposal.startMinute }
    val baselineMinutesByTask = baseline.segments
        .filter { segment -> segment.taskId in proposalTaskIds && !segment.isLocked }
        .groupingBy(PlannedSegment::taskId)
        .fold(0) { total, segment -> total + segment.endMinute - segment.startMinute }
    if (proposalMinutesByTask.any { (taskId, minutes) ->
            tasksById[taskId]?.userPriority == TaskPriority.REQUIRED ||
                minutes != (baselineMinutesByTask[taskId] ?: 0)
        }
    ) return null

    val preserved = baseline.segments.filter { segment ->
        segment.taskId !in proposalTaskIds || segment.isLocked ||
            tasksById[segment.taskId]?.userPriority == TaskPriority.REQUIRED
    }
    val mappedProposals = proposedSegments.map { proposal ->
        PlannedSegment(
            id = "agent:${proposal.taskReference}:${proposal.date.toEpochDay()}:${proposal.startMinute}:${proposal.endMinute}",
            taskId = orderedReferences.getValue(proposal.taskReference),
            date = proposal.date,
            startMinute = proposal.startMinute,
            endMinute = proposal.endMinute,
        )
    }
    if (mappedProposals.any { proposal ->
            preserved.any { existing ->
                proposal.date == existing.date &&
                    proposal.startMinute < existing.endMinute && proposal.endMinute > existing.startMinute
            }
        } || hasOverlap(mappedProposals)
    ) return null
    val proposedOrderIds = orderedTaskReferences.mapNotNull(orderedReferences::get)
    val safeOrder = if (work.currentPlan?.hasManualTaskOrder == true) {
        baseline.orderedTaskIds
    } else {
        proposedOrderIds + baseline.orderedTaskIds.filter { it !in proposedOrderIds }
    }
    return baseline.copy(
        generatedAt = LocalDateTime.now(),
        segments = (preserved + mappedProposals).sortedWith(
            compareBy(PlannedSegment::date, PlannedSegment::startMinute, PlannedSegment::endMinute, PlannedSegment::taskId),
        ),
        orderedTaskIds = safeOrder,
    )
}

private fun hasOverlap(segments: List<PlannedSegment>): Boolean = segments.indices.any { firstIndex ->
    (firstIndex + 1 until segments.size).any { secondIndex ->
        val first = segments[firstIndex]
        val second = segments[secondIndex]
        first.date == second.date && first.startMinute < second.endMinute && first.endMinute > second.startMinute
    }
}
