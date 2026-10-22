package com.swan1127.repland.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.data.export.LocalDataJsonExporter
import com.swan1127.repland.data.export.XlsxExporter
import com.swan1127.repland.domain.model.LearningMemorySnapshot
import com.swan1127.repland.domain.model.LocalDataSnapshot
import com.swan1127.repland.domain.model.ToolOutputRecord
import com.swan1127.repland.domain.ports.DataExportFormat
import com.swan1127.repland.domain.ports.DataManagementRepository
import com.swan1127.repland.domain.ports.ExportSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

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
    /**
     * Injected rather than read from `LocalDate.now()` inline.
     *
     * The spreadsheet export stamps a thirty-day window and a file name from "today", so a test
     * that let the real clock through would assert against a date that changes every midnight —
     * and would be unable to state which days it expects in the sheet.
     */
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DataManagementUiState())
    val uiState: StateFlow<DataManagementUiState> = mutableUiState.asStateFlow()

    /**
     * Serialises the whole local snapshot into [format] and hands the bytes to [sink].
     *
     * The bytes are built here rather than in the UI so that "which format produces which content"
     * is one testable decision. The UI contributes only the destination, which is the part that
     * genuinely needs a `ContentResolver`.
     */
    fun exportTo(
        format: DataExportFormat,
        sink: ExportSink,
        /**
         * The user's tool writing, supplied by whoever can reach the auxiliary store.
         *
         * Defaulted so an export with no tool writing stays expressible and existing callers keep
         * compiling. The snapshot comes from Room, which by design cannot see the auxiliary store —
         * so this parameter is the one place the two halves of the user's data meet.
         */
        toolOutputs: List<ToolOutputRecord> = emptyList(),
        memorySnapshot: LearningMemorySnapshot? = null,
    ) {
        viewModelScope.launch {
            mutableUiState.value = DataManagementUiState(isWorking = true)
            val result = runCatching {
                val snapshot = repository.snapshot().copy(toolOutputs = toolOutputs, learningMemory = memorySnapshot)
                sink.write(bytesFor(format, snapshot))
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

    private fun bytesFor(format: DataExportFormat, snapshot: LocalDataSnapshot): ByteArray =
        when (format) {
            DataExportFormat.JSON ->
                LocalDataJsonExporter.export(snapshot).toByteArray(Charsets.UTF_8)

            DataExportFormat.XLSX ->
                XlsxExporter.build(snapshot, LocalDate.now(clock), clock.zone)
        }

    fun clearAllLocalData(
        protect: suspend (suspend () -> Unit) -> Unit = { write -> write() },
        onCleared: suspend () -> Unit = {},
    ) {
        viewModelScope.launch {
            mutableUiState.value = DataManagementUiState(isWorking = true)
            val result = runCatching {
                protect {
                    repository.clearAllLocalData()
                    onCleared()
                }
            }
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
