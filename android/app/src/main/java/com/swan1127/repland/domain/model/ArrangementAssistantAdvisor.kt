package com.swan1127.repland.domain.model

import java.time.LocalDate

/** The small, inspectable payload permitted for an explicit assistant refinement. */
data class ArrangementAssistantAdviceRequest(
    val utterance: String,
    val date: LocalDate,
    val occupiedIntervals: List<ArrangementOccupiedInterval>,
    val existingTasks: List<ArrangementExistingTask> = emptyList(),
    val draftCandidates: List<ArrangementCandidate> = emptyList(),
    val followUpInstruction: String? = null,
    val availableIntervals: List<ArrangementAvailableInterval> = emptyList(),
    val categoryPreferences: Map<TaskCategory, Int> = emptyMap(),
    val taskFeedback: List<ArrangementTaskFeedback> = emptyList(),
    /** Local-only stale-source guard; never serialized to the provider. */
    val sourceRevision: String? = null,
    val allowedOperations: Set<ArrangementAdviceOperation> = setOf(ArrangementAdviceOperation.PROPOSE_CHANGES),
)

data class ArrangementAvailableInterval(val startMinute: Int, val endMinute: Int)

/** Only explicitly declared free time; an empty day is not an all-day availability claim. */
object ArrangementAvailability {
    fun forDay(input: PlanGenerationInput, date: LocalDate, now: java.time.LocalDateTime): List<ArrangementAvailableInterval> {
        if (date < now.toLocalDate()) return emptyList()
        val first = if (date == now.toLocalDate()) now.hour * 60 + now.minute + 1 else 0
        val result = mutableListOf<ArrangementAvailableInterval>()
        var start: Int? = null
        for (minute in first..1440) {
            val free = minute < 1440 && PlanningConstraintValidator.isAvailable(date, minute, minute + 1,
                input.weeklyBlocks, input.dateOverrides, input.semesterFirstWeekMonday)
            if (free && start == null) start = minute
            if (!free && start != null) { result += ArrangementAvailableInterval(start, minute); start = null }
        }
        return result
    }
}

data class ArrangementExistingTask(val id: String, val title: String, val category: TaskCategory, val durationMinutes: Int?,
    val status: TaskStatus? = null, val priority: TaskPriority? = null, val dueDate: LocalDate? = null,
    val scheduledForDate: LocalDate? = null, val progressPercent: Int? = null, val postponeCount: Int? = null,
    val inputSources: TaskInputSources? = null)

data class ArrangementTaskFeedback(val taskId: String, val feedback: List<AiFeedbackContext>)

data class ArrangementOccupiedInterval(
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val trackId: String,
    /** Courses, commitments and rest are protected when the advisor proposes time. */
    val isHardBusy: Boolean,
    val taskId: String? = null,
)

data class ArrangementAssistantAdvice(
    val candidates: List<ArrangementCandidate>,
    val confidenceLabel: String,
    val operation: ArrangementAdviceOperation = ArrangementAdviceOperation.PROPOSE_CHANGES,
    val queryScope: TaskQueryScope? = null,
    val taskReference: String? = null,
)

enum class ArrangementAdviceOperation { PROPOSE_CHANGES, QUERY_TASKS, FORMULATE_PLAN, EXPLAIN_ORDER }

sealed interface ArrangementAssistantAdviceResult {
    data class Advice(val advice: ArrangementAssistantAdvice) : ArrangementAssistantAdviceResult
    data class Unavailable(val reason: AiAdvisorFailureReason) : ArrangementAssistantAdviceResult
    data class Failed(val reason: AiAdvisorFailureReason) : ArrangementAssistantAdviceResult
}

/**
 * Optional language and placement refinement. It receives only the current
 * utterance, bounded task facts/current draft and the selected day's occupied intervals, cannot write a plan, and its
 * proposed slots still go through the local collision check and confirmation.
 */
interface ArrangementAssistantAdvisor {
    suspend fun refine(request: ArrangementAssistantAdviceRequest): ArrangementAssistantAdviceResult
}

