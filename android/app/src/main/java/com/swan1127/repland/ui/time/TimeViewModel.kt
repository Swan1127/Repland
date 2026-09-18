package com.swan1127.repland.ui.time

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.data.importer.PdfTimetableImporter
import com.swan1127.repland.domain.model.ClassPeriodClock
import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.DateOverrideDraft
import com.swan1127.repland.domain.model.ImportedCourse
import com.swan1127.repland.domain.model.TimeBlockValidator
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import com.swan1127.repland.domain.ports.TimeRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TimeUiState(
    val weeklyBlocks: List<WeeklyTimeBlock> = emptyList(),
    val dateOverrides: List<DateOverride> = emptyList(),
    val semesterFirstWeekMonday: LocalDate? = null,
    val timeConstraintsUpdatedAtEpochMillis: Long = 0L,
    val isLoading: Boolean = true,
    val timetableImport: TimetableImportState = TimetableImportState.Idle,
)

sealed interface TimetableImportState {
    data object Idle : TimetableImportState

    data object Reading : TimetableImportState

    data class Review(val courses: List<ImportedCourse>) : TimetableImportState

    data class Failed(val message: String) : TimetableImportState
}

class TimeViewModel(
    private val timeRepository: TimeRepository,
    private val timetableImporter: PdfTimetableImporter,
) : ViewModel() {
    private val timetableImport = MutableStateFlow<TimetableImportState>(TimetableImportState.Idle)

    val uiState: StateFlow<TimeUiState> = combine(
        timeRepository.observeWeeklyBlocks(),
        timeRepository.observeDateOverrides(),
        timeRepository.observeTimeConstraintSettings(),
        timetableImport,
    ) { weeklyBlocks, dateOverrides, constraintSettings, importState ->
        TimeUiState(
            weeklyBlocks = weeklyBlocks,
            dateOverrides = dateOverrides,
            semesterFirstWeekMonday = constraintSettings.semesterFirstWeekMonday,
            timeConstraintsUpdatedAtEpochMillis = constraintSettings.updatedAtEpochMillis,
            isLoading = false,
            timetableImport = importState,
        )
    }.catch { emit(TimeUiState(isLoading = false)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TimeUiState(),
        )

    fun saveWeeklyBlock(draft: WeeklyTimeBlockDraft) {
        if (!TimeBlockValidator.isValid(draft)) return
        viewModelScope.launch { timeRepository.saveWeeklyBlock(draft) }
    }

    fun deleteWeeklyBlock(id: String) {
        viewModelScope.launch { timeRepository.deleteWeeklyBlock(id) }
    }

    fun saveDateOverride(draft: DateOverrideDraft) {
        if (!TimeBlockValidator.isValid(draft)) return
        viewModelScope.launch { timeRepository.saveDateOverride(draft) }
    }

    fun deleteDateOverride(id: String) {
        viewModelScope.launch { timeRepository.deleteDateOverride(id) }
    }

    fun saveSemesterFirstWeekMonday(date: LocalDate?) {
        viewModelScope.launch { timeRepository.saveSemesterFirstWeekMonday(date) }
    }

    fun readTimetable(uri: Uri) {
        timetableImport.value = TimetableImportState.Reading
        viewModelScope.launch {
            runCatching { timetableImporter.parse(uri) }
                .onSuccess { courses ->
                    timetableImport.value = if (courses.isEmpty()) {
                        TimetableImportState.Failed("未识别到课程，请确认这是星期与节次网格形式的课表 PDF")
                    } else {
                        TimetableImportState.Review(courses)
                    }
                }
                .onFailure { error ->
                    timetableImport.value = TimetableImportState.Failed(
                        error.message ?: "导入失败，请换一个课表 PDF 后重试",
                    )
                }
        }
    }

    fun confirmTimetableImport(courses: List<ImportedCourse>) {
        val existingKeys = uiState.value.weeklyBlocks.map { block ->
            listOf(
                block.title,
                block.dayOfWeek,
                block.startMinute,
                block.endMinute,
                block.weekPattern,
            )
        }.toSet()
        val drafts = courses.mapNotNull(ClassPeriodClock::toWeeklyTimeBlockDraft)
            .filter { draft ->
                listOf(
                    draft.title,
                    draft.dayOfWeek,
                    draft.startMinute,
                    draft.endMinute,
                    draft.weekPattern,
                ) !in existingKeys
            }
            .distinctBy { draft ->
                listOf(
                    draft.title.trim(),
                    draft.dayOfWeek,
                    draft.startMinute,
                    draft.endMinute,
                    draft.weekPattern?.trim()?.takeIf(String::isNotBlank),
                )
            }
        viewModelScope.launch {
            timeRepository.saveWeeklyBlocks(drafts)
            timetableImport.value = TimetableImportState.Idle
        }
    }

    fun clearTimetableImport() {
        timetableImport.value = TimetableImportState.Idle
    }

    class Factory(
        private val timeRepository: TimeRepository,
        private val timetableImporter: PdfTimetableImporter,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(TimeViewModel::class.java))
            return TimeViewModel(timeRepository, timetableImporter) as T
        }
    }
}
