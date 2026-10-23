package com.swan1127.repland.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.ReminderPreferences
import com.swan1127.repland.domain.ports.ReminderSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren

data class ReminderSettingsUiState(
    val preferences: ReminderPreferences = ReminderPreferences(),
    val isLoading: Boolean = true,
)

class ReminderSettingsViewModel(
    private val repository: ReminderSettingsRepository,
) : ViewModel() {
    private val mutationJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private val mutationScope = CoroutineScope(viewModelScope.coroutineContext + mutationJob)
    fun cancelPendingMutations() = mutationJob.cancelChildren()

    val uiState: StateFlow<ReminderSettingsUiState> = repository.observe()
        .map { preferences -> ReminderSettingsUiState(preferences = preferences, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReminderSettingsUiState(),
        )

    fun setEnabled(enabled: Boolean) {
        mutationScope.launch { repository.setEnabled(enabled) }
    }

    class Factory(
        private val repository: ReminderSettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(ReminderSettingsViewModel::class.java))
            return ReminderSettingsViewModel(repository) as T
        }
    }
}
