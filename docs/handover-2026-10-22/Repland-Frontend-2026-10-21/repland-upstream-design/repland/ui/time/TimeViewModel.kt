package com.swan1127.repland.ui.time

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swan1127.repland.data.importer.IcsImporter
import com.swan1127.repland.data.importer.TimetableGridParser
import com.swan1127.repland.domain.model.CalendarImport
import com.swan1127.repland.domain.model.CalendarImportOutcome
import com.swan1127.repland.domain.model.ClassPeriodClock
import com.swan1127.repland.domain.model.DateOverride
import com.swan1127.repland.domain.model.DateOverrideDraft
import com.swan1127.repland.domain.model.ImportedCourse
import com.swan1127.repland.domain.model.TimeArrangementSnapshot
import com.swan1127.repland.domain.model.TimeBlockValidator
import com.swan1127.repland.domain.model.WeeklyTimeBlock
import com.swan1127.repland.domain.model.WeeklyTimeBlockDraft
import com.swan1127.repland.domain.model.toDraft
import com.swan1127.repland.domain.model.weeklyBlockIdentity
import com.swan1127.repland.domain.ports.SpreadsheetGridImporter
import com.swan1127.repland.domain.ports.TimeRepository
import com.swan1127.repland.domain.ports.TimetableImporter
import com.swan1127.repland.domain.ports.TimetableSource
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    /**
     * What the write actually did: [added] rows went in, [skipped] were already there.
     *
     * Both numbers matter. Reporting only the successes makes a re-import of an unchanged file
     * look like a no-op — the screen flickers "reading…" and then says nothing, which reads as a
     * failure. Skipping is the correct outcome there, but it has to be said out loud.
     */
    data class Done(val added: Int, val skipped: Int) : TimetableImportState

    /**
     * What a calendar import actually did, broken down instead of summed.
     *
     * [duplicates] and [ignored] are separate because they mean opposite things: a duplicate is
     * "you already have this", which is the *correct* outcome of re-importing an unchanged file,
     * while an ignored row is "this file asked for something the app will not do" and the user may
     * need to act on it. Collapsing them into one number makes a clean re-import look like a
     * partial failure — the same mistake [Done]'s own comment warns about, one level down.
     *
     * [approximatedSeries] is neither: those blocks *were* imported, but the file says the series
     * ends and the app cannot express when, so they will keep occupying the week after it is over.
     */
    data class CalendarDone(
        val blocks: Int,
        val overrides: Int,
        val duplicates: Int,
        val ignored: Int,
        val approximatedSeries: Int,
    ) : TimetableImportState {
        val added: Int get() = blocks + overrides
    }

    data class Failed(val message: String) : TimetableImportState
}

