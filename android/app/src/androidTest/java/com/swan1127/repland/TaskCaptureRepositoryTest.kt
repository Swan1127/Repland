package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TaskCaptureRepositoryTest {
    @Test fun invalid_raw_duration_cannot_be_committed_as_unknown_or_another_value() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomTaskCaptureRepository(db)
            listOf("-30", "+30", "3.5", "999999999999", "0", "1441").forEach { raw ->
                val capture = TaskCaptureDraft(text = "非法时长", isCustomDuration = true, customDurationText = raw)
                repo.saveDraft(capture)
                listOf(null, 30).forEach { substituted ->
                    assertTrue(runCatching { repo.commit(capture, capture.toTaskDraft().copy(totalDurationMinutes = substituted)) }.isFailure)
                    assertEquals(capture, repo.observeDraft().first())
                    assertTrue(db.taskDao().getAll().isEmpty())
                }
            }
            val capture = TaskCaptureDraft(text = "合法原值", isCustomDuration = true, customDurationText = "45")
            repo.saveDraft(capture)
            assertTrue(runCatching { repo.commit(capture, capture.toTaskDraft().copy(totalDurationMinutes = 30)) }.isFailure)
            assertEquals(capture, repo.observeDraft().first())
            assertTrue(db.taskDao().getAll().isEmpty())
            repo.commit(capture, capture.toTaskDraft())
            assertEquals(45, db.taskDao().getAll().single().totalDurationMinutes)
        } finally { db.close() }
    }
    @Test fun draft_fields_survive_database_close_and_reopen_without_creating_task() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "capture-${java.util.UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, ReplandDatabase::class.java, name).build()
        val draft = TaskCaptureDraft(text = "复习\n第二章", stage = TaskCaptureStage.DURATION,
            dueDate = LocalDate.now().plusDays(3), duration = 30, category = TaskCategory.OFFICE,
            priority = TaskPriority.HIGH, isCustomDuration = true, customDurationText = "45")
        try {
            RoomTaskCaptureRepository(db).saveDraft(draft)
            db.close()
            db = Room.databaseBuilder(context, ReplandDatabase::class.java, name).build()
            assertEquals(draft, RoomTaskCaptureRepository(db).observeDraft().first())
            val snapshot = RoomDataManagementRepository(db).snapshot()
            assertEquals(draft, snapshot.taskCaptureDraft)
            val exported = org.json.JSONObject(com.swan1127.repland.data.export.LocalDataJsonExporter.export(snapshot)).getJSONObject("taskCaptureDraft")
            assertEquals(draft.text, exported.getString("text"))
            assertEquals("45", exported.getString("customDurationText"))
            assertTrue(db.taskDao().getAll().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun commit_consumes_draft_and_retries_do_not_duplicate_or_resurrect_it() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomTaskCaptureRepository(db)
            val capture = TaskCaptureDraft(text = "复习", stage = TaskCaptureStage.DURATION, isCustomDuration = true, customDurationText = "45")
            val task = capture.toTaskDraft().copy(scheduledForDate = LocalDate.now())
            repo.saveDraft(capture); repo.commit(capture, task)
            repo.commit(capture, task); repo.saveDraft(capture)
            assertNull(repo.observeDraft().first())
            assertEquals(1, db.taskDao().getAll().size)
            assertEquals(45, db.taskDao().getAll().single().totalDurationMinutes)
            assertNull(RoomPlanRepository(db).observeCurrentPlan().first())
        } finally { db.close() }
    }

    @Test fun failed_draft_consumption_rolls_back_task_and_keeps_full_draft() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomTaskCaptureRepository(db)
            val capture = TaskCaptureDraft(text = "事务失败", priority = TaskPriority.HIGH)
            repo.saveDraft(capture)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_capture_delete BEFORE DELETE ON planning_workspace WHEN OLD.key = 'task-capture' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            assertTrue(runCatching { repo.commit(capture, capture.toTaskDraft()) }.isFailure)
            assertTrue(db.taskDao().getAll().isEmpty())
            assertEquals(capture, repo.observeDraft().first())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_capture_delete")
            repo.commit(capture, capture.toTaskDraft())
            assertEquals(1, db.taskDao().getAll().size)
        } finally { db.close() }
    }

    @Test fun stale_capture_is_rejected_and_discard_does_not_delete_tasks() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomTaskCaptureRepository(db)
            val old = TaskCaptureDraft(text = "原内容")
            val changed = old.copy(text = "修改后")
            repo.saveDraft(changed)
            assertTrue(runCatching { repo.commit(old, old.toTaskDraft()) }.isFailure)
            assertEquals(changed, repo.observeDraft().first())
            repo.discard("different-id")
            assertEquals(changed, repo.observeDraft().first())
            repo.discard(changed.id)
            assertNull(repo.observeDraft().first())
            assertTrue(db.taskDao().getAll().isEmpty())
        } finally { db.close() }
    }
}
