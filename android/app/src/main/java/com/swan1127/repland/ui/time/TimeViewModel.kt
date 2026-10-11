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
    val hasLoaded: Boolean = false,
    val readError: String? = null,
) {
    val isTrusted: Boolean get() = hasLoaded && !isLoading && readError == null
}

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

enum class TimeMutationKind { WEEKLY_SAVE, WEEKLY_DELETE, OVERRIDE_SAVE, OVERRIDE_DELETE, SEMESTER_SAVE }
data class TimeMutationState(
    val kind: TimeMutationKind? = null,
    val targetId: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val receipt: String? = null,
)

class TimeViewModel(
    private val timeRepository: TimeRepository,
    private val timetableImporter: PdfTimetableImporter,
    private val parseTimetable: suspend (Uri) -> List<ImportedCourse> = timetableImporter::parse,
) : ViewModel() {
    private val timetableImport = MutableStateFlow<TimetableImportState>(TimetableImportState.Idle)
    val timetableImportState: StateFlow<TimetableImportState> = timetableImport
    private var importJob: Job? = null
    private var importVersion = 0L
    private val mutation = MutableStateFlow(TimeMutationState())
    val mutationState: StateFlow<TimeMutationState> = mutation
    private var retryAction: (() -> Unit)? = null

    private val readRetries = MutableStateFlow(0)
    val uiState: StateFlow<TimeUiState> = com.swan1127.repland.ui.state.recoverableRead(
        initial = TimeUiState(), retries = readRetries, errorMessage = "时间设置读取失败，请重试；未删除课程或例外。",
    ) {
        combine(timeRepository.observeWeeklyBlocks(), timeRepository.observeDateOverrides(),
            timeRepository.observeTimeConstraintSettings()) { weeklyBlocks, dateOverrides, settings ->
            TimeUiState(weeklyBlocks, dateOverrides, settings.semesterFirstWeekMonday, settings.updatedAtEpochMillis,
                isLoading = false, hasLoaded = true)
        }
    }.combine(timetableImport) { read, importState ->
        read.value.copy(isLoading = read.isLoading, hasLoaded = read.hasLoaded, readError = read.error, timetableImport = importState)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TimeUiState(),
        )

    fun retryRead() { if (!uiState.value.isLoading && uiState.value.readError != null) readRetries.value++ }

    fun saveWeeklyBlock(draft: WeeklyTimeBlockDraft) = saveWeeklyBlock(draft) {}

    fun saveWeeklyBlock(draft: WeeklyTimeBlockDraft, onSaved: () -> Unit) {
        mutate(TimeMutationKind.WEEKLY_SAVE, draft.id, "每周时间已保存", onSaved) {
            require(TimeBlockValidator.isValid(draft))
            timeRepository.saveWeeklyBlock(draft)
        }
    }

    fun deleteWeeklyBlock(id: String) {
        mutate(TimeMutationKind.WEEKLY_DELETE, id, "每周时间已删除") { timeRepository.deleteWeeklyBlock(id) }
    }

    fun saveDateOverride(draft: DateOverrideDraft) = saveDateOverride(draft) {}

    fun saveDateOverride(draft: DateOverrideDraft, onSaved: () -> Unit) {
        mutate(TimeMutationKind.OVERRIDE_SAVE, draft.id, "单日例外已保存", onSaved) {
            require(TimeBlockValidator.isValid(draft))
            timeRepository.saveDateOverride(draft)
        }
    }

    fun deleteDateOverride(id: String) {
        mutate(TimeMutationKind.OVERRIDE_DELETE, id, "单日例外已删除") { timeRepository.deleteDateOverride(id) }
    }

    fun saveSemesterFirstWeekMonday(date: LocalDate?) {
        mutate(TimeMutationKind.SEMESTER_SAVE, null, "学期起点已保存") { timeRepository.saveSemesterFirstWeekMonday(date) }
    }

    private fun mutate(kind: TimeMutationKind, targetId: String?, receipt: String, onSaved: () -> Unit = {}, action: suspend () -> Unit) {
        if (mutation.value.busy || mutation.value.receipt != null ||
            (timetableImport.value as? TimetableImportState.Review)?.saving == true) return
        if (uiState.value.readError != null || (uiState.value.hasLoaded && uiState.value.isLoading)) {
            mutation.value = TimeMutationState(kind, targetId, error = "请先重新读取时间设置；本次未保存，输入仍保留。")
            retryAction = { mutate(kind, targetId, receipt, onSaved, action) }
            return
        }
        mutation.value = TimeMutationState(kind, targetId, busy = true)
        retryAction = { mutate(kind, targetId, receipt, onSaved, action) }
        viewModelScope.launch {
            try {
                action()
                retryAction = null
                mutation.value = TimeMutationState(kind, targetId, receipt = "$receipt；现有任务计划未自动重新安排")
                onSaved()
            } catch (cancelled: CancellationException) {
                retryAction = null
                mutation.value = TimeMutationState()
                throw cancelled
            } catch (_: Exception) {
                mutation.value = TimeMutationState(kind, targetId, error = "时间设置未保存。输入仍保留，请重试；若原记录已移除，请取消并重新打开。")
            }
        }
    }

    fun retryMutation() { retryAction?.invoke() }
    fun resetMutation() {
        if (!mutation.value.busy) { retryAction = null; mutation.value = TimeMutationState() }
    }

    fun readTimetable(uri: Uri) {
        if ((timetableImport.value as? TimetableImportState.Review)?.saving == true || mutation.value.busy) return
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
        if (uiState.value.readError != null || (uiState.value.hasLoaded && uiState.value.isLoading)) {
            timetableImport.value = review.copy(error = "请先重新读取时间设置；导入预览仍保留，没有写入课程。")
            return
        }
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
