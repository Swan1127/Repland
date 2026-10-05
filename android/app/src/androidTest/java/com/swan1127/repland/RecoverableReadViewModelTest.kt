package com.swan1127.repland

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.swan1127.repland.data.importer.PdfTimetableImporter
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.tasks.*
import com.swan1127.repland.ui.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecoverableReadViewModelTest {
    private fun db() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
    private fun task() = TaskDraft("original", "原任务", "备注", TaskCategory.COURSE, TaskPriority.MEDIUM, 1, 30, null)
    private fun week() = WeeklyTimeBlockDraft(title = "原课程", kind = TimeBlockKind.COURSE,
        dayOfWeek = DayOfWeek.MONDAY, startMinute = 600, endMinute = 645, weekPattern = "1-16单周")

    @Test fun initial_task_failure_is_not_empty_success_and_blocks_save_until_retry() = runBlocking {
        val database = db(); val store = ViewModelStore(); val base = RoomTaskRepository(database)
        val fault = ReadFaultTasks(base).apply { failed.value = true }
        lateinit var vm: TaskViewModel; var observer: Job? = null
        try {
            base.save(task())
            withContext(Dispatchers.Main) { vm = TaskViewModel(fault); store.put("tasks", vm) }
            observer = launch { vm.uiState.collect() }
            withTimeout(10_000) { vm.uiState.first { it.readError != null } }
            assertFalse(vm.uiState.value.hasLoaded); assertFalse(vm.uiState.value.isTrusted); assertNull(vm.uiState.value.error)
            withContext(Dispatchers.Main) { vm.saveTask(task().copy(displayName = "不可写入")) }
            assertNotNull(vm.editorSaveState.value.error); assertNull(vm.editorSaveState.value.receipt)
            assertEquals("原任务", base.observeTasks().first().single().displayName)
            fault.failed.value = false
            assertNotNull(vm.uiState.value.readError)
            withContext(Dispatchers.Main) { vm.retryRead() }
            withTimeout(10_000) { vm.uiState.first { it.isTrusted } }
            assertEquals(2, fault.sources.get()); assertEquals("原任务", vm.uiState.value.tasks.single().displayName)
            withContext(Dispatchers.Main) { vm.saveTask(task().copy(displayName = "恢复后保存")) }
            withTimeout(10_000) { vm.editorSaveState.first { it.receipt != null } }
            assertEquals("恢复后保存", base.observeTasks().first().single().displayName)
        } finally { observer?.cancelAndJoin(); withContext(Dispatchers.Main) { store.clear() }; database.close() }
    }

    @Test fun later_task_failure_keeps_identity_and_action_errors_cannot_heal_read() = runBlocking {
        val database = db(); val store = ViewModelStore(); val base = RoomTaskRepository(database); val fault = ReadFaultTasks(base)
        lateinit var vm: TaskViewModel; var observer: Job? = null
        try {
            base.save(task()); val original = base.observeTasks().first()
            withContext(Dispatchers.Main) { vm = TaskViewModel(fault); store.put("tasks", vm) }
            observer = launch { vm.uiState.collect() }
            withTimeout(10_000) { vm.uiState.first { it.isTrusted } }
            fault.failed.value = true
            withTimeout(10_000) { vm.uiState.first { it.readError != null } }
            assertEquals(original, vm.uiState.value.tasks); assertTrue(vm.uiState.value.hasLoaded)
            withContext(Dispatchers.Main) { vm.cancelTask("original"); vm.saveTask(task()); vm.resetEditorResult(); vm.resetMutation() }
            assertEquals(original, base.observeTasks().first()); assertTrue(base.observeExecutionLogs("original").first().isEmpty())
            assertNotNull(vm.uiState.value.readError); assertNull(vm.uiState.value.error)
            fault.failed.value = false
            withContext(Dispatchers.Main) { vm.retryRead() }
            withTimeout(10_000) { vm.uiState.first { it.isTrusted } }
            withContext(Dispatchers.Main) { vm.saveTask(task().copy(displayName = "", description = "")) }
            withTimeout(10_000) { vm.uiState.first { it.error == TaskError.INVALID_DRAFT } }
            assertNull(vm.uiState.value.readError); assertEquals(original, vm.uiState.value.tasks); assertTrue(vm.uiState.value.isTrusted)
        } finally { observer?.cancelAndJoin(); withContext(Dispatchers.Main) { store.clear() }; database.close() }
    }

    @Test fun initial_time_failure_is_not_empty_success_and_recovers_all_three_sources() = runBlocking {
        val database = db(); val store = ViewModelStore(); val base = RoomTimeRepository(database.timeDao())
        val fault = ReadFaultTime(base).apply { failedPart.value = "settings" }
        lateinit var vm: TimeViewModel; var observer: Job? = null
        try {
            base.saveWeeklyBlock(week())
            withContext(Dispatchers.Main) { vm = TimeViewModel(fault, PdfTimetableImporter(ApplicationProvider.getApplicationContext())); store.put("time", vm) }
            observer = launch { vm.uiState.collect() }
            withTimeout(10_000) { vm.uiState.first { it.readError != null } }
            assertFalse(vm.uiState.value.hasLoaded); assertFalse(vm.uiState.value.isTrusted)
            withContext(Dispatchers.Main) { vm.saveWeeklyBlock(week().copy(title = "不能新增")) }
            assertNull(vm.mutationState.value.receipt); assertNotNull(vm.mutationState.value.error)
            assertEquals(1, base.observeWeeklyBlocks().first().size)
            fault.failedPart.value = null
            withContext(Dispatchers.Main) { vm.retryRead() }
            withTimeout(10_000) { vm.uiState.first { it.isTrusted } }
            assertEquals("原课程", vm.uiState.value.weeklyBlocks.single().title); assertEquals(2, fault.sources.get())
        } finally { observer?.cancelAndJoin(); withContext(Dispatchers.Main) { store.clear() }; database.close() }
    }

    @Test fun weekly_failure_retains_complete_time_snapshot() = timeFailure("weekly")
    @Test fun date_failure_retains_complete_time_snapshot() = timeFailure("dates")
    @Test fun settings_failure_retains_complete_time_snapshot() = timeFailure("settings")
    private fun timeFailure(part: String) = runBlocking {
        val database = db(); val store = ViewModelStore(); val base = RoomTimeRepository(database.timeDao()); val fault = ReadFaultTime(base)
        lateinit var vm: TimeViewModel; var observer: Job? = null
        try {
            base.saveWeeklyBlock(week())
            base.saveDateOverride(DateOverrideDraft(title = "原例外", type = DateOverrideType.BLOCKED,
                date = LocalDate.now().plusDays(1), startMinute = 700, endMinute = 730))
            base.saveSemesterFirstWeekMonday(LocalDate.of(2026, 10, 5))
            withContext(Dispatchers.Main) { vm = TimeViewModel(fault, PdfTimetableImporter(ApplicationProvider.getApplicationContext())); store.put("time", vm) }
            observer = launch { vm.uiState.collect() }
            val original = withTimeout(10_000) { vm.uiState.first { it.isTrusted } }
            fault.failedPart.value = part
            withTimeout(10_000) { vm.uiState.first { it.readError != null } }
            assertEquals(original.weeklyBlocks, vm.uiState.value.weeklyBlocks)
            assertEquals(original.dateOverrides, vm.uiState.value.dateOverrides)
            assertEquals(original.semesterFirstWeekMonday, vm.uiState.value.semesterFirstWeekMonday)
            withContext(Dispatchers.Main) { vm.deleteWeeklyBlock(original.weeklyBlocks.single().id); vm.resetMutation(); vm.saveSemesterFirstWeekMonday(null) }
            assertNotNull(vm.mutationState.value.error); assertNull(vm.mutationState.value.receipt)
            assertEquals(original.weeklyBlocks, base.observeWeeklyBlocks().first())
            assertEquals(original.timeConstraintsUpdatedAtEpochMillis, base.observeTimeConstraintSettings().first().updatedAtEpochMillis)
            fault.failedPart.value = null
            assertNotNull(vm.uiState.value.readError)
            withContext(Dispatchers.Main) { vm.retryRead() }
            withTimeout(10_000) { vm.uiState.first { it.isTrusted } }
            assertEquals(2, fault.sources.get())
            assertEquals(original.weeklyBlocks, vm.uiState.value.weeklyBlocks)
        } finally { observer?.cancelAndJoin(); withContext(Dispatchers.Main) { store.clear() }; database.close() }
    }

    @Test fun import_edits_do_not_heal_read_failure_and_review_can_be_confirmed_after_retry() = runBlocking {
        val database = db(); val store = ViewModelStore(); val base = RoomTimeRepository(database.timeDao()); val fault = ReadFaultTime(base)
        val course = ImportedCourse("preview", "导入草稿", DayOfWeek.MONDAY, 1, 2, "1-16周")
        lateinit var vm: TimeViewModel; var observer: Job? = null
        try {
            withContext(Dispatchers.Main) { vm = TimeViewModel(fault, PdfTimetableImporter(ApplicationProvider.getApplicationContext())) { listOf(course) }; store.put("time", vm) }
            observer = launch { vm.uiState.collect() }
            withTimeout(10_000) { vm.uiState.first { it.isTrusted } }
            withContext(Dispatchers.Main) { vm.readTimetable(Uri.parse("content://qa/injected")) }
            withTimeout(10_000) { vm.uiState.first { it.timetableImport is TimetableImportState.Review } }
            fault.failedPart.value = "weekly"
            withTimeout(10_000) { vm.uiState.first { it.readError != null } }
            withContext(Dispatchers.Main) { vm.updateImportedCourse(course.copy(title = "保留编辑")); vm.acknowledgeImportClock(true); vm.confirmTimetableImport() }
            withTimeout(10_000) { vm.uiState.first { (it.timetableImport as? TimetableImportState.Review)?.error != null } }
            assertNotNull(vm.uiState.value.readError); assertTrue(base.observeWeeklyBlocks().first().isEmpty())
            assertEquals("保留编辑", (vm.uiState.value.timetableImport as TimetableImportState.Review).courses.single().title)
            fault.failedPart.value = null
            withContext(Dispatchers.Main) { vm.retryRead() }
            withTimeout(10_000) { vm.uiState.first { it.isTrusted } }
            withContext(Dispatchers.Main) { vm.confirmTimetableImport() }
            withTimeout(10_000) { vm.timetableImportState.first { it is TimetableImportState.Completed } }
            assertEquals("保留编辑", base.observeWeeklyBlocks().first().single().title)
        } finally { observer?.cancelAndJoin(); withContext(Dispatchers.Main) { store.clear() }; database.close() }
    }
}
