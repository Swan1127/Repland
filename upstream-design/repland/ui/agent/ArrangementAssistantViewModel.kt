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

data class ArrangementAssistantAccessState(
    val isEnabled: Boolean = false,
    val hasExplicitConsent: Boolean = false,
)

class ArrangementAssistantViewModel(
    private val settingsRepository: AiSettingsRepository,
    private val advisor: ArrangementAssistantAdvisor,
) : ViewModel() {
    val access: StateFlow<ArrangementAssistantAccessState> = settingsRepository.observe()
        .map { ArrangementAssistantAccessState(it.isEnabled, it.hasExplicitConsent) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArrangementAssistantAccessState())

    suspend fun refine(
        utterance: String,
        date: LocalDate,
        occupiedEntries: List<TimelineEntry>,
    ): ArrangementAssistantAdviceResult {
        val currentAccess = access.value
        if (!currentAccess.isEnabled || !currentAccess.hasExplicitConsent) {
            return ArrangementAssistantAdviceResult.Unavailable(AiAdvisorFailureReason.DISABLED)
        }
        return advisor.refine(
            ArrangementAssistantAdviceRequest(
                utterance = utterance,
                date = date,
                occupiedIntervals = occupiedEntries.map {
                    ArrangementOccupiedInterval(
                        title = it.title,
                        startMinute = it.startMinute,
                        endMinute = it.endMinute,
                        trackId = it.trackId,
                        isHardBusy = it.kind in setOf(TimelineKind.COURSE, TimelineKind.REST, TimelineKind.COMMITMENT),
                    )
                },
            ),
        )
    }

    class Factory(
        private val settingsRepository: AiSettingsRepository,
        private val advisor: ArrangementAssistantAdvisor,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(ArrangementAssistantViewModel::class.java))
            return ArrangementAssistantViewModel(settingsRepository, advisor) as T
        }
    }
}
