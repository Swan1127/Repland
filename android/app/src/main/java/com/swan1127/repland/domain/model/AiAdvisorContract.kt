package com.swan1127.repland.domain.model

import java.time.LocalDate
import java.util.UUID

/**
 * Versioned boundary for an optional, replaceable AI provider. Requests carry only
 * the user-selected task and its relevant structural context; they contain no
 * account data, complete task history, profile evidence, or state-changing command.
 */
const val AI_ADVISOR_CONTRACT_VERSION = "repland-ai-advisor/v1"

data class AiTaskContext(
    /** Request-local reference, not a Room task ID. */
    val reference: String,
    val title: String,
    val description: String,
    val category: TaskCategory,
    val initialPriority: TaskPriority,
    val currentStatus: TaskStatus,
    val estimatedDays: Int,
    val expectedDurationMinutes: Int?,
    val dueDate: LocalDate?,
    val recentFeedback: List<AiFeedbackContext>,
    val confirmedSegments: List<AiSegmentContext>,
)

data class AiFeedbackContext(
    val actualDurationMinutes: Int?,
    val progressPercent: Int?,
    val completedContent: String?,
    val completionResult: String?,
    val postponeReason: String?,
)

data class AiSegmentContext(
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val isLocked: Boolean,
)

/** A hard time constraint supplied only when it is relevant to a replan request. */
data class AiHardConstraintContext(
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
)

sealed interface AiAdvisorRequest {
    val contractVersion: String
    val requestId: String
}

data class AiTaskUnderstandingRequest(
    override val contractVersion: String = AI_ADVISOR_CONTRACT_VERSION,
    override val requestId: String = UUID.randomUUID().toString(),
    val task: AiTaskContext,
) : AiAdvisorRequest

data class AiDifficultyAndDurationRequest(
    override val contractVersion: String = AI_ADVISOR_CONTRACT_VERSION,
    override val requestId: String = UUID.randomUUID().toString(),
    val task: AiTaskContext,
) : AiAdvisorRequest

data class AiTaskBreakdownRequest(
    override val contractVersion: String = AI_ADVISOR_CONTRACT_VERSION,
    override val requestId: String = UUID.randomUUID().toString(),
    val task: AiTaskContext,
) : AiAdvisorRequest

data class AiSortingExplanationRequest(
    override val contractVersion: String = AI_ADVISOR_CONTRACT_VERSION,
    override val requestId: String = UUID.randomUUID().toString(),
    val task: AiTaskContext,
    val localRankingReasons: List<String>,
) : AiAdvisorRequest

/** Only affected tasks and the concrete constraint summary belong in a future replan request. */
data class AiReplanRequest(
    override val contractVersion: String = AI_ADVISOR_CONTRACT_VERSION,
    override val requestId: String = UUID.randomUUID().toString(),
    val affectedTasks: List<AiTaskContext>,
    val constraintSummary: List<String>,
    val hardConstraints: List<AiHardConstraintContext> = emptyList(),
    /** A user-created order is not an AI-editable input without a separate scoped authorization. */
    val protectedManualOrderReferences: List<String> = emptyList(),
) : AiAdvisorRequest

data class AiDailySummaryRequest(
    override val contractVersion: String = AI_ADVISOR_CONTRACT_VERSION,
    override val requestId: String = UUID.randomUUID().toString(),
    val day: LocalDate,
    val confirmedFeedback: List<AiFeedbackContext>,
) : AiAdvisorRequest

sealed interface AiAdvisorResponse {
    val contractVersion: String
    val requestId: String
}

data class AiTaskUnderstandingAdvice(
    override val contractVersion: String,
    override val requestId: String,
    val summary: String,
    val dependencyNote: String? = null,
) : AiAdvisorResponse

data class AiDifficultyAndDurationAdvice(
    override val contractVersion: String,
    override val requestId: String,
    val difficulty: Int,
    val suggestedDurationMinutes: Int?,
    val explanation: String,
) : AiAdvisorResponse

data class AiTaskBreakdownAdvice(
    override val contractVersion: String,
    override val requestId: String,
    val steps: List<String>,
    val explanation: String,
) : AiAdvisorResponse

