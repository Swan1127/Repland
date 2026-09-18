package com.swan1127.repland.ui.data

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.data.export.LocalDataJsonExporter
import com.swan1127.repland.domain.ports.DataManagementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DataManagementResult {
    EXPORT_SUCCEEDED,
    EXPORT_FAILED,
    CLEAR_SUCCEEDED,
    CLEAR_FAILED,
}

data class DataManagementUiState(
    val isWorking: Boolean = false,
    val result: DataManagementResult? = null,
)

class DataManagementViewModel(
    private val repository: DataManagementRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DataManagementUiState())
    val uiState: StateFlow<DataManagementUiState> = mutableUiState.asStateFlow()

    fun exportTo(contentResolver: ContentResolver, destination: Uri) {
        viewModelScope.launch {
            mutableUiState.value = DataManagementUiState(isWorking = true)
            val result = runCatching {
                val json = LocalDataJsonExporter.export(repository.snapshot())
                requireNotNull(contentResolver.openOutputStream(destination)) {
                    "The selected export destination cannot be opened."
                }.bufferedWriter().use { writer -> writer.write(json) }
            }
            mutableUiState.value = DataManagementUiState(
                result = if (result.isSuccess) {
                    DataManagementResult.EXPORT_SUCCEEDED
                } else {
                    DataManagementResult.EXPORT_FAILED
                },
            )
        }
    }

    fun clearAllLocalData() {
        viewModelScope.launch {
            mutableUiState.value = DataManagementUiState(isWorking = true)
            val result = runCatching { repository.clearAllLocalData() }
            mutableUiState.value = DataManagementUiState(
                result = if (result.isSuccess) {
                    DataManagementResult.CLEAR_SUCCEEDED
                } else {
                    DataManagementResult.CLEAR_FAILED
                },
            )
        }
    }

    class Factory(
        private val repository: DataManagementRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(DataManagementViewModel::class.java))
            return DataManagementViewModel(repository) as T
        }
    }
}
