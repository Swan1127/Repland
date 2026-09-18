package com.swan1127.repland.ui.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.CategoryPreferences
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.ports.CategoryPreferenceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryPreferenceUiState(
    val weights: Map<TaskCategory, Int> = CategoryPreferences.defaults,
    val isLoading: Boolean = true,
)

class CategoryPreferenceViewModel(
    private val repository: CategoryPreferenceRepository,
) : ViewModel() {
    val uiState: StateFlow<CategoryPreferenceUiState> = repository.observe()
        .map { weights -> CategoryPreferenceUiState(weights = weights, isLoading = false) }
        .catch { emit(CategoryPreferenceUiState(isLoading = false)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoryPreferenceUiState(),
        )

    fun save(weights: Map<TaskCategory, Int>) {
        if (!CategoryPreferences.isValid(weights)) return
        viewModelScope.launch { repository.save(weights) }
    }

    class Factory(
        private val repository: CategoryPreferenceRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(CategoryPreferenceViewModel::class.java))
            return CategoryPreferenceViewModel(repository) as T
        }
    }
}