data class AiSortingExplanationAdvice(
    override val contractVersion: String,
    override val requestId: String,
    val explanation: String,
) : AiAdvisorResponse

/** A proposal only. It has no task-status field and cannot apply itself to Room. */
data class AiPlanDraftAdvice(
    override val contractVersion: String,
    override val requestId: String,
    val orderedTaskReferences: List<String>,
    val proposedSegments: List<AiProposedSegment>,
    val explanation: String,
) : AiAdvisorResponse

data class AiProposedSegment(
    val taskReference: String,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
)

data class AiDailySummaryAdvice(
    override val contractVersion: String,
    override val requestId: String,
    val summary: String,
    val suggestedNextStep: String? = null,
) : AiAdvisorResponse

sealed interface AiAdvisorResult {
    data class Advice(val response: AiAdvisorResponse) : AiAdvisorResult

    data class Unavailable(val reason: AiAdvisorFailureReason) : AiAdvisorResult

    data class Failed(val reason: AiAdvisorFailureReason) : AiAdvisorResult
}

enum class AiAdvisorFailureReason {
    DISABLED,
    SERVICE_NOT_CONFIGURED,
    TRANSPORT_FAILURE,
    TIMEOUT,
    INVALID_RESPONSE,
}

interface AiAdvisor {
    suspend fun request(request: AiAdvisorRequest): AiAdvisorResult
}

/** ADR 0001 default: the APK performs no AI network call until a provider is supplied. */
object NoOpAiAdvisor : AiAdvisor {
    override suspend fun request(request: AiAdvisorRequest): AiAdvisorResult =
        AiAdvisorResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
}

sealed interface AiAdviceValidation {
    data class Valid(val response: AiAdvisorResponse) : AiAdviceValidation

    data class Invalid(val reason: String) : AiAdviceValidation
}

/** Rejects malformed, mismatched, or over-broad provider output before the UI can show it. */
object AiAdviceValidator {
    fun validate(
        request: AiAdvisorRequest,
        response: AiAdvisorResponse,
    ): AiAdviceValidation {
        if (request.contractVersion != AI_ADVISOR_CONTRACT_VERSION ||
            response.contractVersion != AI_ADVISOR_CONTRACT_VERSION
        ) {
            return AiAdviceValidation.Invalid("Unsupported contract version")
        }
        if (request.requestId != response.requestId) {
            return AiAdviceValidation.Invalid("Response does not belong to this request")
        }
        return when {
            request is AiTaskUnderstandingRequest && response is AiTaskUnderstandingAdvice ->
                validateTextAdvice(response.summary, response, response.dependencyNote)

            request is AiDifficultyAndDurationRequest && response is AiDifficultyAndDurationAdvice ->
                if (response.difficulty !in 1..5 ||
                    response.suggestedDurationMinutes?.let { it !in 1..1_440 } == true
                ) {
                    AiAdviceValidation.Invalid("Difficulty or duration is outside the accepted range")
                } else {
                    validateTextAdvice(response.explanation, response)
                }

            request is AiTaskBreakdownRequest && response is AiTaskBreakdownAdvice ->
                if (response.steps.isEmpty() || response.steps.any { !isValidText(it) }) {
                    AiAdviceValidation.Invalid("A task breakdown needs valid steps")
                } else {
                    validateTextAdvice(response.explanation, response)
                }

            request is AiSortingExplanationRequest && response is AiSortingExplanationAdvice ->
                validateTextAdvice(response.explanation, response)

            request is AiReplanRequest && response is AiPlanDraftAdvice -> validatePlanAdvice(request, response)

            request is AiDailySummaryRequest && response is AiDailySummaryAdvice ->
                validateTextAdvice(response.summary, response, response.suggestedNextStep)

            else -> AiAdviceValidation.Invalid("Response type is not allowed for this request")
        }
    }

