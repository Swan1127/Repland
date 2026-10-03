package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanningWorkspaceRepositoryTest {
    private fun task(id: String) = TaskDraft(id, id, "", TaskCategory.COURSE, TaskPriority.HIGH, 1, 30, null)

    @Test fun draft_and_order_survive_database_close_and_reopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "planning-workspace-test-${java.util.UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, ReplandDatabase::class.java, name).build()
        try {
            val repo = RoomPlanRepository(db)
            val draft = PlanDraft(LocalDateTime.now(), emptyList(), listOf("a"), emptyList(), orderedTaskIds = listOf("a"), orderOnly = true)
            repo.saveDraft(draft)
            repo.saveTaskOrder(listOf("a"))
            db.close()
            db = Room.databaseBuilder(context, ReplandDatabase::class.java, name).build()
            assertEquals(draft, RoomPlanRepository(db).observeDraft().first())
            assertEquals(listOf("a"), RoomPlanRepository(db).observeTaskOrder().first())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun draft_cannot_remove_a_protected_future_placement() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomPlanRepository(db)
            RoomTaskRepository(db).save(task("a"))
            repo.accept(PlanDraft(LocalDateTime.now(), listOf(PlannedSegment("locked", "a", LocalDate.now().plusDays(1), 600, 630, true)), emptyList(), emptyList()))
            val current = repo.observeCurrentPlan().first()
            assertTrue(runCatching { repo.accept(PlanDraft(LocalDateTime.now(), emptyList(), emptyList(), emptyList())) }.isFailure)
            assertEquals(current, repo.observeCurrentPlan().first())
        } finally { db.close() }
    }

    @Test fun stored_order_does_not_create_or_move_a_plan_and_draft_survives_repository_recreation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomPlanRepository(db)
            val draft = PlanDraft(LocalDateTime.of(2026, 10, 3, 12, 0), emptyList(), listOf("a", "b"), emptyList(),
                orderedTaskIds = listOf("b", "a"), orderOnly = true)
            repo.saveDraft(draft)
            val recreated = RoomPlanRepository(db)
            assertEquals(draft, recreated.observeDraft().first())
            recreated.accept(draft)
            assertEquals(listOf("b", "a"), repo.observeTaskOrder().first())
            assertNull(repo.observeCurrentPlan().first())
            assertNull(repo.observeDraft().first())
        } finally { db.close() }
    }

    @Test fun batch_confirmation_keeps_task_ids_and_rejects_repeat_without_duplicates() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomPlanRepository(db)
            val segment = PlannedSegment("proposal-a", "a", LocalDate.now(), 600, 630)
            repo.saveTasksAndPlace(listOf(task("a")), listOf(segment))
            assertEquals("a", db.taskDao().getAll().single().id)
            assertEquals("a", repo.observeCurrentPlan().first()!!.segments.single().taskId)
            assertTrue(runCatching { repo.saveTasksAndPlace(listOf(task("a")), listOf(segment)) }.isFailure)
            assertEquals(1, db.taskDao().getAll().size)
        } finally { db.close() }
    }

    @Test fun conflicting_batch_is_atomic_and_writes_no_tasks() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomPlanRepository(db)
            val segments = listOf("a", "b").map { PlannedSegment("proposal-$it", it, LocalDate.now(), 600, 630) }
            assertTrue(runCatching { repo.saveTasksAndPlace(listOf(task("a"), task("b")), segments) }.isFailure)
            assertTrue(db.taskDao().getAll().isEmpty())
            assertNull(repo.observeCurrentPlan().first())
        } finally { db.close() }
    }

    @Test fun a_task_change_invalidates_a_stored_preview() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val tasks = RoomTaskRepository(db)
            tasks.save(task("a"))
            val input = PlanGenerationInput(tasks.observeTasks().first(), emptyList(), emptyList(), null)
            val draft = PlanGenerator.generate(input).copy(sourceRevision = PlanningRevision.of(input, null))
            val repo = RoomPlanRepository(db)
            repo.saveDraft(draft)
            tasks.save(task("b"))
            assertTrue(runCatching { repo.accept(draft) }.isFailure)
            assertNull(repo.observeCurrentPlan().first())
            assertEquals(draft, repo.observeDraft().first())
        } finally { db.close() }
    }
}
