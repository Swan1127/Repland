package com.swan1127.repland.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.ProfileEvidence
import com.swan1127.repland.domain.model.ProfileEvidenceValidator
import com.swan1127.repland.domain.ports.ProfileEvidenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileEvidenceUiState(
    val evidence: List<ProfileEvidence> = emptyList(),
    val isLoading: Boolean = true,
    val hasActionError: Boolean = false,
)

class ProfileEvidenceViewModel(
    private val repository: ProfileEvidenceRepository,
) : ViewModel() {
    private val actionError = MutableStateFlow(false)

    val uiState: StateFlow<ProfileEvidenceUiState> = repository.observe()
        .combine(actionError) { evidence, error ->
            ProfileEvidenceUiState(evidence = evidence, isLoading = false, hasActionError = error)
        }
        .catch { emit(ProfileEvidenceUiState(isLoading = false, hasActionError = true)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileEvidenceUiState(),
        )

    fun generate() = action { repository.generateFromConfirmedFeedback() }

    fun updateConclusion(id: String, conclusion: String) {
        if (!ProfileEvidenceValidator.isValidConclusion(conclusion)) {
            actionError.value = true
            return
        }
        action { repository.updateConclusion(id, conclusion) }
    }

    fun delete(id: String) = action { repository.delete(id) }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { actionError.value = false }
                .onFailure { actionError.value = true }
        }
    }

    class Factory(
        private val repository: ProfileEvidenceRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(ProfileEvidenceViewModel::class.java))
            return ProfileEvidenceViewModel(repository) as T
        }
    }
}
