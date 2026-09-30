package com.swan1127.repland.ui.engagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.EngagementMode
import com.swan1127.repland.domain.ports.EngagementRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EngagementViewModel(private val repository: EngagementRepository) : ViewModel() {
    val mode: StateFlow<EngagementMode> = repository.observeMode().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), EngagementMode.GUIDED,
    )

    fun setMode(mode: EngagementMode) { viewModelScope.launch { repository.setMode(mode) } }
    fun recordTimelineOpened(entryId: String) {
        viewModelScope.launch { repository.recordTimelineOpened(entryId) }
    }

    class Factory(private val repository: EngagementRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(EngagementViewModel::class.java))
            return EngagementViewModel(repository) as T
        }
    }
}
