package com.swan1127.repland.ui.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.domain.model.CategoryPreferences
import com.swan1127.repland.domain.model.TaskCategory
import com.swan1127.repland.domain.ports.CategoryPreferenceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryPreferenceUiState(
    val weights: Map<TaskCategory, Int> = CategoryPreferences.defaults,
    val isLoading: Boolean = true,
    val hasLoaded: Boolean = false,
    val readError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val saveReceipt: String? = null,
) {
    val isTrusted get() = hasLoaded && !isLoading && readError == null
}

private data class PreferenceSaveState(val busy: Boolean = false, val error: String? = null, val receipt: String? = null)

class CategoryPreferenceViewModel(
    private val repository: CategoryPreferenceRepository,
) : ViewModel() {
    private val readRetries = MutableStateFlow(0)
    private val saveState = MutableStateFlow(PreferenceSaveState())
    val uiState: StateFlow<CategoryPreferenceUiState> = com.swan1127.repland.ui.state.recoverableRead(
        initial = CategoryPreferences.defaults, retries = readRetries, errorMessage = "类别偏好读取失败；未重置你的权重，请重试。",
        source = repository::observe,
    ).combine(saveState) { read, save -> CategoryPreferenceUiState(read.value, read.isLoading, read.hasLoaded, read.error,
        save.busy, save.error, save.receipt) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoryPreferenceUiState(),
        )

    fun save(weights: Map<TaskCategory, Int>) {
        if (saveState.value.busy) return
        if (uiState.value.readError != null || (uiState.value.hasLoaded && uiState.value.isLoading)) {
            saveState.value = PreferenceSaveState(error = "请先重新读取类别偏好；输入仍保留，本次未保存。")
            return
        }
        if (!CategoryPreferences.isValid(weights)) {
            saveState.value = PreferenceSaveState(error = "权重应为 0–100 的整数，合计不能为零；输入仍保留。")
            return
        }
        val confirmed = weights.toMap()
        saveState.value = PreferenceSaveState(busy = true)
        viewModelScope.launch {
            try {
                repository.save(confirmed)
                saveState.value = PreferenceSaveState(receipt = "类别偏好已保存；用于新排序与新预览，现有计划未改变。")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                saveState.value = PreferenceSaveState()
                throw cancelled
            } catch (_: Exception) {
                saveState.value = PreferenceSaveState(error = "类别偏好未保存；填写内容仍保留，请重试。")
            }
        }
    }

    fun retryRead() { if (!uiState.value.isLoading && uiState.value.readError != null) readRetries.value++ }

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
