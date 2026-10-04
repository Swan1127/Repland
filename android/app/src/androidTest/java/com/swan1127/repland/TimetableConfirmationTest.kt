package com.swan1127.repland

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.swan1127.repland.data.importer.PdfTimetableImporter
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.TimeRepository
import com.swan1127.repland.ui.time.*
import java.time.DayOfWeek
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class TimetableConfirmationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun db() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
    private fun course(id: String = "a") = ImportedCourse(id, "课程$id", DayOfWeek.MONDAY, 1, 2, "1-16周")
    private fun createVm(repo: TimeRepository, store: ViewModelStore, parse: suspend (Uri) -> List<ImportedCourse>): TimeViewModel {
        lateinit var vm: TimeViewModel
        instrumentation.runOnMainSync {
            vm = TimeViewModel(repo, PdfTimetableImporter(ApplicationProvider.getApplicationContext()), parse)
            store.put("timetable", vm)
        }
        return vm
    }
    private suspend fun review(vm: TimeViewModel) = withTimeout(10_000) {
        vm.timetableImportState.first { it is TimetableImportState.Review } as TimetableImportState.Review
    }

    @Test fun preview_selection_edit_clock_validation_and_cancel_never_write() = runBlocking {
        val database = db(); val repo = RoomTimeRepository(database.timeDao()); val store = ViewModelStore()
        try {
            val vm = createVm(repo, store) { listOf(course(), course("b")) }
            instrumentation.runOnMainSync { vm.readTimetable(Uri.EMPTY) }
            review(vm)
            assertTrue(repo.observeWeeklyBlocks().first().isEmpty())
            instrumentation.runOnMainSync {
                vm.confirmTimetableImport()
                vm.selectImportedCourse("b", false)
                vm.updateImportedCourse(course().copy(title = "已修改", startPeriod = 3, endPeriod = 4))
            }
            val edited = vm.timetableImportState.value as TimetableImportState.Review
            assertEquals(setOf("a"), edited.selectedIds); assertFalse(edited.clockAcknowledged)
            assertEquals("已修改", edited.courses.first().title)
            instrumentation.runOnMainSync { vm.updateImportedCourse(course().copy(startPeriod = 99)) }
            assertNotNull((vm.timetableImportState.value as TimetableImportState.Review).error)
            instrumentation.runOnMainSync { vm.clearTimetableImport(); vm.confirmTimetableImport() }
            assertEquals(TimetableImportState.Idle, vm.timetableImportState.value)
            assertTrue(repo.observeWeeklyBlocks().first().isEmpty())
            assertEquals(0L, repo.observeTimeConstraintSettings().first().updatedAtEpochMillis)
        } finally { instrumentation.runOnMainSync { store.clear() }; database.close() }
    }

    @Test fun save_failure_keeps_review_retry_commits_once_and_pending_cannot_cancel_or_replace() = runBlocking {
        val database = db(); val base = RoomTimeRepository(database.timeDao()); val store = ViewModelStore()
        var failWrite = true; var writes = 0; var gate: CompletableDeferred<Unit>? = null
        val repo = object : TimeRepository by base {
            override suspend fun importWeeklyBlocks(drafts: List<WeeklyTimeBlockDraft>): Int {
                gate?.await()
                if (failWrite) error("injected write failure")
                writes++
                return base.importWeeklyBlocks(drafts)
            }
        }
        try {
            val vm = createVm(repo, store) { listOf(course(), course("b")) }
            instrumentation.runOnMainSync { vm.readTimetable(Uri.EMPTY) }; review(vm)
            instrumentation.runOnMainSync { vm.selectImportedCourse("b", false); vm.acknowledgeImportClock(true); vm.confirmTimetableImport() }
            withTimeout(10_000) { vm.timetableImportState.first { (it as? TimetableImportState.Review)?.error != null } }
            val failed = vm.timetableImportState.value as TimetableImportState.Review
            assertEquals(setOf("a"), failed.selectedIds); assertTrue(failed.clockAcknowledged); assertFalse(failed.saving)
            assertTrue(base.observeWeeklyBlocks().first().isEmpty())
            failWrite = false; gate = CompletableDeferred()
            instrumentation.runOnMainSync {
                vm.confirmTimetableImport(); vm.confirmTimetableImport(); vm.clearTimetableImport()
                vm.readTimetable(Uri.parse("file:///different.pdf")); vm.selectImportedCourse("a", false)
            }
            assertTrue((vm.timetableImportState.value as TimetableImportState.Review).saving)
            gate!!.complete(Unit)
            val result = withTimeout(10_000) { vm.timetableImportState.first { it is TimetableImportState.Completed } } as TimetableImportState.Completed
            assertEquals(1, writes); assertEquals(1, result.addedCount); assertEquals(0, result.skippedCount)
            assertEquals("课程a", base.observeWeeklyBlocks().first().single().title)
            instrumentation.runOnMainSync { vm.confirmTimetableImport() }; assertEquals(1, writes)
        } finally { gate?.complete(Unit); instrumentation.runOnMainSync { store.clear() }; database.close() }
    }

    @Test fun cancelled_noncooperative_read_cannot_restore_preview_or_write() = runBlocking {
        val database = db(); val repo = RoomTimeRepository(database.timeDao()); val store = ViewModelStore()
        val entered = CompletableDeferred<Unit>(); val gate = CompletableDeferred<Unit>(); val finished = CompletableDeferred<Unit>()
        try {
            val vm = createVm(repo, store) {
                withContext(NonCancellable) { entered.complete(Unit); gate.await(); finished.complete(Unit); listOf(course()) }
            }
            instrumentation.runOnMainSync { vm.readTimetable(Uri.EMPTY) }
            withTimeout(10_000) { entered.await() }
            instrumentation.runOnMainSync { vm.clearTimetableImport() }
            gate.complete(Unit); withTimeout(10_000) { finished.await() }
            instrumentation.waitForIdleSync()
            assertEquals(TimetableImportState.Idle, vm.timetableImportState.value)
            assertTrue(repo.observeWeeklyBlocks().first().isEmpty())
        } finally { gate.complete(Unit); instrumentation.runOnMainSync { store.clear() }; database.close() }
    }

    @Test fun import_deduplicates_against_current_database_and_preserves_existing_course() = runBlocking {
        val database = db(); val repo = RoomTimeRepository(database.timeDao())
        try {
            val draft = ClassPeriodClock.toWeeklyTimeBlockDraft(course())!!
            repo.saveWeeklyBlock(draft.copy(note = "已有备注", trackId = "course"))
            val original = repo.observeWeeklyBlocks().first().single()
            val revision = repo.observeTimeConstraintSettings().first()
            assertEquals(0, repo.importWeeklyBlocks(listOf(draft, draft.copy(title = " ${draft.title} "))))
            assertEquals(original, repo.observeWeeklyBlocks().first().single())
            assertEquals(revision, repo.observeTimeConstraintSettings().first())
            val next = draft.copy(title = "新增")
            val results = coroutineScope { listOf(async(Dispatchers.IO) { repo.importWeeklyBlocks(listOf(next)) },
                async(Dispatchers.IO) { repo.importWeeklyBlocks(listOf(next)) }).awaitAll() }
            assertEquals(1, results.sum()); assertEquals(2, repo.observeWeeklyBlocks().first().size)
            assertTrue(repo.observeTimeConstraintSettings().first().updatedAtEpochMillis > revision.updatedAtEpochMillis)
        } finally { database.close() }
    }

    @Test fun revision_write_failure_rolls_back_all_imported_rows_and_invalid_batch_never_partially_saves() = runBlocking {
        val database = db(); val repo = RoomTimeRepository(database.timeDao())
        try {
            val draft = ClassPeriodClock.toWeeklyTimeBlockDraft(course())!!
            try { repo.importWeeklyBlocks(listOf(draft, draft.copy(title = " "))); fail("invalid batch must reject") }
            catch (_: IllegalArgumentException) { }
            assertTrue(repo.observeWeeklyBlocks().first().isEmpty())
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_import_revision BEFORE INSERT ON semester_settings BEGIN SELECT RAISE(ABORT, 'test revision failure'); END")
            try { repo.importWeeklyBlocks(listOf(draft, draft.copy(title = "另一课程"))); fail("revision failure must reject") }
            catch (_: android.database.sqlite.SQLiteException) { }
            assertTrue(repo.observeWeeklyBlocks().first().isEmpty())
            assertEquals(0L, repo.observeTimeConstraintSettings().first().updatedAtEpochMillis)
        } finally { database.close() }
    }
}
