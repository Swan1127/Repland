package com.swan1127.repland

import androidx.room.Room
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.swan1127.repland.data.importer.PdfTimetableImporter
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.TimeRepository
import com.swan1127.repland.ui.time.*
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class TimeSettingsIntegrityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun db() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
    private fun weekly() = WeeklyTimeBlockDraft(title = "深夜课程", kind = TimeBlockKind.COURSE,
        dayOfWeek = DayOfWeek.MONDAY, startMinute = 1380, endMinute = 1440, weekPattern = "1-16单周", trackId = "lab", note = "准备资料")
    private fun override() = DateOverrideDraft(title = "深夜例外", type = DateOverrideType.BLOCKED,
        date = LocalDate.of(2026, 10, 5), startMinute = 1380, endMinute = 1440, note = "保留备注")
    private suspend fun fails(action: suspend () -> Unit) { assertTrue(runCatching { action() }.isFailure) }

    @Test fun existing_identity_and_metadata_survive_and_deleted_targets_cannot_resurrect() = runBlocking {
        val database = db(); val repo = RoomTimeRepository(database.timeDao())
        try {
            repo.saveWeeklyBlock(weekly()); repo.saveDateOverride(override())
            val first = repo.observeWeeklyBlocks().first().single(); val date = repo.observeDateOverrides().first().single()
            repo.saveWeeklyBlock(weekly().copy(id = first.id, title = "修改课程"))
            repo.saveDateOverride(override().copy(id = date.id, title = "修改例外"))
            val edited = repo.observeWeeklyBlocks().first().single(); val editedDate = repo.observeDateOverrides().first().single()
            assertEquals(first.id, edited.id); assertEquals(first.createdAtEpochMillis, edited.createdAtEpochMillis)
            assertEquals(first.weekPattern, edited.weekPattern); assertEquals(first.trackId, edited.trackId); assertEquals(1440, edited.endMinute)
            assertEquals(date.id, editedDate.id); assertEquals(date.createdAtEpochMillis, editedDate.createdAtEpochMillis)
            repo.deleteWeeklyBlock(first.id); repo.deleteDateOverride(date.id)
            val revision = repo.observeTimeConstraintSettings().first()
            fails { repo.saveWeeklyBlock(weekly().copy(id = first.id)) }
            fails { repo.saveDateOverride(override().copy(id = date.id)) }
            assertTrue(repo.observeWeeklyBlocks().first().isEmpty()); assertTrue(repo.observeDateOverrides().first().isEmpty())
            assertEquals(revision, repo.observeTimeConstraintSettings().first())
        } finally { database.close() }
    }

    @Test fun revision_failure_rolls_back_every_time_mutation() = runBlocking {
        val database = db(); val repo = RoomTimeRepository(database.timeDao())
        try {
            repo.saveWeeklyBlock(weekly()); repo.saveDateOverride(override()); repo.saveSemesterFirstWeekMonday(LocalDate.of(2026, 10, 5))
            val blocks = repo.observeWeeklyBlocks().first(); val dates = repo.observeDateOverrides().first()
            val revision = repo.observeTimeConstraintSettings().first()
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER qa_time_revision_failure BEFORE INSERT ON semester_settings BEGIN SELECT RAISE(ABORT, 'qa revision failure'); END")
            val actions: List<suspend () -> Unit> = listOf(
                { repo.saveWeeklyBlock(weekly().copy(title = "新课程")) },
                { repo.saveWeeklyBlock(weekly().copy(id = blocks.single().id, title = "改课程")) },
                { repo.deleteWeeklyBlock(blocks.single().id) },
                { repo.saveWeeklyBlocks(listOf(weekly().copy(title = "批量"))) },
                { repo.saveDateOverride(override().copy(title = "新例外")) },
                { repo.saveDateOverride(override().copy(id = dates.single().id, title = "改例外")) },
                { repo.deleteDateOverride(dates.single().id) },
                { repo.saveSemesterFirstWeekMonday(LocalDate.of(2026, 10, 12)) },
            )
            actions.forEach { action ->
                fails(action)
                assertEquals(blocks, repo.observeWeeklyBlocks().first()); assertEquals(dates, repo.observeDateOverrides().first())
                assertEquals(revision, repo.observeTimeConstraintSettings().first())
            }
        } finally { database.close() }
    }

    @Test fun invalid_input_is_not_partially_saved_and_concurrent_writes_advance_revision() = runBlocking {
        val database = db(); val repo = RoomTimeRepository(database.timeDao())
        try {
            fails { repo.saveWeeklyBlocks(listOf(weekly(), weekly().copy(title = ""))) }
            fails { repo.saveWeeklyBlock(weekly().copy(startMinute = 1440)) }
            fails { repo.saveDateOverride(override().copy(endMinute = 1441)) }
            fails { repo.saveSemesterFirstWeekMonday(LocalDate.of(2026, 10, 6)) }
            assertTrue(repo.observeWeeklyBlocks().first().isEmpty()); assertEquals(0L, repo.observeTimeConstraintSettings().first().updatedAtEpochMillis)
            repo.saveSemesterFirstWeekMonday(LocalDate.of(2026, 10, 5))
            val previous = repo.observeTimeConstraintSettings().first().updatedAtEpochMillis
            coroutineScope { listOf(async(Dispatchers.IO) { repo.saveWeeklyBlock(weekly()) },
                async(Dispatchers.IO) { repo.saveDateOverride(override()) }).awaitAll() }
            val settings = repo.observeTimeConstraintSettings().first()
            assertTrue(settings.updatedAtEpochMillis >= previous + 2); assertEquals(LocalDate.of(2026, 10, 5), settings.semesterFirstWeekMonday)
        } finally { database.close() }
    }

    private fun vm(repo: TimeRepository, store: ViewModelStore): TimeViewModel {
        lateinit var result: TimeViewModel
        instrumentation.runOnMainSync { result = TimeViewModel(repo, PdfTimetableImporter(ApplicationProvider.getApplicationContext())); store.put("time", result) }
        return result
    }
    @Test fun failed_save_keeps_target_retry_is_guarded_and_receipt_means_one_commit() = runBlocking {
        val database = db(); val base = RoomTimeRepository(database.timeDao()); val store = ViewModelStore()
        var fail = true; var writes = 0; var gate: CompletableDeferred<Unit>? = null
        val repo = object : TimeRepository by base {
            override suspend fun saveWeeklyBlock(draft: WeeklyTimeBlockDraft) {
                gate?.await(); if (fail) error("injected")
                writes++; base.saveWeeklyBlock(draft)
            }
        }
        try {
            val model = vm(repo, store)
            instrumentation.runOnMainSync { model.saveWeeklyBlock(weekly()) }
            withTimeout(10_000) { model.mutationState.first { it.error != null } }
            assertFalse(model.mutationState.value.busy); assertNull(model.mutationState.value.receipt)
            assertTrue(base.observeWeeklyBlocks().first().isEmpty())
            fail = false; gate = CompletableDeferred()
            instrumentation.runOnMainSync { model.retryMutation(); model.retryMutation(); model.saveDateOverride(override()); model.resetMutation() }
            assertTrue(model.mutationState.value.busy)
            gate!!.complete(Unit)
            withTimeout(10_000) { model.mutationState.first { it.receipt != null } }
            assertEquals(1, writes); assertEquals(1, base.observeWeeklyBlocks().first().size); assertTrue(base.observeDateOverrides().first().isEmpty())
            instrumentation.runOnMainSync { model.resetMutation() }; assertEquals(TimeMutationState(), model.mutationState.value)
        } finally { gate?.complete(Unit); instrumentation.runOnMainSync { store.clear() }; database.close() }
    }

    @Test fun delete_failure_has_stable_identity_and_cancel_is_not_a_business_error() = runBlocking {
        val database = db(); val base = RoomTimeRepository(database.timeDao()); val store = ViewModelStore()
        val repo = object : TimeRepository by base {
            override suspend fun deleteWeeklyBlock(id: String) { error("injected") }
            override suspend fun saveDateOverride(draft: DateOverrideDraft) { throw CancellationException("cancelled") }
        }
        try {
            base.saveWeeklyBlock(weekly()); val original = base.observeWeeklyBlocks().first().single()
            val model = vm(repo, store)
            instrumentation.runOnMainSync { model.deleteWeeklyBlock(original.id) }
            withTimeout(10_000) { model.mutationState.first { it.error != null } }
            assertEquals(original.id, model.mutationState.value.targetId); assertEquals(TimeMutationKind.WEEKLY_DELETE, model.mutationState.value.kind)
            assertEquals(original, base.observeWeeklyBlocks().first().single())
            instrumentation.runOnMainSync { model.resetMutation(); model.saveDateOverride(override()) }
            instrumentation.waitForIdleSync()
            assertEquals(TimeMutationState(), model.mutationState.value); assertTrue(base.observeDateOverrides().first().isEmpty())
        } finally { instrumentation.runOnMainSync { store.clear() }; database.close() }
    }
}