    private fun validatePlanAdvice(
        request: AiReplanRequest,
        response: AiPlanDraftAdvice,
    ): AiAdviceValidation {
        val allowedReferences = request.affectedTasks.map(AiTaskContext::reference).toSet()
        val validSegmentShape = response.proposedSegments.all { segment ->
            segment.taskReference in allowedReferences &&
                segment.startMinute in 0 until 24 * 60 &&
                segment.endMinute in 1..24 * 60 &&
                segment.startMinute < segment.endMinute &&
                segment.startMinute % 30 == 0 &&
                segment.endMinute % 30 == 0
        }
        val hasGeneratedOverlap = response.proposedSegments.anyIndexedPair { first, second ->
            first.date == second.date && first.startMinute < second.endMinute && first.endMinute > second.startMinute
        }
        val overlapsHardConstraint = response.proposedSegments.any { proposal ->
            request.hardConstraints.any { constraint ->
                proposal.date == constraint.date &&
                    proposal.startMinute < constraint.endMinute && proposal.endMinute > constraint.startMinute
            }
        }
        val lockedSegments = request.affectedTasks.flatMap { task ->
            task.confirmedSegments.filter(AiSegmentContext::isLocked).map { segment -> task to segment }
        }
        val movesLockedSegment = response.proposedSegments.any { proposal ->
            lockedSegments.any { (task, locked) ->
                proposal.date == locked.date &&
                    proposal.startMinute < locked.endMinute && proposal.endMinute > locked.startMinute &&
                    (proposal.taskReference != task.reference ||
                        proposal.startMinute != locked.startMinute || proposal.endMinute != locked.endMinute)
            }
        }
        val changesRequiredPlacement = response.proposedSegments.any { proposal ->
            request.affectedTasks
                .firstOrNull { it.reference == proposal.taskReference }
                ?.takeIf { it.initialPriority == TaskPriority.REQUIRED }
                ?.confirmedSegments
                ?.none { fixed ->
                    proposal.date == fixed.date &&
                        proposal.startMinute == fixed.startMinute && proposal.endMinute == fixed.endMinute
                } == true
        }
        val changesManualOrder = request.protectedManualOrderReferences.isNotEmpty() &&
            response.orderedTaskReferences.isNotEmpty() &&
            response.orderedTaskReferences != request.protectedManualOrderReferences
        return if (
            !validSegmentShape ||
            response.orderedTaskReferences.size > allowedReferences.size ||
            response.orderedTaskReferences.distinct().size != response.orderedTaskReferences.size ||
            response.proposedSegments.size > 120 ||
            hasGeneratedOverlap ||
            overlapsHardConstraint ||
            movesLockedSegment ||
            changesRequiredPlacement ||
            changesManualOrder ||
            response.orderedTaskReferences.any { it !in allowedReferences }
        ) {
            AiAdviceValidation.Invalid("Plan proposal references an unrelated task or invalid segment")
        } else {
            validateTextAdvice(response.explanation, response)
        }
    }

    private fun validateTextAdvice(
        requiredText: String,
        response: AiAdvisorResponse,
        vararg optionalTexts: String?,
    ): AiAdviceValidation = if (
        isValidText(requiredText) && optionalTexts.all { text -> text == null || isValidText(text) }
    ) {
        AiAdviceValidation.Valid(response)
    } else {
        AiAdviceValidation.Invalid("Advice text is empty or too long")
    }

    private fun isValidText(value: String): Boolean = value.trim().length in 1..2_000
}

private fun <T> List<T>.anyIndexedPair(predicate: (T, T) -> Boolean): Boolean =
    indices.any { firstIndex ->
        (firstIndex + 1..lastIndex).any { secondIndex -> predicate(this[firstIndex], this[secondIndex]) }
    }

/** Builds the smallest task-scoped request shown to the user before any provider call. */
object AiRequestFactory {
    private const val MAX_RECENT_FEEDBACK = 5

    fun taskUnderstanding(
        task: Task,
        executionLogs: List<TaskExecutionLog>,
        currentPlan: ConfirmedPlan?,
    ): AiTaskUnderstandingRequest {
        return AiTaskUnderstandingRequest(
            task = taskContext("current-task", task, executionLogs, currentPlan),
        )
    }

