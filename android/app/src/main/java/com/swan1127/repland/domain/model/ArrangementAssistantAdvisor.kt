package com.swan1127.repland.domain.model

import java.time.LocalDate

/** The small, inspectable payload permitted for an explicit assistant refinement. */
data class ArrangementAssistantAdviceRequest(
    val utterance: String,
    val date: LocalDate,
    val occupiedIntervals: List<ArrangementOccupiedInterval>,
    val existingTasks: List<ArrangementExistingTask> = emptyList(),
    val draftCandidates: List<ArrangementCandidate> = emptyList(),
)

data class ArrangementExistingTask(val id: String, val title: String, val category: TaskCategory, val durationMinutes: Int?)

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
)

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
        val references = advice.candidates.mapNotNull { it.existingTaskId }
        if (references.distinct().size != references.size || references.any { id -> request.existingTasks.none { it.id == id } }) return null
        return validate(advice)
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