class TimeViewModel(
    private val timeRepository: TimeRepository,
    private val timetableImporter: TimetableImporter,
    private val xlsxImporter: SpreadsheetGridImporter,
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

    /**
     * Returns the [Job] rather than swallowing it.
     *
     * The undo stack has to read the database *after* this write has landed; without a handle on
     * the job the caller would have to guess a delay, and a guess that is too short records an
     * "after" state that is really the "before" state — an undo entry that restores nothing.
     * Callers that do not care simply ignore the return value, as they always did.
     */
    fun deleteWeeklyBlock(id: String): Job =
        viewModelScope.launch { timeRepository.deleteWeeklyBlock(id) }

    fun saveDateOverride(draft: DateOverrideDraft) {
        if (!TimeBlockValidator.isValid(draft)) return
        viewModelScope.launch { timeRepository.saveDateOverride(draft) }
    }

    /** See [deleteWeeklyBlock] for why this hands back its [Job]. */
    fun deleteDateOverride(id: String): Job =
        viewModelScope.launch { timeRepository.deleteDateOverride(id) }

    fun saveSemesterFirstWeekMonday(date: LocalDate?) {
        viewModelScope.launch { timeRepository.saveSemesterFirstWeekMonday(date) }
    }

    /**
     * Swaps the entire weekly timetable for another saved one (D2).
     *
     * Delete-then-insert rather than a computed diff: the two sets are unrelated, so a minimal
     * edit would be more code and more ways to be wrong. This is the only writer of blocks,
     * and a switch is a single user action, so the planner never observes a half-swapped state.
     */
    /** See [deleteWeeklyBlock] for why this hands back its [Job]. */
    fun replaceTimetable(drafts: List<WeeklyTimeBlockDraft>): Job =
        viewModelScope.launch {
            uiState.value.weeklyBlocks.forEach { block ->
                timeRepository.deleteWeeklyBlock(block.id)
            }
            timeRepository.saveWeeklyBlocks(drafts)
        }

    /**
     * Reads the arrangement straight from the database, for the undo stack to hold on to.
     *
     * Deliberately a suspend read rather than a peek at [uiState]: the observed flow trails a
     * write by a round-trip, and an undo that captures a stale "before" would restore the wrong
     * thing.
     */
    suspend fun currentArrangement(): TimeArrangementSnapshot = TimeArrangementSnapshot(
        blocks = timeRepository.currentWeeklyBlocks().map(WeeklyTimeBlock::toDraft),
        overrides = timeRepository.currentDateOverrides().map(DateOverride::toDraft),
    )

    /**
     * Puts a whole arrangement back, replacing whatever is there now.
     *
     * The work lives in the repository, not here, because it has to be one database transaction —
     * see [TimeRepository.restoreArrangement] for why that matters. This stays as a thin delegate
     * so the undo stack does not have to know which repository it is talking to.
     *
     * Runs on the caller's coroutine, not `viewModelScope`: undo must be finished before the next
     * step can read the database, otherwise two quick undos interleave their write passes.
     */
    suspend fun restoreArrangement(snapshot: TimeArrangementSnapshot) =
        timeRepository.restoreArrangement(snapshot)

    fun readTimetable(source: TimetableSource) {
        timetableImport.value = TimetableImportState.Reading
        viewModelScope.launch {
            runCatching { timetableImporter.parse(source) }
                .onSuccess { courses ->
                    if (courses.isEmpty()) {
                        timetableImport.value = TimetableImportState.Failed("未识别到课程，请确认这是星期与节次网格形式的课表 PDF")
                    } else {
                        // Keep the existing Reading state until the repository write has finished.
                        // Assigning it after launching the write can otherwise race with the Idle state.
                        importRecognizedCourses(courses)
                    }
                }
                .onFailure { error ->
                    timetableImport.value = TimetableImportState.Failed(
                        error.message ?: "导入失败，请换一个课表 PDF 后重试",
                    )
                }
        }
    }

    /**
     * Reads a .xlsx timetable.
     *
     * Kept separate from [readTimetable] instead of being sniffed from the URI, because SAF
     * hands back opaque `content://` ids with no usable extension. The caller already knows
     * which picker the user came through, so it passes that knowledge down explicitly rather
     * than making this class guess.
     */
    fun readSpreadsheet(source: TimetableSource) {
        timetableImport.value = TimetableImportState.Reading
        viewModelScope.launch {
            runCatching { TimetableGridParser.parse(xlsxImporter.parse(source)) }
                .onSuccess { courses ->
                    if (courses.isEmpty()) {
                        timetableImport.value = TimetableImportState.Failed(
                            "表格里没找到「星期 × 节次」的网格，请确认有一行是星期表头",
                        )
                    } else {
                        importRecognizedCourses(courses)
                    }
                }
                .onFailure { error ->
                    timetableImport.value = TimetableImportState.Failed(
                        error.message ?: "Excel 导入失败，请确认文件是 .xlsx",
                    )
                }
        }
    }

    /** The same recognition rules, applied to a range the user copied straight out of Excel. */
    fun readTimetableFromClipboard(text: String) {
        val courses = TimetableGridParser.parse(TimetableGridParser.parseTabSeparated(text))
        if (courses.isEmpty()) {
            timetableImport.value = TimetableImportState.Failed("剪贴板里没有可识别的课表网格")
        } else {
            importRecognizedCourses(courses)
        }
    }

    /**
     * Reads a `.ics` calendar and turns its events into occupied time.
     *
     * Both decisions live in pure functions — [IcsImporter] parses the text and [CalendarImport]
     * maps events onto the timetable — so what is left here is opening the stream and writing the
     * result, with no branch worth testing.
     *
     * The existing rows are read from the **repository**, not from `uiState.value`. That state flow
     * is `WhileSubscribed`, so with nothing collecting it still holds its initial empty lists: an
     * import run before the screen subscribed would then find "nothing there" and write every row a
     * second time. [TimeRepository.currentWeeklyBlocks] exists for exactly this "as they are right
     * now" read.
     *
     * @param fallbackTitle used for an event with no `SUMMARY`; supplied by the caller because a
     *        placeholder is language and this class has no `Context`.
     */
    fun importCalendar(source: TimetableSource, fallbackTitle: String) {
        timetableImport.value = TimetableImportState.Reading
        viewModelScope.launch {
            val text = runCatching { source.open()?.bufferedReader()?.use { it.readText() } }.getOrNull()
            if (text.isNullOrBlank()) {
                timetableImport.value = TimetableImportState.Failed("无法读取这个文件，请换一个 .ics 日历后重试")
                return@launch
            }
            if (!IcsImporter.looksLikeCalendar(text)) {
                timetableImport.value = TimetableImportState.Failed("这不是一个日历文件（缺少 VCALENDAR 头）")
                return@launch
            }
            val outcome = CalendarImport.plan(
                events = IcsImporter.parse(text),
                existingWeeklyBlocks = timeRepository.currentWeeklyBlocks(),
                existingDateOverrides = timeRepository.currentDateOverrides(),
                fallbackTitle = fallbackTitle,
                semesterFirstWeekMonday = timeRepository.observeTimeConstraintSettings().first()
                    .semesterFirstWeekMonday,
            )
            when (outcome) {
                CalendarImportOutcome.NoEvents ->
                    timetableImport.value = TimetableImportState.Failed("日历里没有可导入的安排")

                is CalendarImportOutcome.Imported -> {
                    if (outcome.weeklyBlocks.isNotEmpty()) {
                        timeRepository.saveWeeklyBlocks(outcome.weeklyBlocks)
                    }
                    // Written one at a time: the repository has no batch call for one-day overrides,
                    // and an import is a user-initiated action where correctness beats round trips.
                    outcome.dateOverrides.forEach { timeRepository.saveDateOverride(it) }
                    timetableImport.value = TimetableImportState.CalendarDone(
                        blocks = outcome.weeklyBlocks.size,
                        overrides = outcome.dateOverrides.size,
                        duplicates = outcome.duplicateCount,
                        ignored = outcome.ignoredCount,
                        approximatedSeries = outcome.approximatedSeries,
                    )
                }
            }
        }
    }

    /** File selection is already an explicit import action; recognized courses go straight to the timeline. */
    private fun importRecognizedCourses(courses: List<ImportedCourse>) {
        val existingKeys = uiState.value.weeklyBlocks
            .map { block -> rowIdentity(block.title, block.dayOfWeek, block.startMinute, block.endMinute, block.weekPattern) }
            .toSet()
        // De-duplicated *before* the "already there" check rather than after, so `skipped`
        // means exactly one thing: rows the timetable already had. Folding the file's own
        // duplicates into that count would report a re-import as more crowded than it is.
        val recognized = courses.mapNotNull(ClassPeriodClock::toWeeklyTimeBlockDraft)
            .distinctBy { draft ->
                rowIdentity(draft.title, draft.dayOfWeek, draft.startMinute, draft.endMinute, draft.weekPattern)
            }
        val drafts = recognized.filter { draft ->
            rowIdentity(draft.title, draft.dayOfWeek, draft.startMinute, draft.endMinute, draft.weekPattern) !in existingKeys
        }
        viewModelScope.launch {
            timeRepository.saveWeeklyBlocks(drafts)
            timetableImport.value = TimetableImportState.Done(
                added = drafts.size,
                skipped = recognized.size - drafts.size,
            )
        }
    }

    /**
     * What makes two rows "the same class" for import purposes.
     *
     * The rule itself now lives in [weeklyBlockIdentity], because the calendar import needs the
     * same judgement and a second hand-written copy is exactly how the two checks drifted apart
     * once before (see that function's own note).
     */
    private fun rowIdentity(
        title: String,
        dayOfWeek: DayOfWeek,
        startMinute: Int,
        endMinute: Int,
        weekPattern: String?,
    ): List<Any?> = weeklyBlockIdentity(title, dayOfWeek, startMinute, endMinute, weekPattern)

    fun clearTimetableImport() {
        timetableImport.value = TimetableImportState.Idle
    }

    class Factory(
        private val timeRepository: TimeRepository,
        private val timetableImporter: TimetableImporter,
        private val xlsxImporter: SpreadsheetGridImporter,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(TimeViewModel::class.java))
            return TimeViewModel(timeRepository, timetableImporter, xlsxImporter) as T
        }
    }
}
