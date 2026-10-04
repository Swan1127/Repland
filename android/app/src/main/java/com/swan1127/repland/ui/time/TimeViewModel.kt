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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

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

    data class Review(
        val courses: List<ImportedCourse>,
        val selectedIds: Set<String> = courses.map(ImportedCourse::id).toSet(),
        val clockAcknowledged: Boolean = false,
        val saving: Boolean = false,
        val error: String? = null,
    ) : TimetableImportState

    data class Completed(val addedCount: Int, val skippedCount: Int) : TimetableImportState

    data class Failed(val message: String) : TimetableImportState
}

class TimeViewModel(
    private val timeRepository: TimeRepository,
    private val timetableImporter: PdfTimetableImporter,
    private val parseTimetable: suspend (Uri) -> List<ImportedCourse> = timetableImporter::parse,
) : ViewModel() {
    private val timetableImport = MutableStateFlow<TimetableImportState>(TimetableImportState.Idle)
    val timetableImportState: StateFlow<TimetableImportState> = timetableImport
    private var importJob: Job? = null
    private var importVersion = 0L

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
        if ((timetableImport.value as? TimetableImportState.Review)?.saving == true) return
        importJob?.cancel()
        val version = ++importVersion
        timetableImport.value = TimetableImportState.Reading
        importJob = viewModelScope.launch {
            try {
                val courses = parseTimetable(uri)
                if (version != importVersion) return@launch
                timetableImport.value = if (courses.isEmpty()) {
                    TimetableImportState.Failed("未识别到课程，请确认这是星期与节次网格形式的课表 PDF")
                } else if (courses.map(ImportedCourse::id).distinct().size != courses.size) {
                    TimetableImportState.Failed("课程识别身份重复，请换一个课表 PDF 后重试")
                } else TimetableImportState.Review(courses)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (version == importVersion) timetableImport.value = TimetableImportState.Failed(
                    "无法识别此课表，请检查文件权限和 PDF 格式后重新选择；没有写入课程",
                )
            }
        }
    }

    fun selectImportedCourse(id: String, selected: Boolean) {
        updateReview { review ->
            if (review.courses.none { it.id == id }) review
            else review.copy(selectedIds = if (selected) review.selectedIds + id else review.selectedIds - id, error = null)
        }
    }

    fun acknowledgeImportClock(acknowledged: Boolean) {
        updateReview { it.copy(clockAcknowledged = acknowledged, error = null) }
    }

    fun updateImportedCourse(course: ImportedCourse) {
        updateReview { review ->
            val draft = ClassPeriodClock.toWeeklyTimeBlockDraft(course)
            if (review.courses.none { it.id == course.id } || draft == null || !TimeBlockValidator.isValid(draft)) {
                review.copy(error = "课程名称或节次无效，请修改后重试")
            } else review.copy(courses = review.courses.map { if (it.id == course.id) course else it },
                clockAcknowledged = false, error = null)
        }
    }

    private fun updateReview(transform: (TimetableImportState.Review) -> TimetableImportState.Review) {
        val review = timetableImport.value as? TimetableImportState.Review ?: return
        if (!review.saving) timetableImport.value = transform(review)
    }

    fun confirmTimetableImport() {
        val review = timetableImport.value as? TimetableImportState.Review ?: return
        if (review.saving) return
        val selected = review.courses.filter { it.id in review.selectedIds }
        if (!review.clockAcknowledged || selected.isEmpty()) {
            timetableImport.value = review.copy(error = "请至少选择一门课程并核对默认节次时间")
            return
        }
        val drafts = selected.map(ClassPeriodClock::toWeeklyTimeBlockDraft)
        if (drafts.any { it == null || !TimeBlockValidator.isValid(it) }) {
            timetableImport.value = review.copy(error = "所选课程名称或节次无效，请编辑后重试")
            return
        }
        timetableImport.value = review.copy(saving = true, error = null)
        importJob = viewModelScope.launch {
            try {
                val added = timeRepository.importWeeklyBlocks(drafts.filterNotNull())
                timetableImport.value = TimetableImportState.Completed(added, selected.size - added)
            } catch (cancelled: CancellationException) {
                timetableImport.value = review
                throw cancelled
            } catch (_: Exception) {
                timetableImport.value = review.copy(error = "课程未保存，预览仍保留；请重试")
            }
        }
    }

    fun clearTimetableImport() {
        if ((timetableImport.value as? TimetableImportState.Review)?.saving == true) return
        ++importVersion
        importJob?.cancel()
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