    fun difficultyAndDuration(
        task: Task,
        executionLogs: List<TaskExecutionLog>,
        currentPlan: ConfirmedPlan?,
    ): AiDifficultyAndDurationRequest = AiDifficultyAndDurationRequest(
        task = taskContext("current-task", task, executionLogs, currentPlan),
    )

    fun taskBreakdown(
        task: Task,
        executionLogs: List<TaskExecutionLog>,
        currentPlan: ConfirmedPlan?,
    ): AiTaskBreakdownRequest = AiTaskBreakdownRequest(
        task = taskContext("current-task", task, executionLogs, currentPlan),
    )

    fun sortingExplanation(
        task: Task,
        executionLogs: List<TaskExecutionLog>,
        currentPlan: ConfirmedPlan?,
        localRankingReasons: List<String>,
    ): AiSortingExplanationRequest = AiSortingExplanationRequest(
        task = taskContext("current-task", task, executionLogs, currentPlan),
        localRankingReasons = localRankingReasons.filter(String::isNotBlank).take(12),
    )

    fun replan(
        affectedTasks: List<Task>,
        executionLogs: List<TaskExecutionLog>,
        currentPlan: ConfirmedPlan?,
        constraintSummary: List<String>,
        hardConstraints: List<AiHardConstraintContext>,
        protectedManualTaskIds: List<String>,
    ): AiReplanRequest {
        val tasks = affectedTasks.distinctBy(Task::id).take(50)
        val referenceByTaskId = tasks.mapIndexed { index, task -> task.id to "affected-task-${index + 1}" }.toMap()
        return AiReplanRequest(
            affectedTasks = tasks.map { task ->
                taskContext(referenceByTaskId.getValue(task.id), task, executionLogs, currentPlan)
            },
            constraintSummary = constraintSummary.filter(String::isNotBlank).take(20),
            hardConstraints = hardConstraints.distinct().take(240),
            protectedManualOrderReferences = protectedManualTaskIds.mapNotNull(referenceByTaskId::get),
        )
    }

    fun dailySummary(
        day: LocalDate,
        executionLogs: List<TaskExecutionLog>,
    ): AiDailySummaryRequest = AiDailySummaryRequest(
        day = day,
        confirmedFeedback = effectiveFeedback(executionLogs).take(MAX_RECENT_FEEDBACK),
    )

    private fun taskContext(
        reference: String,
        task: Task,
        executionLogs: List<TaskExecutionLog>,
        currentPlan: ConfirmedPlan?,
    ): AiTaskContext = AiTaskContext(
        reference = reference,
        title = task.displayName,
        description = task.description,
        category = task.category,
        initialPriority = task.userPriority,
        currentStatus = task.status,
        estimatedDays = task.estimatedDays,
        expectedDurationMinutes = task.totalDurationMinutes,
        dueDate = task.dueDate,
        recentFeedback = effectiveFeedback(executionLogs.filter { it.taskId == task.id }),
        confirmedSegments = currentPlan?.segments
            ?.filter { it.taskId == task.id }
            ?.map { segment ->
                AiSegmentContext(
                    date = segment.date,
                    startMinute = segment.startMinute,
                    endMinute = segment.endMinute,
                    isLocked = segment.isLocked,
                )
            }
            .orEmpty(),
    )

    /** A correction becomes the only outbound version of the feedback it corrects. */
    private fun effectiveFeedback(executionLogs: List<TaskExecutionLog>): List<AiFeedbackContext> {
        val correctedLogIds = executionLogs.mapNotNull(TaskExecutionLog::correctedLogId).toSet()
        return executionLogs
            .asReversed()
            .asSequence()
            .filter { log -> log.id !in correctedLogIds }
            .filter { TaskLifecycleValidator.hasFeedback(it.feedback) }
            .take(MAX_RECENT_FEEDBACK)
            .map { log ->
                AiFeedbackContext(
                    actualDurationMinutes = log.feedback.actualDurationMinutes,
                    progressPercent = log.feedback.progressPercent,
                    completedContent = log.feedback.completedContent,
                    completionResult = log.feedback.completionResult,
                    postponeReason = log.feedback.postponeReason,
                )
            }
            .toList()
    }
}