object NoOpArrangementAssistantAdvisor : ArrangementAssistantAdvisor {
    override suspend fun refine(request: ArrangementAssistantAdviceRequest): ArrangementAssistantAdviceResult =
        ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.SERVICE_NOT_CONFIGURED)
}

object ArrangementAssistantAdviceValidator {
    fun validate(advice: ArrangementAssistantAdvice, request: ArrangementAssistantAdviceRequest): ArrangementAssistantAdvice? {
        if (advice.operation !in request.allowedOperations) return null
        if (advice.operation != ArrangementAdviceOperation.PROPOSE_CHANGES) {
            if (advice.candidates.isNotEmpty() || !request.followUpInstruction.isNullOrBlank() || request.draftCandidates.isNotEmpty()) return null
            return when (advice.operation) {
                ArrangementAdviceOperation.QUERY_TASKS -> advice.takeIf { it.queryScope != null && it.taskReference == null }
                ArrangementAdviceOperation.FORMULATE_PLAN -> advice.takeIf { it.queryScope == null && it.taskReference == null }
                ArrangementAdviceOperation.EXPLAIN_ORDER -> advice.takeIf { it.queryScope == null && request.existingTasks.any { task -> task.id == it.taskReference } }
                else -> null
            }
        }
        val references = advice.candidates.mapNotNull { it.existingTaskId }
        if (references.distinct().size != references.size || references.any { id -> request.existingTasks.none { it.id == id } }) return null
        val proposalIds = advice.candidates.mapNotNull { it.proposalId }
        if (proposalIds.distinct().size != proposalIds.size || advice.candidates.any { candidate ->
            candidate.proposalId != null && request.draftCandidates.none { it.proposalId == candidate.proposalId && it.existingTaskId == candidate.existingTaskId }
        }) return null
        val checked = validate(advice) ?: return null
        if (!request.followUpInstruction.isNullOrBlank() && checked.candidates.size != advice.candidates.size) return null
        val explicitStarts = ArrangementAssistantInterpreter.interpret(
            listOfNotNull(request.utterance, request.followUpInstruction).joinToString("；")
        ).candidates.mapNotNull { it.timeHint.explicitStartMinute }.toSet()
        return checked.copy(candidates = checked.candidates.map { candidate ->
            val start = candidate.timeHint.explicitStartMinute
            val duration = candidate.durationMinutes
            val explicit = candidate.placementSource == ArrangementPlacementSource.USER_EXPLICIT &&
                (start in explicitStarts || request.draftCandidates.any { candidate.proposalId != null && it.proposalId == candidate.proposalId &&
                    it.placementSource == ArrangementPlacementSource.USER_EXPLICIT && it.timeHint.explicitStartMinute == start })
            if (!explicit && start != null &&
                (duration == null || request.availableIntervals.none { start >= it.startMinute && start + duration <= it.endMinute })) {
                candidate.copy(timeHint = ArrangementTimeHint(windowLabel = candidate.timeHint.windowLabel),
                    placementSource = ArrangementPlacementSource.UNSCHEDULED,
                    needsClarification = candidate.needsClarification + ArrangementClarification.TIME)
            } else if (start != null && !explicit) candidate.copy(placementSource = ArrangementPlacementSource.AI_SUGGESTED)
            else candidate
        })
    }
    fun validate(advice: ArrangementAssistantAdvice): ArrangementAssistantAdvice? {
        val validCandidates = advice.candidates
            .filter { candidate ->
                candidate.title.isNotBlank() &&
                (candidate.durationMinutes == null || candidate.durationMinutes in 5..720) &&
                    (candidate.timeHint.explicitStartMinute == null || candidate.timeHint.explicitStartMinute in 0 until 1_440) &&
                    (candidate.timeHint.explicitStartMinute == null || candidate.durationMinutes == null ||
                        candidate.timeHint.explicitStartMinute + candidate.durationMinutes <= 1_440)
            }
            .take(8)
        return advice.copy(candidates = validCandidates).takeIf { it.candidates.isNotEmpty() }
    }
}
