package com.swan1127.repland

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.room.*
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantChangesRepositoryTest {
    private fun task(id: String) = TaskDraft(id, "英语$id", "私有备注", TaskCategory.COURSE, TaskPriority.HIGH, 1, 90, null)
    private fun segment(id: String, day: LocalDate, start: Int, locked: Boolean = false) = PlannedSegment("$id-$day-$start", id, day, start, start + 30, isLocked = locked)
    private suspend fun bind(db: ReplandDatabase, repo: RoomPlanRepository, day: LocalDate) {
        val revision = PlanningRevision.of(PlanGenerationInput(db.taskDao().getAll().map { it.toDomain() }, emptyList(), emptyList(), null), repo.observeCurrentPlan().first(), repo.observeTaskOrder().first())
        repo.saveAssistantWorkspace(AssistantWorkspace(day, "调整英语", listOf(AssistantTaskProposal("p", "英语a", TaskCategory.COURSE, 30, ArrangementTimeHint(720), emptySet(), ArrangementPlacementSource.AI_SUGGESTED, existingTaskId = "a")), sourceRevision = revision))
        assertEquals("a", repo.observeAssistantWorkspace().first()!!.proposals.single().existingTaskId)
    }
    @Test fun adjustment_preserves_task_facts_other_days_and_rejects_duplicate_confirmation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomPlanRepository(db); val day = LocalDate.now().plusDays(1)
            RoomTaskRepository(db).save(task("a"))
            repo.accept(PlanDraft(LocalDateTime.now(), listOf(segment("a", day, 600), segment("a", day.plusDays(1), 600)), emptyList(), emptyList(), listOf("a")))
            val before = db.taskDao().getAll()
            bind(db, repo, day)
            val changes = listOf(segment("a", day, 720))
            val result = repo.saveAssistantChanges(emptyList(), changes, setOf("a"), day)
            assertEquals(AssistantSaveResult(0, 1, 1, day), result)
            assertEquals(before, db.taskDao().getAll())
            assertEquals(setOf(day to 720, day.plusDays(1) to 600), repo.observeCurrentPlan().first()!!.segments.map { it.date to it.startMinute }.toSet())
            assertNull(repo.observeAssistantWorkspace().first())
            val plan = repo.observeCurrentPlan().first()
            assertTrue(runCatching { repo.saveAssistantChanges(emptyList(), changes, setOf("a"), day) }.isFailure)
            assertEquals(plan, repo.observeCurrentPlan().first())
        } finally { db.close() }
    }
    @Test fun mixed_collision_rolls_back_new_task_and_keeps_existing_plan_and_draft() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomPlanRepository(db); val day = LocalDate.now().plusDays(1)
            RoomTaskRepository(db).save(task("a")); RoomTaskRepository(db).save(task("b"))
            repo.accept(PlanDraft(LocalDateTime.now(), listOf(segment("a", day, 600), segment("b", day, 720)), emptyList(), emptyList(), listOf("a", "b")))
            bind(db, repo, day)
            val before = repo.observeCurrentPlan().first(); val draft = repo.observeAssistantWorkspace().first()
            assertTrue(runCatching { repo.saveAssistantChanges(listOf(task("new")), listOf(segment("a", day, 720), segment("new", day, 800)), setOf("a"), day) }.isFailure)
            assertNull(db.taskDao().getById("new")); assertEquals(before, repo.observeCurrentPlan().first()); assertEquals(draft, repo.observeAssistantWorkspace().first())
        } finally { db.close() }
    }
    @Test fun locked_placement_cannot_be_replaced() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomPlanRepository(db); val day = LocalDate.now().plusDays(1)
            RoomTaskRepository(db).save(task("a"))
            repo.accept(PlanDraft(LocalDateTime.now(), listOf(segment("a", day, 600, true)), emptyList(), emptyList(), listOf("a")))
            bind(db, repo, day); val before = repo.observeCurrentPlan().first()
            assertTrue(runCatching { repo.saveAssistantChanges(emptyList(), listOf(segment("a", day, 720)), setOf("a"), day) }.isFailure)
            assertEquals(before, repo.observeCurrentPlan().first()); assertNotNull(repo.observeAssistantWorkspace().first())
        } finally { db.close() }
    }
    @Test fun started_placement_cannot_be_replaced() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomPlanRepository(db); val day = LocalDate.now()
            RoomTaskRepository(db).save(task("a"))
            repo.accept(PlanDraft(LocalDateTime.now(), listOf(segment("a", day, 0)), emptyList(), emptyList(), listOf("a")))
            bind(db, repo, day); val before = repo.observeCurrentPlan().first()
            assertTrue(runCatching { repo.saveAssistantChanges(emptyList(), listOf(segment("a", day, 1400)), setOf("a"), day) }.isFailure)
            assertEquals(before, repo.observeCurrentPlan().first()); assertNotNull(repo.observeAssistantWorkspace().first())
        } finally { db.close() }
    }
    @Test fun changed_task_facts_make_an_existing_task_draft_stale() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomPlanRepository(db); val day = LocalDate.now().plusDays(1)
            RoomTaskRepository(db).save(task("a")); bind(db, repo, day)
            RoomTaskRepository(db).save(task("a").copy(totalDurationMinutes = 120))
            assertTrue(runCatching { repo.saveAssistantChanges(emptyList(), listOf(segment("a", day, 720)), setOf("a"), day) }.isFailure)
            assertNull(repo.observeCurrentPlan().first()); assertNotNull(repo.observeAssistantWorkspace().first())
        } finally { db.close() }
    }
    @Test fun unchanged_placement_is_not_reported_as_an_adjustment() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomPlanRepository(db); val day = LocalDate.now().plusDays(1)
            RoomTaskRepository(db).save(task("a"))
            repo.accept(PlanDraft(LocalDateTime.now(), listOf(segment("a", day, 720)), emptyList(), emptyList(), listOf("a")))
            bind(db, repo, day)
            assertEquals(AssistantSaveResult(0, 0, 1, day), repo.saveAssistantChanges(emptyList(), listOf(segment("a", day, 720)), setOf("a"), day))
        } finally { db.close() }
    }
    @Test fun unapplied_follow_up_survives_round_trip_and_blocks_confirmation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReplandDatabase::class.java).build()
        try {
            val repo = RoomPlanRepository(db); val day = LocalDate.now().plusDays(1)
            RoomTaskRepository(db).save(task("a")); bind(db, repo, day)
            val draft = repo.observeAssistantWorkspace().first()!!.copy(followUpInstruction = "挪到16点")
            repo.saveAssistantWorkspace(draft)
            assertEquals(draft, repo.observeAssistantWorkspace().first())
            assertTrue(runCatching { repo.saveAssistantChanges(emptyList(), listOf(segment("a", day, 720)), setOf("a"), day) }.isFailure)
            assertNull(repo.observeCurrentPlan().first()); assertEquals(draft, repo.observeAssistantWorkspace().first())
        } finally { db.close() }
    }
}
