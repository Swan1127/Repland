package com.swan1127.repland

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.domain.ports.TaskRepository
import com.swan1127.repland.ui.tasks.TaskViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class TaskEditorIntegrityTest {
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
    private fun draft(id: String = "editor-test") = TaskDraft(id = id, displayName = "原任务", description = "原备注",
        category = TaskCategory.COURSE, userPriority = TaskPriority.HIGH, estimatedDays = 1, totalDurationMinutes = 30, dueDate = null)

    @Test fun edit_requires_existing_identity_and_never_recreates_removed_task() = runBlocking {
        val db = database()
        try {
            val repo = RoomTaskRepository(db)
            repo.save(draft())
            db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id = 'editor-test'")
            try { repo.updateExisting(draft().copy(displayName = "不应重建")); fail("must reject missing identity") }
            catch (_: IllegalArgumentException) { }
            assertTrue(db.taskDao().getAll().isEmpty())
        } finally { db.close() }
    }

    @Test fun edit_preserves_status_initial_priority_history_and_identity() = runBlocking {
        val db = database()
        try {
            val repo = RoomTaskRepository(db)
            repo.save(draft()); repo.confirmStatus("editor-test", TaskStatus.IN_PROGRESS)
            val original = repo.observeTasks().first().single()
            repo.updateExisting(draft().copy(displayName = "修改后", description = "新备注", userPriority = TaskPriority.LOW, totalDurationMinutes = 45))
            val edited = repo.observeTasks().first().single()
            assertEquals(original.id, edited.id); assertEquals(original.createdAtEpochMillis, edited.createdAtEpochMillis)
            assertEquals(TaskStatus.IN_PROGRESS, edited.status); assertEquals(TaskPriority.HIGH, edited.userPriority)
            assertEquals(45, edited.totalDurationMinutes); assertEquals("新备注", edited.description)
            assertEquals(1, repo.observeExecutionLogs(original.id).first().size)
        } finally { db.close() }
    }

    @Test fun save_failure_has_no_receipt_retry_and_pending_duplicate_follow_real_commit() = runBlocking {
        val db = database()
        val store = ViewModelStore()
        val base = RoomTaskRepository(db)
        var failWrite = true
        var writes = 0
        var gate: CompletableDeferred<Unit>? = null
        val repo = object : TaskRepository by base {
            override suspend fun updateExisting(draft: TaskDraft) {
                gate?.await()
                if (failWrite) error("injected failure")
                base.updateExisting(draft); writes++
            }
        }
        lateinit var vm: TaskViewModel
        try {
            base.save(draft())
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm = TaskViewModel(repo); store.put("editor", vm) }
            val edit = draft().copy(description = "保留输入", totalDurationMinutes = 45)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.saveTask(edit) }
            withTimeout(10_000) { vm.editorSaveState.first { it.error != null } }
            assertNull(vm.editorSaveState.value.receipt)
            assertEquals("原备注", base.observeTasks().first().single().description)
            failWrite = false; gate = CompletableDeferred()
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.saveTask(edit); vm.saveTask(edit) }
            assertTrue(vm.editorSaveState.value.saving); assertNull(vm.editorSaveState.value.receipt)
            gate!!.complete(Unit)
            withTimeout(10_000) { vm.editorSaveState.first { it.receipt != null } }
            assertEquals(1, writes); assertEquals("保留输入", base.observeTasks().first().single().description)
            assertEquals(1, db.taskDao().getAll().size)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.saveTask(edit) }
            assertEquals(1, writes)
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }; db.close() }
    }
}
