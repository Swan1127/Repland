package com.swan1127.repland.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceResult
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceRequest
import com.swan1127.repland.domain.model.ArrangementAssistantAdvisor
import com.swan1127.repland.domain.model.ArrangementOccupiedInterval
import com.swan1127.repland.domain.model.TimelineEntry
import com.swan1127.repland.domain.model.TimelineKind
import com.swan1127.repland.domain.ports.AiSettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import com.swan1127.repland.domain.model.ArrangementAssistantAdviceValidator

data class ArrangementAssistantAccessState(
    val isEnabled: Boolean = false,
    val hasExplicitConsent: Boolean = false,
)

class ArrangementAssistantViewModel(
    private val settingsRepository: AiSettingsRepository,
    private val advisor: ArrangementAssistantAdvisor,
    private val reads: com.swan1127.repland.domain.model.PlanningReadService? = null,
) : ViewModel() {
    val access: StateFlow<ArrangementAssistantAccessState> = settingsRepository.observe()
        .map { ArrangementAssistantAccessState(it.isEnabled, it.hasExplicitConsent) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArrangementAssistantAccessState())

    suspend fun refine(
        utterance: String,
        date: LocalDate,
        occupiedEntries: List<TimelineEntry>,
    ): ArrangementAssistantAdviceResult {
        return refine(ArrangementAssistantAdviceRequest(utterance, date, occupiedEntries.map {
            ArrangementOccupiedInterval(it.title, it.startMinute, it.endMinute, it.trackId,
                it.kind in setOf(TimelineKind.COURSE, TimelineKind.REST, TimelineKind.COMMITMENT), it.taskId)
        }))
    }

    suspend fun refine(request: ArrangementAssistantAdviceRequest): ArrangementAssistantAdviceResult {
        val currentAccess = settingsRepository.observe().first()
        if (!currentAccess.isEnabled || !currentAccess.hasExplicitConsent) {
            return ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.DISABLED)
        }
        if (request.existingTasks.size > 50 || request.draftCandidates.size > 8) return ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE)
        val source = try { reads?.snapshot() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { return ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.CONTEXT_UNAVAILABLE) }
        if (source != null && !request.sourceRevision.isNullOrBlank() && request.sourceRevision != source.revision)
            return ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.SOURCE_CHANGED)
        val prepared = try { if (source == null) request else requireNotNull(reads).prepare(request, source) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { return ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.CONTEXT_UNAVAILABLE) }
        if (prepared.draftCandidates.any { it.existingTaskId != null && prepared.existingTasks.none { task -> task.id == it.existingTaskId } })
            return ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.SOURCE_CHANGED)
        val result = advisor.refine(prepared)
        val latest = settingsRepository.observe().first()
        if (!latest.isEnabled || !latest.hasExplicitConsent) return ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.DISABLED)
        if (source != null) {
            val revision = try { requireNotNull(reads).snapshot().revision } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { return ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.CONTEXT_UNAVAILABLE) }
            if (source.revision != revision) return ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.SOURCE_CHANGED)
        }
        return if (result is ArrangementAssistantAdviceResult.Advice) {
            ArrangementAssistantAdviceValidator.validate(result.advice, prepared)?.let { ArrangementAssistantAdviceResult.Advice(it) }
                ?: ArrangementAssistantAdviceResult.Failed(AiAdvisorFailureReason.INVALID_RESPONSE)
        } else result
    }

    class Factory(
        private val settingsRepository: AiSettingsRepository,
        private val advisor: ArrangementAssistantAdvisor,
        private val reads: com.swan1127.repland.domain.model.PlanningReadService? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(ArrangementAssistantViewModel::class.java))
            return ArrangementAssistantViewModel(settingsRepository, advisor, reads) as T
        }
    }
}
